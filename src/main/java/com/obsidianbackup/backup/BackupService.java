package com.obsidianbackup.backup;

import com.obsidianbackup.config.AppConfig;
import com.obsidianbackup.config.ConfigManager;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 핵심 백업 로직.
 *
 * 실행 흐름:
 *   1. 설정 유효성 검사
 *   2. 오늘 날짜 폴더 생성: backupFolder/yyyy-MM-dd/
 *   3. 소스 폴더 파일 전체 순회
 *      - 증분 모드: 대상 파일이 없거나 소스가 더 최신이면 복사
 *      - 전체 모드: 무조건 복사
 *   4. 보관 기간 초과 날짜 폴더 삭제
 *   5. 결과를 BackupHistory에 기록, ConfigManager에 lastBackupTime 업데이트
 */
public class BackupService {

    private static final Logger LOGGER = Logger.getLogger(BackupService.class.getName());
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ConfigManager configManager;
    private final BackupHistory backupHistory;

    // ───────────────────────────────────────────
    // 생성자
    // ───────────────────────────────────────────

    /**
     * 기본 생성자: 운영 환경에서 사용.
     * ConfigManager 싱글턴과 ConfigManager 경로 기반 BackupHistory를 사용한다.
     */
    public BackupService() {
        this.configManager = ConfigManager.getInstance();
        AppConfig cfg = configManager.load();
        Path appDataDir = configManager.getConfigPath().getParent();
        this.backupHistory = BackupHistory.getInstance(appDataDir);
    }

    /**
     * 의존성 주입 생성자: 테스트용.
     */
    BackupService(ConfigManager configManager, BackupHistory backupHistory) {
        this.configManager = configManager;
        this.backupHistory = backupHistory;
    }

    // ───────────────────────────────────────────
    // 공개 API
    // ───────────────────────────────────────────

