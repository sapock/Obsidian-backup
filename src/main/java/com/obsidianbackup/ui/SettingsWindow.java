package com.obsidianbackup.ui;

import com.obsidianbackup.backup.BackupHistory;
import com.obsidianbackup.backup.BackupResult;
import com.obsidianbackup.backup.BackupService;
import com.obsidianbackup.config.AppConfig;
import com.obsidianbackup.config.ConfigManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.logging.Logger;

/**
 * JavaFX 설정창 컨트롤러.
 *
 * 탭 구성:
 *   1. 설정 — 폴더/시각/보관기간/옵션 편집, 즉시 백업
 *   2. 백업 이력 — history.json 테이블, 실패 상세, 디스크 사용량
 *   3. 로그 — app.log 뷰어, 내보내기
 */
public class SettingsWindow {

    private static final Logger LOGGER = Logger.getLogger(SettingsWindow.class.getName());

    /** 창은 앱 수명 동안 하나만 존재한다. */
    private static Stage stage;

    // ── 탭 패널 ──────────────────────────────────
    @FXML private TabPane tabPane;

    // ── 설정 탭 ──────────────────────────────────
    @FXML private TextField  sourceFolderField;
    @FXML private TextField  backupFolderField;
    @FXML private Spinner<Integer> backupHourSpinner;
    @FXML private Spinner<Integer> backupMinuteSpinner;
    @FXML private Spinner<Integer> retentionDaysSpinner;
    @FXML private CheckBox   autoStartCheckBox;
    @FXML private CheckBox   incrementalBackupCheckBox;
    @FXML private CheckBox   showNotificationCheckBox;
    @FXML private Button     backupNowButton;
    @FXML private Label      statusLabel;

    // ── 이력 탭 ──────────────────────────────────
    @FXML private TableView<BackupResult>          historyTable;
    @FXML private TableColumn<BackupResult,String> colStartTime;
    @FXML private TableColumn<BackupResult,String> colStatus;
    @FXML private TableColumn<BackupResult,String> colCopiedFiles;
    @FXML private TableColumn<BackupResult,String> colTotalFiles;
    @FXML private TableColumn<BackupResult,String> colSize;
    @FXML private TableColumn<BackupResult,String> colDuration;
    @FXML private TitledPane errorPane;
    @FXML private TextArea   errorDetailArea;
    @FXML private ProgressBar diskUsageBar;
    @FXML private Label      diskUsageLabel;

    // ── 로그 탭 ──────────────────────────────────
    @FXML private TextArea logArea;

    private final ConfigManager configManager = ConfigManager.getInstance();
    private BackupService backupService;

    /** FXMLLoader가 생성한 현재 컨트롤러 인스턴스 (TrayManager 콜백용). */
    private static SettingsWindow controller;

    /** 설정 저장 후 실행할 콜백 (BackupScheduler::reschedule 주입용). */
    private static Runnable onConfigSaved;

    // ───────────────────────────────────────────
    // 정적 진입점 (TrayManager, App에서 호출)
    // ───────────────────────────────────────────

    /** 설정창을 연다. 이미 열려 있으면 앞으로 가져온다. */
    public static void show() {
        if (stage != null && stage.isShowing()) {
            stage.toFront();
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(
                SettingsWindow.class.getResource("/fxml/settings.fxml"));
            Parent root = loader.load();
            controller = loader.getController();
            stage = new Stage();
            stage.setTitle("ObsidianBackup — 설정");
            stage.setScene(new Scene(root));
            stage.setMinWidth(620);
            stage.setMinHeight(500);
            stage.show();
        } catch (IOException e) {
            LOGGER.severe("settings.fxml 로드 실패: " + e.getMessage());
        }
    }

    /**
     * 설정창을 열고 백업 이력 탭으로 전환한다.
     * TrayManager의 "백업 이력" 메뉴에서 호출된다.
     */
    public static void showHistoryTab() {
        show();
        if (controller != null) {
            controller.switchToHistoryTab();
        }
    }

    /**
     * 설정 저장 후 호출될 콜백을 등록한다.
     * App에서 BackupScheduler::reschedule을 주입한다.
     */
    public static void setOnConfigSaved(Runnable callback) {
        onConfigSaved = callback;
    }

    /**
     * 창이 열려 있을 때만 이력을 새로고침한다.
     * TrayManager 백업 완료 후 호출된다.
     */
    public static void refreshIfVisible() {
        if (stage != null && stage.isShowing() && controller != null) {
            controller.refreshHistory();
        }
    }

    /** 현재 Stage 참조 (TrayManager에서 Owner 지정용). */
    public static Stage getStage() {
        return stage;
    }

    // ───────────────────────────────────────────
    // FXML 초기화
    // ───────────────────────────────────────────

    @FXML
    private void initialize() {
        backupService = new BackupService();

        initSpinners();
        initHistoryTable();
        loadConfigToUI();
        refreshHistory();
        refreshLog();
    }

