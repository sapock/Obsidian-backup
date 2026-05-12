package com.obsidianbackup.backup;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 백업 한 회 실행 결과를 담는 불변 데이터 모델.
 * BackupHistory가 JSON 배열로 직렬화하여 history.json에 저장한다.
 */
public class BackupResult {

    public enum Status {
        IN_PROGRESS, SUCCESS, FAILED
    }

    private static final DateTimeFormatter ID_FMT =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter ISO_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    // ───────────────────────────────────────────
    // 필드
    // ───────────────────────────────────────────

    /** 고유 ID (시작 시각 기반: "20260512-020000") */
    private String id;

    /** 백업 시작 시각 (ISO-8601) */
    private String startTime;

    /** 백업 종료 시각 (ISO-8601). 진행 중이면 null */
    private String endTime;

    /** 결과 상태 */
    private Status status;

    /** 실제로 복사된 파일 수 */
    private int copiedFiles;

    /** 소스 폴더의 전체 파일 수 */
    private int totalFiles;

    /** 복사된 파일 총 바이트 */
    private long totalSizeBytes;

    /** 소요 시간 (밀리초) */
    private long durationMs;

    /** 실패 시 오류 메시지. 성공이면 null */
    private String errorMessage;

    // ───────────────────────────────────────────
    // 팩토리 메서드
    // ───────────────────────────────────────────

    /** 진행 중 상태로 시작 레코드를 만든다. */
    public static BackupResult start(LocalDateTime startTime) {
        BackupResult r = new BackupResult();
        r.id = startTime.format(ID_FMT);
        r.startTime = startTime.format(ISO_FMT);
        r.status = Status.IN_PROGRESS;
        return r;
    }

    /** 성공 결과로 완료 처리한다 (새 객체 반환). */
    public BackupResult succeed(LocalDateTime endTime, int copiedFiles,
                                int totalFiles, long totalSizeBytes) {
        BackupResult r = copy();
        r.endTime = endTime.format(ISO_FMT);
        r.status = Status.SUCCESS;
        r.copiedFiles = copiedFiles;
        r.totalFiles = totalFiles;
        r.totalSizeBytes = totalSizeBytes;
        r.durationMs = calcDurationMs(endTime);
        return r;
    }

    /** 실패 결과로 완료 처리한다 (새 객체 반환). */
    public BackupResult fail(LocalDateTime endTime, String errorMessage) {
        BackupResult r = copy();
        r.endTime = endTime.format(ISO_FMT);
        r.status = Status.FAILED;
        r.errorMessage = errorMessage;
        r.durationMs = calcDurationMs(endTime);
        return r;
    }

    // ───────────────────────────────────────────
    // 내부 유틸
    // ───────────────────────────────────────────

    private BackupResult copy() {
        BackupResult r = new BackupResult();
        r.id = this.id;
        r.startTime = this.startTime;
        r.copiedFiles = this.copiedFiles;
        r.totalFiles = this.totalFiles;
        r.totalSizeBytes = this.totalSizeBytes;
        r.durationMs = this.durationMs;
        return r;
    }

    private long calcDurationMs(LocalDateTime endTime) {
        LocalDateTime start = LocalDateTime.parse(this.startTime, ISO_FMT);
        return java.time.Duration.between(start, endTime).toMillis();
    }

    // ───────────────────────────────────────────
    // Getters (불변 조회용)
    // ───────────────────────────────────────────

    public String getId()            { return id; }
    public String getStartTime()     { return startTime; }
    public String getEndTime()       { return endTime; }
    public Status getStatus()        { return status; }
    public int getCopiedFiles()      { return copiedFiles; }
    public int getTotalFiles()       { return totalFiles; }
    public long getTotalSizeBytes()  { return totalSizeBytes; }
    public long getDurationMs()      { return durationMs; }
    public String getErrorMessage()  { return errorMessage; }

    @Override
    public String toString() {
        return "BackupResult{id='" + id + "', status=" + status
            + ", copied=" + copiedFiles + "/" + totalFiles
            + ", sizeBytes=" + totalSizeBytes
            + ", durationMs=" + durationMs + '}';
    }
}
