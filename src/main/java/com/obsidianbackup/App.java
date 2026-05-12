package com.obsidianbackup;

import com.obsidianbackup.config.AppConfig;
import com.obsidianbackup.config.ConfigManager;
import com.obsidianbackup.ui.SettingsWindow;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

/**
 * 애플리케이션 진입점.
 *
 * 시작 흐름:
 *   1. config.json 로드
 *   2. 설정창 표시 (최초 실행이거나 설정이 미완성인 경우 반드시 표시)
 *   3. (4단계) TrayManager 초기화
 */
public class App extends Application {

    @Override
    public void start(Stage primaryStage) {
        // JavaFX 가 닫혀도 데몬 스레드(백업, 스케줄러)가 실행 중일 수 있으므로
        // 창을 모두 닫아도 JVM이 종료되지 않게 설정한다.
        // (4단계 TrayManager 구현 후 System.exit() 호출로 종료)
        Platform.setImplicitExit(false);

        // primaryStage는 사용하지 않음 — SettingsWindow가 자체 Stage를 관리한다.
        primaryStage.hide();

        AppConfig config = ConfigManager.getInstance().load();

        // 설정이 완성되지 않은 경우(첫 실행)에는 무조건 설정창을 연다.
        if (!config.isValid()) {
            SettingsWindow.show();
        } else {
            SettingsWindow.show();
            // TODO(4단계): TrayManager.install() 호출로 트레이 아이콘 등록
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
