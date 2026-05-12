package com.obsidianbackup.scheduler;

import com.obsidianbackup.config.AppConfig;
import com.obsidianbackup.config.ConfigManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BackupScheduler 단위 테스트.
 *
 * 실제 스케줄링(24시간 대기)은 통합 테스트 영역이므로,
 * 여기서는 핵심 계산 로직과 안전 동작만 검증한다.
 */
class BackupSchedulerTest {

    @TempDir
    Path tempDir;

    private ConfigManager configManager;
    private BackupScheduler scheduler;

    @BeforeEach
    void setUp() throws Exception {
        Field instanceField = ConfigManager.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);

        configManager = ConfigManager.getInstance();
        Field pathField = ConfigManager.class.getDeclaredField("configPath");
        pathField.setAccessible(true);
        pathField.set(configManager, tempDir.resolve("config.json"));

        // null trayManager — 스케줄러 로직만 테스트
        scheduler = new BackupScheduler(null, configManager, null);
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdown();
    }

    // ───────────────────────────────────────────
    // calcDelayMs — 핵심 지연 계산 검증
    // ───────────────────────────────────────────

    @Test
    @DisplayName("오늘 미래 시각: 당일 남은 시간을 반환한다")
    void calcDelayMs_futureToday() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 12, 1, 0, 0); // 01:00
        long delay = scheduler.calcDelayMs(2, 0, now);               // → 02:00

        assertEquals(60 * 60 * 1_000L, delay, "1시간(3600s) 대기");
    }

    @Test
    @DisplayName("오늘 이미 지난 시각: 다음 날로 넘어간 시간을 반환한다")
    void calcDelayMs_pastToday() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 12, 3, 0, 0); // 03:00
        long delay = scheduler.calcDelayMs(2, 0, now);               // 02:00 지남

        assertEquals(23 * 60 * 60 * 1_000L, delay, "23시간 대기 (내일 02:00)");
    }

    @Test
    @DisplayName("정확히 현재 시각과 일치: 내일로 넘긴다 (24시간)")
    void calcDelayMs_exactNow() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 12, 2, 0, 0);
        long delay = scheduler.calcDelayMs(2, 0, now);

        assertEquals(24 * 60 * 60 * 1_000L, delay, "24시간 대기 (내일 02:00)");
    }

    @Test
    @DisplayName("자정 직전(23:59) 예약 00:00: 1분 대기")
    void calcDelayMs_nearMidnightToMidnight() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 12, 23, 59, 0);
        long delay = scheduler.calcDelayMs(0, 0, now);

        assertEquals(60 * 1_000L, delay, "1분(60s) 대기");
    }

    @Test
    @DisplayName("분 단위 차이도 정확히 계산한다")
    void calcDelayMs_minutePrecision() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 12, 1, 30, 0); // 01:30
        long delay = scheduler.calcDelayMs(2, 15, now);               // → 02:15

        assertEquals(45 * 60 * 1_000L, delay, "45분(2700s) 대기");
    }

    // ───────────────────────────────────────────
    // reschedule() — 예외 없이 동작 확인
    // ───────────────────────────────────────────

    @Test
    @DisplayName("start() + reschedule() 연속 호출은 예외를 던지지 않는다")
    void startAndReschedule_noException() {
        AppConfig config = configManager.load();
        config.setSourceFolder(tempDir.resolve("s").toString());
        config.setBackupFolder(tempDir.resolve("d").toString());
        configManager.save(config);

        assertDoesNotThrow(() -> {
            scheduler.start();
            scheduler.reschedule();
            scheduler.reschedule(); // 여러 번 호출 안전성 확인
        });
    }

    @Test
    @DisplayName("shutdown() 후 reschedule()을 호출해도 예외를 던지지 않는다")
    void reschedule_afterShutdown_noException() {
        scheduler.start();
        scheduler.shutdown();
        assertDoesNotThrow(() -> scheduler.reschedule());
    }

    // ───────────────────────────────────────────
    // setAutoStart — 비 Windows 환경 안전 동작
    // ───────────────────────────────────────────

    @Test
    @DisplayName("비 Windows 환경에서 setAutoStart(true)는 예외를 던지지 않는다")
    void setAutoStart_enable_nonWindows_noException() {
        assertDoesNotThrow(() ->
            BackupScheduler.setAutoStart(true, "/fake/path/ObsidianBackup.exe"));
    }

    @Test
    @DisplayName("비 Windows 환경에서 setAutoStart(false)는 예외를 던지지 않는다")
    void setAutoStart_disable_nonWindows_noException() {
        assertDoesNotThrow(() ->
            BackupScheduler.setAutoStart(false, null));
    }
}
