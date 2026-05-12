package com.obsidianbackup.config;

/**
 * 앱 전체 설정을 담는 데이터 모델.
 * ConfigManager가 이 객체를 JSON으로 직렬화/역직렬화한다.
 */
public class AppConfig {

    // ───────────────────────────────────────────
    // 폴더 경로
    // ───────────────────────────────────────────

    /** Obsidian Vault 소스 폴더 경로 */
    private String sourceFolder = "";

    /** 백업 파일이 저장될 대상 폴더 경로 */
    private String backupFolder = "";

    // ───────────────────────────────────────────
    // 스케줄 설정
    // ───────────────────────────────────────────

    /** 매일 백업 실행 시각 - 시 (0~23) */
    private int backupHour = 2;

    /** 매일 백업 실행 시각 - 분 (0~59) */
    private int backupMinute = 0;

    // ───────────────────────────────────────────
    // 보관 정책
    // ───────────────────────────────────────────

    /** 백업 폴더 보관 기간 (일). 초과 시 오래된 날짜 폴더 자동 삭제 */
    private int retentionDays = 30;

    // ───────────────────────────────────────────
    // 동작 옵션
    // ───────────────────────────────────────────

    /** Windows 시작 시 자동 실행 여부 (레지스트리 등록) */
    private boolean autoStart = true;

    /** 증분 백업 사용 여부. true=변경 파일만 복사, false=전체 복사 */
    private boolean incrementalBackup = true;

    /** 백업 완료/실패 시 OS 알림 표시 여부 */
    private boolean showNotification = false;

    // ───────────────────────────────────────────
    // 런타임 상태 (설정창 표시용, 저장됨)
    // ───────────────────────────────────────────

    /** 마지막 백업 성공 시각 (ISO-8601). 없으면 null */
    private String lastBackupTime = null;

    /** 로그 레벨. "INFO" 또는 "DEBUG" */
    private String logLevel = "INFO";

    // ───────────────────────────────────────────
    // 기본 생성자 (Gson 역직렬화에 필요)
    // ───────────────────────────────────────────

    public AppConfig() {}

    // ───────────────────────────────────────────
    // 유효성 검사
    // ───────────────────────────────────────────

    /**
     * 필수 설정이 모두 채워져 있는지 확인한다.
     * @return 소스·백업 폴더가 모두 비어있지 않으면 true
     */
    public boolean isValid() {
        return sourceFolder != null && !sourceFolder.isBlank()
            && backupFolder != null && !backupFolder.isBlank();
    }

    /**
     * 설정 요약 문자열 (디버깅용)
     */
    @Override
    public String toString() {
        return "AppConfig{"
            + "sourceFolder='" + sourceFolder + '\''
            + ", backupFolder='" + backupFolder + '\''
            + ", backupHour=" + backupHour
            + ", backupMinute=" + backupMinute
            + ", retentionDays=" + retentionDays
            + ", autoStart=" + autoStart
            + ", incrementalBackup=" + incrementalBackup
            + ", showNotification=" + showNotification
            + ", lastBackupTime='" + lastBackupTime + '\''
            + ", logLevel='" + logLevel + '\''
            + '}';
    }

    // ───────────────────────────────────────────
    // Getters & Setters
    // ───────────────────────────────────────────

    public String getSourceFolder() {
        return sourceFolder;
    }

    public void setSourceFolder(String sourceFolder) {
        this.sourceFolder = sourceFolder;
    }

    public String getBackupFolder() {
        return backupFolder;
    }

    public void setBackupFolder(String backupFolder) {
        this.backupFolder = backupFolder;
    }

    public int getBackupHour() {
        return backupHour;
    }

    public void setBackupHour(int backupHour) {
        if (backupHour < 0 || backupHour > 23) {
            throw new IllegalArgumentException("backupHour must be 0~23, got: " + backupHour);
        }
        this.backupHour = backupHour;
    }

    public int getBackupMinute() {
        return backupMinute;
    }

    public void setBackupMinute(int backupMinute) {
        if (backupMinute < 0 || backupMinute > 59) {
            throw new IllegalArgumentException("backupMinute must be 0~59, got: " + backupMinute);
        }
        this.backupMinute = backupMinute;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        if (retentionDays < 1) {
            throw new IllegalArgumentException("retentionDays must be >= 1, got: " + retentionDays);
        }
        this.retentionDays = retentionDays;
    }

    public boolean isAutoStart() {
        return autoStart;
    }

    public void setAutoStart(boolean autoStart) {
        this.autoStart = autoStart;
    }

    public boolean isIncrementalBackup() {
        return incrementalBackup;
    }

    public void setIncrementalBackup(boolean incrementalBackup) {
        this.incrementalBackup = incrementalBackup;
    }

    public boolean isShowNotification() {
        return showNotification;
    }

    public void setShowNotification(boolean showNotification) {
        this.showNotification = showNotification;
    }

    public String getLastBackupTime() {
        return lastBackupTime;
    }

    public void setLastBackupTime(String lastBackupTime) {
        this.lastBackupTime = lastBackupTime;
    }

    public String getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }
}
