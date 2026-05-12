package com.obsidianbackup;

import com.obsidianbackup.config.AppConfig;
import com.obsidianbackup.config.ConfigManager;
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
 *   2. TrayManager 트레이 아이콘 등록
 *   3. 설정이 미완성이면 설정창 자동 팝업
 *   4. (5단계) BackupScheduler 시작
 */
public class App extends Application {

    private final TrayManager trayManager = new TrayManager();

    @Override
    public void start(Stage primaryStage) {
        // JavaFX 창을 모두 닫아도 트레이 + 스케줄러가 살아있도록 암묵적 종료 비활성화
        Platform.setImplicitExit(false);

        // primaryStage는 사용하지 않음 — SettingsWindow가 자체 Stage 관리
        primaryStage.hide();

        // 트레이 아이콘 설치
        trayManager.install();

        // 설정이 미완성이면(첫 실행) 설정창 팝업
        AppConfig config = ConfigManager.getInstance().load();
        if (!config.isValid()) {
            SettingsWindow.show();
        }
    }

    @Override
    public void stop() {
        trayManager.uninstall();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
