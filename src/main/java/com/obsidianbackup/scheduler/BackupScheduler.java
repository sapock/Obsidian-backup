package com.obsidianbackup.scheduler;

import com.obsidianbackup.backup.BackupResult;
import com.obsidianbackup.backup.BackupService;
import com.obsidianbackup.config.AppConfig;
import com.obsidianbackup.config.ConfigManager;
import com.obsidianbackup.ui.TrayManager;

import java.io.File;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 매일 지정 시각에 백업을 실행하는 스케줄러.
 *
 * 동작 방식:
 *   - 단일 스레드 ScheduledExecutorService에서 one-shot 방식으로 예약한다.
 *   - 백업 완료 후 다음 날 같은 시각으로 자동 재예약하므로,
 *     장시간 백업이 겹쳐 실행되지 않는다.
 *   - 설정 변경 시 reschedule()을 호출하면 즉시 새 시각으로 재예약된다.
 *
 * Windows 자동 시작:
 *   - setAutoStart(enable, appPath)로 레지스트리 Run 키를 등록/해제한다.
 *   - Windows 외 환경에서는 경고 로그만 남기고 무시한다.
 */
public class BackupScheduler {

    private static final Logger LOGGER = Logger.getLogger(BackupScheduler.class.getName());
    private static final DateTimeFormatter DT_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String REG_RUN_KEY =
        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String REG_VALUE_NAME = "ObsidianBackup";

    // ───────────────────────────────────────────
    // 필드
    // ───────────────────────────────────────────

    private final ScheduledExecutorService executor =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "backup-scheduler");
            t.setDaemon(true);
            return t;
        });

    private volatile ScheduledFuture<?> pendingTask;

    private final BackupService   backupService;
    private final ConfigManager   configManager;
    private final TrayManager     trayManager;   // null 허용 (테스트용)

    // ───────────────────────────────────────────
    // 생성자
    // ───────────────────────────────────────────

    /** 운영 환경용 생성자. */
    public BackupScheduler(TrayManager trayManager) {
        this.trayManager   = trayManager;
        this.backupService = new BackupService();
        this.configManager = ConfigManager.getInstance();
    }

    /** 테스트용 의존성 주입 생성자. */
    BackupScheduler(BackupService backupService, ConfigManager configManager,
                    TrayManager trayManager) {
        this.backupService = backupService;
        this.configManager = configManager;
        this.trayManager   = trayManager;
    }

    // ───────────────────────────────────────────
    // 공개 API
    // ───────────────────────────────────────────

    /** 스케줄러를 시작한다. App.start()에서 한 번 호출. */
    public void start() {
        reschedule();
    }

    /**
     * 현재 예약을 취소하고 설정에서 다시 읽어 재예약한다.
     * SettingsWindow에서 설정 저장 후 호출된다.
     */
    public synchronized void reschedule() {
        if (pendingTask != null && !pendingTask.isDone()) {
            pendingTask.cancel(false); // 실행 중인 백업은 중단하지 않음
        }
        scheduleNext(LocalDateTime.now());
    }

    /** 스케줄러를 종료한다. App.stop()에서 호출. */
    public void shutdown() {
        executor.shutdownNow();
        LOGGER.info("BackupScheduler shut down.");
    }

    // ───────────────────────────────────────────
    // 내부 스케줄 로직
    // ───────────────────────────────────────────

    private synchronized void scheduleNext(LocalDateTime now) {
        if (executor.isShutdown()) return;

        AppConfig config = configManager.load();
        long delayMs = calcDelayMs(config.getBackupHour(), config.getBackupMinute(), now);

        LocalDateTime nextRun = now.plusNanos(delayMs * 1_000_000L);
        LOGGER.info("Next backup scheduled at: " + nextRun.format(DT_FMT)
            + " (in " + delayMs / 60_000 + " min)");

        pendingTask = executor.schedule(this::runAndReschedule, delayMs, TimeUnit.MILLISECONDS);
    }

    private void runAndReschedule() {
        LOGGER.info("Scheduled backup triggered.");
        BackupResult result = backupService.run();

        if (trayManager != null) {
            trayManager.onBackupFinished(result);
        }

        // 다음 날 같은 시각으로 재예약
        scheduleNext(LocalDateTime.now());
    }

    // ───────────────────────────────────────────
    // 지연 계산 (패키지 접근 — 테스트용)
    // ───────────────────────────────────────────

    /**
     * 지금부터 다음 예약 시각까지의 밀리초를 반환한다.
     * 오늘 해당 시각이 이미 지났으면 내일로 넘긴다.
     */
    long calcDelayMs(int hour, int minute) {
        return calcDelayMs(hour, minute, LocalDateTime.now());
    }

    /** 테스트에서 고정 기준 시각을 주입할 수 있는 오버로드. */
    long calcDelayMs(int hour, int minute, LocalDateTime now) {
        LocalDateTime next = now.toLocalDate().atTime(hour, minute, 0);
        if (!next.isAfter(now)) {
            next = next.plusDays(1);
        }
        return Duration.between(now, next).toMillis();
    }

    // ───────────────────────────────────────────
    // Windows 자동 시작 (레지스트리)
    // ───────────────────────────────────────────

    /**
     * Windows 시작 시 자동 실행 등록/해제.
     *
     * @param enable  true=등록, false=해제
     * @param appPath 등록할 실행 명령. null이면 현재 JVM 경로를 추측한다.
     */
    public static void setAutoStart(boolean enable, String appPath) {
        if (!isWindows()) {
            LOGGER.info("AutoStart is only supported on Windows. Skipping.");
            return;
        }
        try {
            ProcessBuilder pb;
            if (enable) {
                String cmd = (appPath != null) ? appPath : resolveCurrentExePath();
                if (cmd == null) {
                    LOGGER.warning("Cannot determine app path for autostart. Skipping.");
                    return;
                }
                pb = new ProcessBuilder("reg", "add", REG_RUN_KEY,
                    "/v", REG_VALUE_NAME, "/t", "REG_SZ", "/d", cmd, "/f");
            } else {
                pb = new ProcessBuilder("reg", "delete", REG_RUN_KEY,
                    "/v", REG_VALUE_NAME, "/f");
            }
            pb.redirectErrorStream(true);
            int exit = pb.start().waitFor();
            LOGGER.info("AutoStart " + (enable ? "enabled" : "disabled")
                + " (reg exit=" + exit + ").");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING,
                "Failed to " + (enable ? "enable" : "disable") + " autostart.", e);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("windows");
    }

    /**
     * 현재 실행 환경에서 자동 시작 등록 명령을 추측한다.
     *
     * - jpackage .exe: 현재 프로세스 경로 그대로 사용
     * - JAR 실행: "javaw.exe" -jar "path/to/jar"
     */
    private static String resolveCurrentExePath() {
        try {
            String command = ProcessHandle.current().info().command().orElse(null);

            // jpackage가 만든 .exe인 경우
            if (command != null
                    && command.toLowerCase().endsWith(".exe")
                    && !command.toLowerCase().contains("java")) {
                return "\"" + command + "\"";
            }

            // JAR 실행 중인 경우
            File jar = new File(BackupScheduler.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
            if (jar.getName().endsWith(".jar")) {
                String javaExe = (command != null) ? command : "javaw.exe";
                return "\"" + javaExe + "\" -jar \"" + jar.getAbsolutePath() + "\"";
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Cannot resolve app path.", e);
        }
        return null;
    }
}
