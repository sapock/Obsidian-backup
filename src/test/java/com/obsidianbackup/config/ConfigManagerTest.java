package com.obsidianbackup.config;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ConfigManager 단위 테스트.
 *
 * @TempDir 를 사용해 테스트마다 격리된 임시 디렉터리를 생성하므로
 * 실제 %APPDATA% 를 건드리지 않는다.
 */
class ConfigManagerTest {

    @TempDir
    Path tempDir;

    /** 각 테스트 전에 싱글턴을 tempDir 기반으로 재초기화 */
    @BeforeEach
    void resetSingleton() throws Exception {
        // ConfigManager 싱글턴 인스턴스 초기화
        Field instanceField = ConfigManager.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);

        // APPDATA 환경 변수 대신 tempDir 를 가리키도록 configPath 교체
        // (ConfigManager 내부에서 APPDATA 를 읽으므로, 테스트에서는
        //  configPath 필드를 직접 주입한다)
        ConfigManager mgr = ConfigManager.getInstance();
        Field pathField = ConfigManager.class.getDeclaredField("configPath");
        pathField.setAccessible(true);
        pathField.set(mgr, tempDir.resolve("ObsidianBackup").resolve("config.json"));
    }

    // ───────────────────────────────────────────
    // load() 테스트
    // ───────────────────────────────────────────

    @Test
    @DisplayName("config.json 이 없으면 기본값 AppConfig 를 생성하고 파일을 저장한다")
    void load_createsDefaultConfigWhenFileNotFound() {
        ConfigManager mgr = ConfigManager.getInstance();
        AppConfig config = mgr.load();

        assertNotNull(config, "config should not be null");
        assertEquals("", config.getSourceFolder());
        assertEquals("", config.getBackupFolder());
        assertEquals(2, config.getBackupHour());
        assertEquals(0, config.getBackupMinute());
        assertEquals(30, config.getRetentionDays());
        assertTrue(config.isAutoStart());
        assertTrue(config.isIncrementalBackup());
        assertFalse(config.isShowNotification());

        // 파일이 실제로 생성됐는지 확인
        assertTrue(Files.exists(mgr.getConfigPath()), "config.json should be created");
    }

    @Test
    @DisplayName("저장 후 load() 하면 동일한 값을 반환한다")
    void saveAndLoad_roundTrip() {
        ConfigManager mgr = ConfigManager.getInstance();

        AppConfig original = new AppConfig();
        original.setSourceFolder("C:\\Obsidian\\MyVault");
        original.setBackupFolder("D:\\Backup");
        original.setBackupHour(3);
        original.setBackupMinute(30);
        original.setRetentionDays(14);
        original.setAutoStart(false);
        original.setIncrementalBackup(false);
        original.setShowNotification(true);
        original.setLastBackupTime("2026-05-12T03:30:00");

        boolean saved = mgr.save(original);
        assertTrue(saved, "save should succeed");

        // 캐시 우회를 위해 reload() 사용
        AppConfig loaded = mgr.reload();

        assertEquals(original.getSourceFolder(), loaded.getSourceFolder());
        assertEquals(original.getBackupFolder(), loaded.getBackupFolder());
        assertEquals(original.getBackupHour(), loaded.getBackupHour());
        assertEquals(original.getBackupMinute(), loaded.getBackupMinute());
        assertEquals(original.getRetentionDays(), loaded.getRetentionDays());
        assertEquals(original.isAutoStart(), loaded.isAutoStart());
        assertEquals(original.isIncrementalBackup(), loaded.isIncrementalBackup());
        assertEquals(original.isShowNotification(), loaded.isShowNotification());
        assertEquals(original.getLastBackupTime(), loaded.getLastBackupTime());
    }

    @Test
    @DisplayName("JSON 이 깨진 파일이 있을 때 기본값으로 폴백한다")
    void load_fallsBackToDefaultOnCorruptFile() throws Exception {
        ConfigManager mgr = ConfigManager.getInstance();
        Path configPath = mgr.getConfigPath();

        // 디렉터리 생성 후 손상된 JSON 파일 작성
        Files.createDirectories(configPath.getParent());
        Files.writeString(configPath, "{ this is not valid json !!!");

        AppConfig config = mgr.load();

        assertNotNull(config, "should fall back to default, not throw");
        assertEquals("", config.getSourceFolder(), "fallback should have empty source folder");
    }

    // ───────────────────────────────────────────
    // save() 테스트
    // ───────────────────────────────────────────

    @Test
    @DisplayName("save(null) 은 false 를 반환하고 예외를 던지지 않는다")
    void save_withNullConfig_returnsFalse() {
        ConfigManager mgr = ConfigManager.getInstance();
        boolean result = mgr.save(null);
        assertFalse(result);
    }

    // ───────────────────────────────────────────
    // updateLastBackupTime() 테스트
    // ───────────────────────────────────────────

    @Test
    @DisplayName("updateLastBackupTime() 은 lastBackupTime 만 업데이트하고 나머지 필드는 유지한다")
    void updateLastBackupTime_preservesOtherFields() {
        ConfigManager mgr = ConfigManager.getInstance();

        AppConfig config = mgr.load();
        config.setSourceFolder("C:\\Vault");
        config.setRetentionDays(7);
        mgr.save(config);

        mgr.updateLastBackupTime("2026-05-12T02:00:00");
        AppConfig updated = mgr.reload();

        assertEquals("2026-05-12T02:00:00", updated.getLastBackupTime());
        assertEquals("C:\\Vault", updated.getSourceFolder(), "other fields must not change");
        assertEquals(7, updated.getRetentionDays(), "retentionDays must not change");
    }

    // ───────────────────────────────────────────
    // AppConfig 유효성 검사 테스트
    // ───────────────────────────────────────────

    @Test
    @DisplayName("소스·백업 폴더가 모두 채워지면 isValid() 는 true 를 반환한다")
    void appConfig_isValid_whenBothFoldersSet() {
        AppConfig config = new AppConfig();
        assertFalse(config.isValid(), "empty config should not be valid");

        config.setSourceFolder("C:\\Vault");
        assertFalse(config.isValid(), "only source set should not be valid");

        config.setBackupFolder("D:\\Backup");
        assertTrue(config.isValid(), "both folders set should be valid");
    }

    @Test
    @DisplayName("backupHour 범위를 벗어나면 IllegalArgumentException 을 던진다")
    void appConfig_setBackupHour_throwsOnInvalidRange() {
        AppConfig config = new AppConfig();
        assertThrows(IllegalArgumentException.class, () -> config.setBackupHour(-1));
        assertThrows(IllegalArgumentException.class, () -> config.setBackupHour(24));
        assertDoesNotThrow(() -> config.setBackupHour(0));
        assertDoesNotThrow(() -> config.setBackupHour(23));
    }

    @Test
    @DisplayName("backupMinute 범위를 벗어나면 IllegalArgumentException 을 던진다")
    void appConfig_setBackupMinute_throwsOnInvalidRange() {
        AppConfig config = new AppConfig();
        assertThrows(IllegalArgumentException.class, () -> config.setBackupMinute(-1));
        assertThrows(IllegalArgumentException.class, () -> config.setBackupMinute(60));
        assertDoesNotThrow(() -> config.setBackupMinute(0));
        assertDoesNotThrow(() -> config.setBackupMinute(59));
    }

    @Test
    @DisplayName("retentionDays 가 0 이하이면 IllegalArgumentException 을 던진다")
    void appConfig_setRetentionDays_throwsOnZeroOrNegative() {
        AppConfig config = new AppConfig();
        assertThrows(IllegalArgumentException.class, () -> config.setRetentionDays(0));
        assertThrows(IllegalArgumentException.class, () -> config.setRetentionDays(-5));
        assertDoesNotThrow(() -> config.setRetentionDays(1));
    }
}