    // ───────────────────────────────────────────
    // 초기화 헬퍼
    // ───────────────────────────────────────────

    private void initSpinners() {
        backupHourSpinner.setValueFactory(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 23, 2));
        backupMinuteSpinner.setValueFactory(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 59, 0));
        retentionDaysSpinner.setValueFactory(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 365, 30));

        backupHourSpinner.setEditable(true);
        backupMinuteSpinner.setEditable(true);
        retentionDaysSpinner.setEditable(true);
    }

    private void initHistoryTable() {
        colStartTime.setCellValueFactory(d ->
            new SimpleStringProperty(d.getValue().getStartTime()));
        colStatus.setCellValueFactory(d ->
            new SimpleStringProperty(statusEmoji(d.getValue().getStatus())));
        colCopiedFiles.setCellValueFactory(d ->
            new SimpleStringProperty(String.valueOf(d.getValue().getCopiedFiles())));
        colTotalFiles.setCellValueFactory(d ->
            new SimpleStringProperty(String.valueOf(d.getValue().getTotalFiles())));
        colSize.setCellValueFactory(d ->
            new SimpleStringProperty(formatBytes(d.getValue().getTotalSizeBytes())));
        colDuration.setCellValueFactory(d ->
            new SimpleStringProperty(formatDuration(d.getValue().getDurationMs())));

        // 실패 행 선택 시 오류 상세 표시
        historyTable.getSelectionModel().selectedItemProperty().addListener(
            (obs, old, sel) -> {
                boolean showError = sel != null
                    && sel.getStatus() == BackupResult.Status.FAILED
                    && sel.getErrorMessage() != null;
                if (showError) {
                    errorDetailArea.setText(sel.getErrorMessage());
                    errorPane.setExpanded(true);
                }
                errorPane.setVisible(showError);
                errorPane.setManaged(showError);
            });
    }

    private void loadConfigToUI() {
        AppConfig cfg = configManager.load();
        sourceFolderField.setText(cfg.getSourceFolder());
        backupFolderField.setText(cfg.getBackupFolder());
        backupHourSpinner.getValueFactory().setValue(cfg.getBackupHour());
        backupMinuteSpinner.getValueFactory().setValue(cfg.getBackupMinute());
        retentionDaysSpinner.getValueFactory().setValue(cfg.getRetentionDays());
        autoStartCheckBox.setSelected(cfg.isAutoStart());
        incrementalBackupCheckBox.setSelected(cfg.isIncrementalBackup());
        showNotificationCheckBox.setSelected(cfg.isShowNotification());
    }

    // ───────────────────────────────────────────
    // 설정 탭 — 이벤트 핸들러
    // ───────────────────────────────────────────

    @FXML
    private void browseSourceFolder() {
        File dir = chooseDirectory("소스 폴더 선택", sourceFolderField.getText());
        if (dir != null) sourceFolderField.setText(dir.getAbsolutePath());
    }

    @FXML
    private void browseBackupFolder() {
        File dir = chooseDirectory("백업 폴더 선택", backupFolderField.getText());
        if (dir != null) backupFolderField.setText(dir.getAbsolutePath());
    }

    @FXML
    private void saveConfig() {
        AppConfig cfg = configManager.getCached() != null
            ? configManager.getCached() : new AppConfig();

        cfg.setSourceFolder(sourceFolderField.getText().trim());
        cfg.setBackupFolder(backupFolderField.getText().trim());
        cfg.setBackupHour(backupHourSpinner.getValue());
        cfg.setBackupMinute(backupMinuteSpinner.getValue());
        cfg.setRetentionDays(retentionDaysSpinner.getValue());
        cfg.setAutoStart(autoStartCheckBox.isSelected());
        cfg.setIncrementalBackup(incrementalBackupCheckBox.isSelected());
        cfg.setShowNotification(showNotificationCheckBox.isSelected());

        boolean ok = configManager.save(cfg);
        setStatus(ok ? "설정이 저장되었습니다." : "설정 저장에 실패했습니다.", !ok);

        if (ok) {
            // autoStart 레지스트리 갱신 (Windows만 동작, 다른 OS는 무시)
            com.obsidianbackup.scheduler.BackupScheduler.setAutoStart(
                cfg.isAutoStart(), null);

            // 스케줄러 재예약
            if (onConfigSaved != null) onConfigSaved.run();
        }
    }

    @FXML
    private void runBackupNow() {
        backupNowButton.setDisable(true);
        setStatus("백업 진행 중...", false);

        Task<BackupResult> task = new Task<>() {
            @Override
            protected BackupResult call() {
                return backupService.run();
            }
        };

        task.setOnSucceeded(e -> {
            BackupResult r = task.getValue();
            boolean failed = r.getStatus() == BackupResult.Status.FAILED;
            setStatus(
                failed ? "백업 실패: " + truncate(r.getErrorMessage(), 80)
                       : "백업 완료: " + r.getCopiedFiles() + "개 파일 복사 ("
                         + formatBytes(r.getTotalSizeBytes()) + ")",
                failed);
            backupNowButton.setDisable(false);
            refreshHistory();
        });

        task.setOnFailed(e -> {
            setStatus("예상치 못한 오류: " + task.getException().getMessage(), true);
            backupNowButton.setDisable(false);
        });

        Thread t = new Thread(task, "backup-thread");
        t.setDaemon(true);
        t.start();
    }

    // ───────────────────────────────────────────
    // 이력 탭
    // ───────────────────────────────────────────

    /** 이력 탭(인덱스 1)으로 전환하고 데이터를 새로고침한다. */
    private void switchToHistoryTab() {
        tabPane.getSelectionModel().select(1);
        refreshHistory();
    }

    private void refreshHistory() {
        Path appDataDir = configManager.getConfigPath().getParent();
        BackupHistory history = BackupHistory.getInstance(appDataDir);
        List<BackupResult> results = history.loadAll();
        historyTable.getItems().setAll(results);
        updateDiskUsage();
    }

    private void updateDiskUsage() {
        AppConfig cfg = configManager.getCached();
        if (cfg == null || cfg.getBackupFolder() == null || cfg.getBackupFolder().isBlank()) {
            diskUsageLabel.setText("");
            diskUsageBar.setProgress(0);
            return;
        }
        try {
            Path backupPath = Path.of(cfg.getBackupFolder());
            if (!Files.exists(backupPath)) {
                diskUsageLabel.setText("폴더 없음");
                diskUsageBar.setProgress(0);
                return;
            }
            FileStore store = Files.getFileStore(backupPath);
            long total  = store.getTotalSpace();
            long usable = store.getUsableSpace();
            long used   = total - usable;

            double ratio = total > 0 ? (double) used / total : 0;
            diskUsageBar.setProgress(ratio);
            diskUsageLabel.setText(formatBytes(used) + " / " + formatBytes(total)
                + String.format(" (%.1f%%)", ratio * 100));
        } catch (IOException e) {
            diskUsageLabel.setText("용량 조회 실패");
        }
    }

    // ───────────────────────────────────────────
    // 로그 탭
    // ───────────────────────────────────────────

    @FXML
    private void refreshLog() {
        Path logPath = configManager.getConfigPath().getParent().resolve("app.log");
        if (!Files.exists(logPath)) {
            logArea.setText("로그 파일이 없습니다.\n경로: " + logPath);
            return;
        }
        try {
            List<String> lines = Files.readAllLines(logPath, StandardCharsets.UTF_8);
            // 최근 500줄만 표시
            int from = Math.max(0, lines.size() - 500);
            logArea.setText(String.join("\n", lines.subList(from, lines.size())));
            logArea.setScrollTop(Double.MAX_VALUE);
        } catch (IOException e) {
            logArea.setText("로그 읽기 실패: " + e.getMessage());
        }
    }

    @FXML
    private void exportLog() {
        Path logPath = configManager.getConfigPath().getParent().resolve("app.log");

        FileChooser fc = new FileChooser();
        fc.setTitle("로그 내보내기");
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("텍스트 파일", "*.txt", "*.log"));
        fc.setInitialFileName("obsidian-backup.log");

        File dest = fc.showSaveDialog(stage);
        if (dest == null) return;

        try {
            Files.copy(logPath, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            setStatus("로그를 내보냈습니다: " + dest.getAbsolutePath(), false);
        } catch (IOException e) {
            setStatus("로그 내보내기 실패: " + e.getMessage(), true);
        }
    }

    // ───────────────────────────────────────────
    // 내부 유틸
    // ───────────────────────────────────────────

    private void setStatus(String message, boolean error) {
        statusLabel.setText(message);
        statusLabel.setStyle(error
            ? "-fx-text-fill: #c0392b; -fx-font-weight: bold;"
            : "-fx-text-fill: #27ae60; -fx-font-weight: bold;");
    }

    private File chooseDirectory(String title, String initialPath) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(title);
        if (initialPath != null && !initialPath.isBlank()) {
            File init = new File(initialPath);
            if (init.isDirectory()) chooser.setInitialDirectory(init);
        }
        return chooser.showDialog(stage);
    }

    private String statusEmoji(BackupResult.Status status) {
        return switch (status) {
            case SUCCESS    -> "성공";
            case FAILED     -> "실패";
            case IN_PROGRESS -> "진행 중";
        };
    }

    private String formatBytes(long bytes) {
        if (bytes < 1_024L)           return bytes + " B";
        if (bytes < 1_048_576L)       return String.format("%.1f KB", bytes / 1_024.0);
        if (bytes < 1_073_741_824L)   return String.format("%.1f MB", bytes / 1_048_576.0);
        return                               String.format("%.2f GB", bytes / 1_073_741_824.0);
    }

    private String formatDuration(long ms) {
        if (ms < 1_000)    return ms + "ms";
        if (ms < 60_000)   return String.format("%.1f초", ms / 1_000.0);
        return String.format("%d분 %d초", ms / 60_000, (ms % 60_000) / 1_000);
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
