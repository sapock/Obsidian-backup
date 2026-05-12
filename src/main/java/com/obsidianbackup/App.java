package com.obsidianbackup;

import javafx.application.Application;
import javafx.stage.Stage;

/**
 * 애플리케이션 진입점.
 * 3단계(TrayManager, SettingsWindow) 구현 전 최소 스텁.
 */
public class App extends Application {

    @Override
    public void start(Stage primaryStage) {
        // TODO: 3단계에서 TrayManager 초기화 및 설정창 표시 구현
        primaryStage.setTitle("ObsidianBackup");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
