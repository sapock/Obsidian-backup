package com.obsidianbackup.ui;

import com.obsidianbackup.backup.BackupResult;
import com.obsidianbackup.backup.BackupService;
import com.obsidianbackup.config.AppConfig;
import com.obsidianbackup.config.ConfigManager;
import javafx.application.Platform;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * java.awt.SystemTray 기반 트레이 아이콘 관리자.
 *
 * 팝업 메뉴:
 *   - 마지막 백업 시각 (비활성 레이블)
 *   - 설정 열기
 *   - 지금 백업
 *   - 백업 이력
 *   - 종료
 *
 * 주의: AWT 이벤트와 JavaFX 이벤트를 Bridge하므로,
 * JavaFX UI를 건드리는 코드는 반드시 Platform.runLater()로 감싼다.
 */
public class TrayManager {

    private static final Logger LOGGER = Logger.getLogger(TrayManager.class.getName());

    private TrayIcon   trayIcon;
    private MenuItem   lastBackupItem;  // 마지막 백업 시각 표시용 (비활성)
    private MenuItem   backupNowItem;

    private volatile boolean backupRunning = false;

    private final ConfigManager configManager = ConfigManager.getInstance();
    private final BackupService backupService = new BackupService();

    // ───────────────────────────────────────────
    // 설치 / 제거
    // ───────────────────────────────────────────

    /**
     * 시스템 트레이에 아이콘을 등록한다.
     * 트레이를 지원하지 않는 환경에서는 경고만 남기고 무시한다.
     */
    public void install() {
        if (!SystemTray.isSupported()) {
            LOGGER.warning("SystemTray is not supported on this platform.");
            return;
        }

        Image icon    = loadIcon();
        PopupMenu menu = buildMenu();

        trayIcon = new TrayIcon(icon, buildTooltip(), menu);
        trayIcon.setImageAutoSize(true);

        // 더블 클릭 → 설정창 열기
        trayIcon.addActionListener(e -> Platform.runLater(SettingsWindow::show));

        try {
            SystemTray.getSystemTray().add(trayIcon);
            LOGGER.info("Tray icon installed.");
        } catch (AWTException e) {
            LOGGER.log(Level.SEVERE, "Failed to install tray icon.", e);
        }
    }

    /** 시스템 트레이에서 아이콘을 제거한다. */
    public void uninstall() {
        if (trayIcon != null && SystemTray.isSupported()) {
            SystemTray.getSystemTray().remove(trayIcon);
            trayIcon = null;
            LOGGER.info("Tray icon removed.");
        }
    }

    // ───────────────────────────────────────────
    // 외부에서 상태 업데이트 (BackupScheduler 완료 후)
    // ───────────────────────────────────────────

    /**
     * 백업 완료 후 툴팁·메뉴 레이블 갱신 및 OS 알림 표시.
     *
     * @param result 완료된 백업 결과
     */
    public void onBackupFinished(BackupResult result) {
        refreshTooltip();

        boolean failed = result.getStatus() == BackupResult.Status.FAILED;
        String title   = failed ? "백업 실패" : "백업 완료";
        String message = failed
            ? truncate(result.getErrorMessage(), 100)
            : result.getCopiedFiles() + "개 파일 복사 (" + formatBytes(result.getTotalSizeBytes()) + ")";
        TrayIcon.MessageType type = failed
            ? TrayIcon.MessageType.ERROR : TrayIcon.MessageType.INFO;

        showNotification(title, message, type);

        // 설정창이 열려 있으면 이력 탭 새로고침
        Platform.runLater(SettingsWindow::refreshIfVisible);
    }

    // ───────────────────────────────────────────
    // OS 알림
    // ───────────────────────────────────────────

    /**
     * 설정에서 showNotification이 true일 때만 OS 알림 버블을 표시한다.
     */
    public void showNotification(String title, String message, TrayIcon.MessageType type) {
        AppConfig cfg = configManager.getCached();
        if (cfg != null && !cfg.isShowNotification()) return;
        if (trayIcon == null) return;
        trayIcon.displayMessage(title, message, type);
    }

