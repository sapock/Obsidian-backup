package com.obsidianbackup.backup;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 백업 이력을 history.json 파일로 저장하고 불러오는 싱글턴 매니저.
 *
 * 파일 위치는 ConfigManager의 configPath 부모 디렉터리(앱 데이터 폴더)와 동일하다.
 * 외부에서 setHistoryPath()로 경로를 주입할 수 있어 테스트가 용이하다.
 */
public class BackupHistory {

    private static final Logger LOGGER = Logger.getLogger(BackupHistory.class.getName());
    private static final String HISTORY_FILE_NAME = "history.json";
    private static final int MAX_HISTORY_ENTRIES = 90;

    private static BackupHistory instance;

    private final Gson gson;
    private Path historyPath;
    private List<BackupResult> cache;

    // ───────────────────────────────────────────
    // 싱글턴 초기화
    // ───────────────────────────────────────────

    private BackupHistory(Path historyPath) {
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .serializeNulls()
            .create();
        this.historyPath = historyPath;
    }

    public static synchronized BackupHistory getInstance(Path appDataDir) {
        if (instance == null) {
            instance = new BackupHistory(appDataDir.resolve(HISTORY_FILE_NAME));
        }
        return instance;
    }

    /** 테스트 전용: 싱글턴 리셋 */
    static synchronized void resetInstance() {
        instance = null;
    }

    // ───────────────────────────────────────────
    // 이력 조회
    // ───────────────────────────────────────────

    /**
     * 저장된 이력 전체를 최신 순으로 반환한다.
     */
    public List<BackupResult> loadAll() {
        if (!Files.exists(historyPath)) {
            cache = new ArrayList<>();
            return Collections.unmodifiableList(cache);
        }

        try (Reader reader = Files.newBufferedReader(historyPath, StandardCharsets.UTF_8)) {
            Type listType = new TypeToken<List<BackupResult>>() {}.getType();
            List<BackupResult> list = gson.fromJson(reader, listType);
            cache = (list != null) ? list : new ArrayList<>();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load history.json. Starting fresh.", e);
            cache = new ArrayList<>();
        }

        return Collections.unmodifiableList(cache);
    }

    /**
     * 가장 최근 이력 항목을 반환한다. 없으면 null.
     */
    public BackupResult getLatest() {
        List<BackupResult> all = loadAll();
        return all.isEmpty() ? null : all.get(0);
    }

    // ───────────────────────────────────────────
    // 이력 추가/갱신
    // ───────────────────────────────────────────

    /**
     * 새 결과를 이력 맨 앞에 추가하고 파일에 저장한다.
     * 같은 id가 이미 있으면 교체(갱신)한다.
     */
    public synchronized void append(BackupResult result) {
        if (cache == null) loadAll();

        // 동일 id 교체
        cache.removeIf(r -> r.getId().equals(result.getId()));
        cache.add(0, result);

        // 최대 항목 수 제한
        if (cache.size() > MAX_HISTORY_ENTRIES) {
            cache = new ArrayList<>(cache.subList(0, MAX_HISTORY_ENTRIES));
        }

        persist();
    }

    // ───────────────────────────────────────────
    // 내부 저장
    // ───────────────────────────────────────────

    private void persist() {
        try {
            Files.createDirectories(historyPath.getParent());
            try (Writer writer = Files.newBufferedWriter(historyPath, StandardCharsets.UTF_8)) {
                gson.toJson(cache, writer);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to save history.json", e);
        }
    }

    // ───────────────────────────────────────────
    // 경로 접근 (테스트용)
    // ───────────────────────────────────────────

    public Path getHistoryPath() {
        return historyPath;
    }

    void setHistoryPath(Path path) {
        this.historyPath = path;
        this.cache = null;
    }
}
