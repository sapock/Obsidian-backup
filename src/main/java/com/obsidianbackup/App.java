package com.obsidianbackup;

import com.obsidianbackup.config.AppConfig;
import com.obsidianbackup.config.ConfigManager;
import com.obsidianbackup.scheduler.BackupScheduler;
import com.obsidianbackup.ui.SettingsWindow;
import com.obsidianbackup.ui.TrayManager;
import com.obsidianbackup.util.LoggingSetup;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * 애플리케이션 진입점.
 *
 * 시작 흐름:
 *   1. 파일 로깅 설정 (app.log)
 *   2. config.json 로드
 *   3. TrayManager — 트레이 아이콘 등록
 *   4. BackupScheduler — 매일 자동 백업 예약
 *   5. 설정 미완성 시 설정창 팝업
 *
 * 종료 흐름 (stop()):
 *   - BackupScheduler 종료
 *   - TrayManager 아이콘 제거
 */
public class App extends Application {

    private static final Logger LOGGER = Logger.getLogger(App.class.getName());

    private final TrayManager     trayManager = new TrayManager();
    private       BackupScheduler scheduler;

    @Override
    public void start(Stage primaryStage) {
        // 1. 로깅 설정 (파일 핸들러 — app.log)
        ConfigManager configManager = ConfigManager.getInstance();
        Path appDataDir = configManager.getConfigPath().getParent();
        LoggingSetup.init(appDataDir);

        // 2. 설정 로드 및 로그 레벨 적용
        AppConfig config = configManager.load();
        LoggingSetup.applyLogLevel(config.getLogLevel());

        LOGGER.info("ObsidianBackup v1.0.0 starting.");

        // 3. 창을 모두 닫아도 트레이 + 스케줄러가 살아있도록 암묵적 종료 비활성화
        Platform.setImplicitExit(false);
        primaryStage.hide();

        // 4. 트레이 아이콘 등록
        trayManager.install();

        // 5. 스케줄러 초기화 및 시작
        scheduler = new BackupScheduler(trayManager);
        SettingsWindow.setOnConfigSaved(scheduler::reschedule);
        scheduler.start();

        // 6. 첫 실행 또는 설정 미완성 시 설정창 팝업
        if (!config.isValid()) {
            LOGGER.info("Config invalid — opening SettingsWindow for initial setup.");
            SettingsWindow.show();
        }
    }

    @Override
    public void stop() {
        LOGGER.info("ObsidianBackup stopping.");
        if (scheduler != null) scheduler.shutdown();
        trayManager.uninstall();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