    // ───────────────────────────────────────────
    // 팝업 메뉴 구성
    // ───────────────────────────────────────────

    private PopupMenu buildMenu() {
        PopupMenu menu = new PopupMenu();

        // 마지막 백업 시각 (비활성 정보 레이블)
        lastBackupItem = new MenuItem(buildTooltip());
        lastBackupItem.setEnabled(false);
        menu.add(lastBackupItem);

        menu.addSeparator();

        // 설정 열기
        MenuItem openSettings = new MenuItem("설정 열기");
        openSettings.addActionListener(e -> Platform.runLater(SettingsWindow::show));
        menu.add(openSettings);

        // 지금 백업
        backupNowItem = new MenuItem("지금 백업");
        backupNowItem.addActionListener(e -> runBackupAsync());
        menu.add(backupNowItem);

        // 백업 이력
        MenuItem showHistory = new MenuItem("백업 이력");
        showHistory.addActionListener(e -> Platform.runLater(SettingsWindow::showHistoryTab));
        menu.add(showHistory);

        menu.addSeparator();

        // 종료
        MenuItem exit = new MenuItem("종료");
        exit.addActionListener(e -> {
            uninstall();
            Platform.exit();
            System.exit(0);
        });
        menu.add(exit);

        return menu;
    }

    // ───────────────────────────────────────────
    // 백업 실행 (AWT 이벤트 스레드에서 호출됨)
    // ───────────────────────────────────────────

    private void runBackupAsync() {
        if (backupRunning) return;
        backupRunning = true;

        EventQueue.invokeLater(() -> {
            backupNowItem.setEnabled(false);
            backupNowItem.setLabel("백업 중...");
        });

        Thread t = new Thread(() -> {
            BackupResult result = backupService.run();
            backupRunning = false;

            EventQueue.invokeLater(() -> {
                backupNowItem.setEnabled(true);
                backupNowItem.setLabel("지금 백업");
            });

            onBackupFinished(result);
        }, "tray-backup-thread");
        t.setDaemon(true);
        t.start();
    }

    // ───────────────────────────────────────────
    // 내부 유틸
    // ───────────────────────────────────────────

    private void refreshTooltip() {
        String tip = buildTooltip();
        EventQueue.invokeLater(() -> {
            if (trayIcon     != null) trayIcon.setToolTip(tip);
            if (lastBackupItem != null) lastBackupItem.setLabel(tip);
        });
    }

    private String buildTooltip() {
        AppConfig cfg = configManager.getCached();
        if (cfg == null) cfg = configManager.load();
        String last = cfg.getLastBackupTime();
        return last != null
            ? "ObsidianBackup — 마지막 백업: " + last
            : "ObsidianBackup — 백업 이력 없음";
    }

    private Image loadIcon() {
        URL url = getClass().getResource("/tray-icon.png");
        if (url != null) {
            try {
                return ImageIO.read(url);
            } catch (IOException e) {
                LOGGER.warning("tray-icon.png 로드 실패. 기본 아이콘 사용.");
            }
        }
        return generateFallbackIcon();
    }

    /** 리소스 파일이 없을 때 사용하는 16×16 프로그래밍 방식 아이콘 */
    private Image generateFallbackIcon() {
        final int SIZE = 16;
        BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 파란 원형 배경
        g.setColor(new Color(74, 144, 217));
        g.fillOval(0, 0, SIZE, SIZE);

        // 흰색 'O'
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 10));
        FontMetrics fm = g.getFontMetrics();
        String ch = "O";
        int tx = (SIZE - fm.stringWidth(ch)) / 2;
        int ty = (SIZE - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString(ch, tx, ty);
        g.dispose();

        return img;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1_048_576L)     return String.format("%.1f KB", bytes / 1_024.0);
        if (bytes < 1_073_741_824L) return String.format("%.1f MB", bytes / 1_048_576.0);
        return                             String.format("%.2f GB", bytes / 1_073_741_824.0);
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
