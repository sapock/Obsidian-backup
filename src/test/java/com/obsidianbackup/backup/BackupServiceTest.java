package com.obsidianbackup.backup;

import com.obsidianbackup.config.AppConfig;
import com.obsidianbackup.config.ConfigManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BackupService 단위 테스트.
 *
 * ConfigManager·BackupHistory 모두 임시 디렉터리로 격리하여
 * 실제 파일 시스템에 영향을 주지 않는다.
 */
class BackupServiceTest {

    @TempDir
    Path tempDir;

    private ConfigManager configManager;
    private BackupHistory  backupHistory;
    private BackupService  backupService;

    @BeforeEach
    void setUp() throws Exception {
        // ConfigManager 싱글턴 리셋 및 tempDir 경로 주입
        Field instanceField = ConfigManager.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);

        configManager = ConfigManager.getInstance();
        Field pathField = ConfigManager.class.getDeclaredField("configPath");
        pathField.setAccessible(true);
        pathField.set(configManager, tempDir.resolve("config.json"));

        // BackupHistory 싱글턴 리셋 및 tempDir 경로 주입
        BackupHistory.resetInstance();
        backupHistory = BackupHistory.getInstance(tempDir);
        backupHistory.setHistoryPath(tempDir.resolve("history.json"));

        backupService = new BackupService(configManager, backupHistory);
    }

    // ───────────────────────────────────────────
    // 설정 유효성 실패
    // ───────────────────────────────────────────

    @Test
    @DisplayName("설정이 비어있으면 FAILED 결과를 반환한다")
    void run_failsWhenConfigInvalid() {
        configManager.load(); // 기본 빈 설정

        BackupResult result = backupService.run();

        assertEquals(BackupResult.Status.FAILED, result.getStatus());
        assertNotNull(result.getErrorMessage());
    }

    // ───────────────────────────────────────────
    // 전체 백업
    // ───────────────────────────────────────────

    @Test
    @DisplayName("전체 백업: 소스의 모든 파일이 날짜 폴더 하위로 복사된다")
    void run_fullBackup_copiesAllFiles() throws Exception {
        Path source = tempDir.resolve("source");
        Path dest   = tempDir.resolve("dest");
        Files.createDirectories(source);

        Files.writeString(source.resolve("note1.md"), "# Note 1");
        Files.writeString(source.resolve("note2.md"), "# Note 2");
        Path sub = source.resolve("subfolder");
        Files.createDirectories(sub);
        Files.writeString(sub.resolve("nested.md"), "# Nested");

        AppConfig config = configManager.load();
        config.setSourceFolder(source.toString());
        config.setBackupFolder(dest.toString());
        config.setIncrementalBackup(false);
        configManager.save(config);

        BackupResult result = backupService.run();

        assertEquals(BackupResult.Status.SUCCESS, result.getStatus());
        assertEquals(3, result.getCopiedFiles());
        assertEquals(3, result.getTotalFiles());
        assertTrue(result.getTotalSizeBytes() > 0);

        // 날짜 폴더 하위에 파일이 실제로 존재하는지 확인
        String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        assertTrue(Files.exists(dest.resolve(today).resolve("note1.md")));
        assertTrue(Files.exists(dest.resolve(today).resolve("subfolder").resolve("nested.md")));
    }

    // ───────────────────────────────────────────
    // 증분 백업
    // ───────────────────────────────────────────

    @Test
    @DisplayName("증분 백업: 변경된 파일만 복사된다")
    void run_incrementalBackup_copiesOnlyChanged() throws Exception {
        Path source = tempDir.resolve("source");
        Path dest   = tempDir.resolve("dest");
        Files.createDirectories(source);

        Path file1 = source.resolve("unchanged.md");
        Path file2 = source.resolve("changed.md");
        Files.writeString(file1, "unchanged content");
        Files.writeString(file2, "original content");

        AppConfig config = configManager.load();
        config.setSourceFolder(source.toString());
        config.setBackupFolder(dest.toString());
        config.setIncrementalBackup(true);
        configManager.save(config);

        // 1회 전체 백업
        BackupResult first = backupService.run();
        assertEquals(BackupResult.Status.SUCCESS, first.getStatus());
        assertEquals(2, first.getCopiedFiles());

        // file2 수정 (변경 시각이 대상보다 미래가 되도록 1초 후 수정)
        Thread.sleep(1100);
        Files.writeString(file2, "updated content");

        // 2회 증분 백업
        BackupResult second = backupService.run();
        assertEquals(BackupResult.Status.SUCCESS, second.getStatus());
        assertEquals(1, second.getCopiedFiles(), "only changed file should be copied");
        assertEquals(2, second.getTotalFiles());
    }

    // ───────────────────────────────────────────
    // 보관 기간 삭제
    // ───────────────────────────────────────────

    @Test
    @DisplayName("보관 기간 초과 날짜 폴더가 삭제된다")
    void deleteExpiredFolders_removesOldFolders() throws Exception {
        Path backupRoot = tempDir.resolve("backup");
        Files.createDirectories(backupRoot);

        // 오래된 폴더 (보관 기간 초과)
        Path old = backupRoot.resolve("2026-04-01");
        Files.createDirectories(old);
        Files.writeString(old.resolve("old.md"), "old");

        // 최근 폴더 (보관 기간 이내)
        Path recent = backupRoot.resolve("2026-05-11");
        Files.createDirectories(recent);
        Files.writeString(recent.resolve("recent.md"), "recent");

        backupService.deleteExpiredFolders(backupRoot, 7, "2026-05-12");

        assertFalse(Files.exists(old),    "expired folder should be deleted");
        assertTrue(Files.exists(recent), "recent folder should remain");
    }

    @Test
    @DisplayName("보관 기간 내 폴더는 삭제되지 않는다")
    void deleteExpiredFolders_keepsRecentFolders() throws Exception {
        Path backupRoot = tempDir.resolve("backup");
        Files.createDirectories(backupRoot);

        Path folder = backupRoot.resolve("2026-05-10");
        Files.createDirectories(folder);

        backupService.deleteExpiredFolders(backupRoot, 30, "2026-05-12");

        assertTrue(Files.exists(folder), "folder within retention should not be deleted");
    }

    @Test
    @DisplayName("날짜 형식이 아닌 폴더는 삭제되지 않는다")
    void deleteExpiredFolders_ignoresNonDateFolders() throws Exception {
        Path backupRoot = tempDir.resolve("backup");
        Files.createDirectories(backupRoot);

        Path other = backupRoot.resolve("miscFolder");
        Files.createDirectories(other);

        backupService.deleteExpiredFolders(backupRoot, 1, "2026-05-12");

        assertTrue(Files.exists(other), "non-date folder must not be touched");
    }

    // ───────────────────────────────────────────
    // 이력 저장 확인
    // ───────────────────────────────────────────

    @Test
    @DisplayName("백업 완료 후 이력이 history.json 에 저장된다")
    void run_savesHistoryOnSuccess() throws Exception {
        Path source = tempDir.resolve("src");
        Path dest   = tempDir.resolve("dst");
        Files.createDirectories(source);
        Files.writeString(source.resolve("a.md"), "a");

        AppConfig config = configManager.load();
        config.setSourceFolder(source.toString());
        config.setBackupFolder(dest.toString());
        configManager.save(config);

        backupService.run();

        List<BackupResult> history = backupHistory.loadAll();
        assertFalse(history.isEmpty(), "history should not be empty after backup");
        assertEquals(BackupResult.Status.SUCCESS, history.get(0).getStatus());
    }

    // ───────────────────────────────────────────
    // 소스 폴더 없음
    // ───────────────────────────────────────────

    @Test
    @DisplayName("소스 폴더가 존재하지 않으면 FAILED 결과를 반환한다")
    void run_failsWhenSourceDoesNotExist() throws Exception {
        AppConfig config = configManager.load();
        config.setSourceFolder(tempDir.resolve("nonexistent").toString());
        config.setBackupFolder(tempDir.resolve("dest").toString());
        configManager.save(config);

        BackupResult result = backupService.run();

        assertEquals(BackupResult.Status.FAILED, result.getStatus());
        assertNotNull(result.getErrorMessage());
    }
}
