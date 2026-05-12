package com.obsidianbackup;

import com.obsidianbackup.config.AppConfig;
import com.obsidianbackup.config.ConfigManager;
import com.obsidianbackup.scheduler.BackupScheduler;
import com.obsidianbackup.ui.SettingsWindow;
import com.obsidianbackup.ui.TrayManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

/**
 * 애플리케이션 진입점.
 *
 * 시작 흐름:
 *   1. config.json 로드
 *   2. TrayManager — 트레이 아이콘 등록
 *   3. BackupScheduler — 매일 자동 백업 예약 시작
 *   4. 설정이 미완성이면 설정창 자동 팝업
 *
 * 종료 흐름 (stop()):
 *   - BackupScheduler 종료
 *   - TrayManager 아이콘 제거
 */
public class App extends Application {

    private final TrayManager     trayManager = new TrayManager();
    private       BackupScheduler scheduler;

    @Override
    public void start(Stage primaryStage) {
        // 창을 모두 닫아도 트레이 + 스케줄러가 살아있도록 암묵적 종료 비활성화
        Platform.setImplicitExit(false);

        // primaryStage 사용 안 함 — SettingsWindow 가 자체 Stage 관리
        primaryStage.hide();

        // 트레이 아이콘 등록
        trayManager.install();

        // 스케줄러 초기화 및 시작
        scheduler = new BackupScheduler(trayManager);
        SettingsWindow.setOnConfigSaved(scheduler::reschedule);
        scheduler.start();

        // 설정이 미완성이면 설정창 팝업 (첫 실행 안내)
        AppConfig config = ConfigManager.getInstance().load();
        if (!config.isValid()) {
            SettingsWindow.show();
        }
    }

    @Override
    public void stop() {
        if (scheduler != null) scheduler.shutdown();
        trayManager.uninstall();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