    /**
     * 백업을 동기적으로 실행하고 결과를 반환한다.
     *
     * @return 백업 결과 (성공/실패 모두 반환, 예외 미발생)
     */
    public BackupResult run() {
        AppConfig config = configManager.load();
        LocalDateTime startTime = LocalDateTime.now();
        BackupResult inProgress = BackupResult.start(startTime);
        backupHistory.append(inProgress);

        LOGGER.info("Backup started. source=" + config.getSourceFolder()
            + " dest=" + config.getBackupFolder()
            + " incremental=" + config.isIncrementalBackup());

        if (!config.isValid()) {
            String msg = "Invalid config: sourceFolder or backupFolder is not set.";
            LOGGER.warning(msg);
            BackupResult failed = inProgress.fail(LocalDateTime.now(), msg);
            backupHistory.append(failed);
            return failed;
        }

        Path source = Paths.get(config.getSourceFolder());
        Path destDate = Paths.get(config.getBackupFolder())
            .resolve(startTime.format(DATE_FMT));

        try {
            validateSourceExists(source);
            Files.createDirectories(destDate);

            AtomicInteger copiedCount = new AtomicInteger(0);
            AtomicInteger totalCount  = new AtomicInteger(0);
            AtomicLong    copiedBytes = new AtomicLong(0);

            copyFiles(source, destDate, config.isIncrementalBackup(),
                      copiedCount, totalCount, copiedBytes);

            deleteExpiredFolders(
                Paths.get(config.getBackupFolder()),
                config.getRetentionDays(),
                startTime.toLocalDate().format(DATE_FMT));

            LocalDateTime endTime = LocalDateTime.now();
            BackupResult success = inProgress.succeed(
                endTime, copiedCount.get(), totalCount.get(), copiedBytes.get());

            backupHistory.append(success);
            configManager.updateLastBackupTime(endTime.format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")));

            LOGGER.info("Backup completed. " + success);
            return success;

        } catch (Exception e) {
            String msg = errorMessage(e);
            LOGGER.log(Level.SEVERE, "Backup failed", e);
            BackupResult failed = inProgress.fail(LocalDateTime.now(), msg);
            backupHistory.append(failed);
            return failed;
        }
    }

    // ───────────────────────────────────────────
    // 파일 복사
    // ───────────────────────────────────────────

    /**
     * source 트리 전체를 dest 아래로 복사한다.
     * 증분 모드에서는 대상 파일이 없거나 소스가 더 최신인 경우에만 복사한다.
     */
    private void copyFiles(Path source, Path dest, boolean incremental,
                           AtomicInteger copiedCount, AtomicInteger totalCount,
                           AtomicLong copiedBytes) throws IOException {

        Files.walkFileTree(source, new SimpleFileVisitor<>() {

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                Path relative = source.relativize(dir);
                Path targetDir = dest.resolve(relative);
                Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                totalCount.incrementAndGet();
                Path relative = source.relativize(file);
                Path target   = dest.resolve(relative);

                if (incremental && Files.exists(target)) {
                    long srcModified  = attrs.lastModifiedTime().toMillis();
                    long destModified = Files.getLastModifiedTime(target).toMillis();
                    if (srcModified <= destModified) {
                        return FileVisitResult.CONTINUE; // 변경 없음, 건너뜀
                    }
                }

                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING,
                           StandardCopyOption.COPY_ATTRIBUTES);
                copiedCount.incrementAndGet();
                copiedBytes.addAndGet(attrs.size());
                LOGGER.fine("Copied: " + relative);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                LOGGER.log(Level.WARNING, "Cannot access file, skipping: " + file, exc);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    // ───────────────────────────────────────────
    // 보관 기간 초과 폴더 삭제
    // ───────────────────────────────────────────

    /**
     * backupRoot 하위에서 "yyyy-MM-dd" 형식 폴더 중 retentionDays 초과된 것을 삭제한다.
     *
     * @param backupRoot  백업 루트 디렉터리
     * @param retentionDays 보관 일수
     * @param todayStr    오늘 날짜 문자열 ("yyyy-MM-dd") — 테스트 주입 가능
     */
    void deleteExpiredFolders(Path backupRoot, int retentionDays, String todayStr) {
        if (!Files.isDirectory(backupRoot)) return;

        try (Stream<Path> dirs = Files.list(backupRoot)) {
            List<Path> expired = dirs
                .filter(Files::isDirectory)
                .filter(p -> isExpired(p.getFileName().toString(), todayStr, retentionDays))
                .collect(Collectors.toList());

            for (Path dir : expired) {
                deleteDirectoryTree(dir);
                LOGGER.info("Deleted expired backup folder: " + dir);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to list backup root for retention cleanup", e);
        }
    }

    /**
     * 폴더명이 "yyyy-MM-dd" 패턴이고 보관 기간을 초과했으면 true.
     */
    private boolean isExpired(String folderName, String todayStr, int retentionDays) {
        if (!folderName.matches("\\d{4}-\\d{2}-\\d{2}")) return false;
        try {
            java.time.LocalDate folderDate = java.time.LocalDate.parse(folderName);
            java.time.LocalDate today      = java.time.LocalDate.parse(todayStr);
            return folderDate.isBefore(today.minusDays(retentionDays));
        } catch (Exception e) {
            return false;
        }
    }

    /** 디렉터리 트리를 재귀 삭제한다. */
    private void deleteDirectoryTree(Path dir) throws IOException {
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc)
                    throws IOException {
                Files.delete(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    // ───────────────────────────────────────────
    // 내부 유틸
    // ───────────────────────────────────────────

    private void validateSourceExists(Path source) throws IOException {
        if (!Files.isDirectory(source)) {
            throw new NoSuchFileException(source.toString(),
                null, "Source folder does not exist or is not a directory.");
        }
    }

    private String errorMessage(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String trace = sw.toString();
        // 스택 트레이스가 너무 길면 첫 500자만
        return trace.length() > 500
            ? e.getClass().getSimpleName() + ": " + e.getMessage()
            : trace;
    }
}
