package com.obsidianbackup.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * AppConfig를 JSON 파일로 저장하고 불러오는 싱글턴 매니저.
 *
 * 설정 파일 위치:
 *   Windows: %APPDATA%/ObsidianBackup/config.json
 *   (예) C:/Users/user/AppData/Roaming/ObsidianBackup/config.json
 *
 * 사용 예:
 *   AppConfig config = ConfigManager.getInstance().load();
 *   config.setSourceFolder("C:\\Obsidian\\MyVault");
 *   ConfigManager.getInstance().save(config);
 */
public class ConfigManager {

    private static final Logger LOGGER = Logger.getLogger(ConfigManager.class.getName());

    /** 앱 데이터 디렉터리 이름 */
    private static final String APP_DIR_NAME = "ObsidianBackup";

    /** 설정 파일 이름 */
    private static final String CONFIG_FILE_NAME = "config.json";

    /** 싱글턴 인스턴스 */
    private static ConfigManager instance;

    /** Gson 인스턴스 (pretty-print 포맷) */
    private final Gson gson;

    /** 설정 파일 경로 */
    private final Path configPath;

    /** 현재 로드된 설정 (메모리 캐시) */
    private AppConfig cachedConfig;

    // ───────────────────────────────────────────
    // 싱글턴 초기화
    // ───────────────────────────────────────────

    private ConfigManager() {
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .serializeNulls()
            .create();

        this.configPath = resolveConfigPath();
        LOGGER.info("Config path: " + configPath);
    }

    /**
     * 싱글턴 인스턴스를 반환한다.
     */
    public static synchronized ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }

    // ───────────────────────────────────────────
    // 설정 로드
    // ───────────────────────────────────────────

    /**
     * 설정 파일을 읽어 AppConfig를 반환한다.
     *
     * - 파일이 없으면 기본값 AppConfig를 생성하고 즉시 저장 후 반환한다.
     * - 파일이 손상된 경우 기본값으로 폴백하고 경고 로그를 남긴다.
     * - 결과는 메모리에 캐시된다.
     *
     * @return 로드된 AppConfig (null 아님)
     */
    public AppConfig load() {
        ensureDirectoryExists();

        if (!Files.exists(configPath)) {
            LOGGER.info("config.json not found. Creating default config.");
            AppConfig defaultConfig = new AppConfig();
            save(defaultConfig);
            cachedConfig = defaultConfig;
            return defaultConfig;
        }

        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            AppConfig config = gson.fromJson(reader, AppConfig.class);

            if (config == null) {
                LOGGER.warning("config.json is empty. Using default config.");
                config = new AppConfig();
            }

            LOGGER.fine("Config loaded: " + config);
            cachedConfig = config;
            return config;

        } catch (Exception e) {
            LOGGER.log(Level.WARNING,
                "Failed to parse config.json. Falling back to default config.", e);
            AppConfig fallback = new AppConfig();
            cachedConfig = fallback;
            return fallback;
        }
    }

    // ───────────────────────────────────────────
    // 설정 저장
    // ───────────────────────────────────────────

    /**
     * AppConfig를 JSON 파일로 저장한다.
     *
     * 저장에 실패해도 예외를 던지지 않고 로그만 남긴다.
     * (트레이 우클릭 → 설정 저장 흐름에서 앱 중단을 방지하기 위함)
     *
     * @param config 저장할 설정 객체
     * @return 저장 성공 여부
     */
    public boolean save(AppConfig config) {
        if (config == null) {
            LOGGER.warning("save() called with null config. Skipping.");
            return false;
        }

        ensureDirectoryExists();

        try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
            gson.toJson(config, writer);
            cachedConfig = config;
            LOGGER.info("Config saved: " + configPath);
            return true;

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to save config.json", e);
            return false;
        }
    }

    // ───────────────────────────────────────────
    // 캐시 접근
    // ───────────────────────────────────────────

    /**
     * 마지막으로 로드/저장된 설정을 반환한다.
     * load()를 한 번도 호출하지 않은 경우 null을 반환한다.
     *
     * @return 캐시된 AppConfig 또는 null
     */
    public AppConfig getCached() {
        return cachedConfig;
    }

    /**
     * 캐시를 무효화하고 파일에서 다시 로드한다.
     *
     * @return 새로 로드된 AppConfig
     */
    public AppConfig reload() {
        cachedConfig = null;
        return load();
    }

    // ───────────────────────────────────────────
    // 편의 메서드 — lastBackupTime 업데이트
    // ───────────────────────────────────────────

    /**
     * 마지막 백업 시각만 업데이트하고 파일에 저장한다.
     * BackupService 완료 후 호출된다.
     *
     * @param isoDateTime ISO-8601 형식 문자열 (예: "2026-05-12T02:00:00")
     */
    public void updateLastBackupTime(String isoDateTime) {
        AppConfig config = cachedConfig != null ? cachedConfig : load();
        config.setLastBackupTime(isoDateTime);
        save(config);
    }

    // ───────────────────────────────────────────
    // 내부 유틸
    // ───────────────────────────────────────────

    /**
     * OS별 앱 데이터 디렉터리 하위에 설정 파일 경로를 결정한다.
     * Windows: %APPDATA%\ObsidianBackup\config.json
     * 기타 OS(개발·테스트용): user.home/.obsidianbackup/config.json
     */
    private Path resolveConfigPath() {
        String appData = System.getenv("APPDATA");
        Path dir;

        if (appData != null && !appData.isBlank()) {
            dir = Paths.get(appData, APP_DIR_NAME);
        } else {
            // 비 Windows 환경 (개발 시 Mac/Linux)
            dir = Paths.get(System.getProperty("user.home"), "." + APP_DIR_NAME.toLowerCase());
        }

        return dir.resolve(CONFIG_FILE_NAME);
    }

    /**
     * 설정 파일 디렉터리가 없으면 생성한다.
     */
    private void ensureDirectoryExists() {
        Path dir = configPath.getParent();
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
                LOGGER.info("Created app data directory: " + dir);
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Failed to create app data directory: " + dir, e);
            }
        }
    }

    /**
     * 설정 파일의 절대 경로를 반환한다. (UI 표시·디버깅용)
     */
    public Path getConfigPath() {
        return configPath;
    }
}
