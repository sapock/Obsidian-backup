# Obsidian Backup

Obsidian Vault를 매일 자동으로 로컬 폴더에 백업하는 Windows 데스크톱 애플리케이션입니다.
시스템 트레이에 상주하며 설정한 시각에 자동으로 증분 백업을 수행합니다.

---

## 목차

1. [기능 요구사항](#기능-요구사항)
2. [기술 스택](#기술-스택)
3. [프로젝트 구조](#프로젝트-구조)
4. [개발 환경 설정](#개발-환경-설정)
5. [빌드 방법](#빌드-방법)
6. [구동 방법](#구동-방법)
7. [설정 파일 스펙](#설정-파일-스펙)
8. [백업 이력 파일 스펙](#백업-이력-파일-스펙)
9. [디버깅 방법](#디버깅-방법)
10. [배포 (.exe 빌드)](#배포-exe-빌드)
11. [개발 로드맵](#개발-로드맵)

---

## 기능 요구사항

### 백업 기능
- 로컬 폴더 → 로컬 폴더 파일 복사
- 마지막 수정 시각 기준 변경된 파일만 복사 (증분 백업)
- 백업 폴더 내 날짜별 서브폴더 자동 생성 (`yyyy-MM-dd/`)
- 즉시 수동 백업 실행 (트레이 메뉴 또는 설정창 버튼)
- 보관 기간 초과 날짜 폴더 자동 삭제

### 스케줄링
- 매일 사용자가 지정한 시각에 자동 백업 실행
- 앱 실행 중 스케줄 재설정 즉시 반영
- Windows 시작 시 자동 실행 (레지스트리 등록/해제)

### 설정 관리
- 소스 폴더 / 백업 폴더 경로 지정
- 백업 실행 시각 (시·분) 설정
- 보관 기간 설정 (기본 30일)
- 설정은 JSON 파일로 영속 저장 (`config.json`)

### UI — 설정창 (JavaFX)
- 설정 / 백업 이력 / 로그 3개 탭
- 폴더 선택 다이얼로그 (DirectoryChooser)
- 백업 이력 테이블: 날짜, 상태, 파일 수, 용량, 소요 시간
- 실패 시 오류 상세 패널
- 백업 저장소 사용량 프로그레스 바

### UI — 시스템 트레이
- 트레이 아이콘 상주 (java.awt.SystemTray)
- 우클릭 팝업 메뉴: 설정 열기 / 지금 백업 / 백업 이력 / 종료
- 마지막 백업 시각 툴팁 표시
- 백업 완료/실패 시 OS 알림 (선택 설정)

### 이력 및 로그
- 백업 결과 JSON 이력 저장 (`history.json`)
- 성공 / 실패 / 진행 중 상태 기록
- 복사 파일 수, 총 용량, 소요 시간 기록
- 실패 시 오류 메시지 및 스택 트레이스 저장
- 로그 파일 텍스트 내보내기

---

## 기술 스택

| 항목 | 내용 |
|------|------|
| 언어 | Java 17 (LTS) |
| UI | JavaFX 21 |
| 빌드 | Gradle 8.x |
| JSON | Gson 2.10.1 |
| 트레이 | java.awt.SystemTray |
| 스케줄링 | ScheduledExecutorService |
| 파일 I/O | java.nio.file (NIO2) |
| 배포 | jpackage → Windows `.exe` / `.msi` |
| 대상 OS | Windows 10 / 11 (64bit) |

---

## 프로젝트 구조

```
obsidian-backup/
├── build.gradle
├── settings.gradle
├── README.md
└── src/
    └── main/
        ├── java/
        │   └── com/obsidianbackup/
        │       ├── App.java                    # 진입점, JavaFX Application
        │       ├── config/
        │       │   ├── AppConfig.java          # 설정 데이터 모델
        │       │   └── ConfigManager.java      # JSON 읽기/쓰기
        │       ├── backup/
        │       │   ├── BackupService.java      # 핵심 백업 로직
        │       │   ├── BackupResult.java       # 백업 결과 모델
        │       │   └── BackupHistory.java      # 이력 관리
        │       ├── scheduler/
        │       │   └── BackupScheduler.java    # 매일 자동 실행
        │       └── ui/
        │           ├── TrayManager.java        # 시스템 트레이
        │           └── SettingsWindow.java     # JavaFX 설정창
        └── resources/
            ├── tray-icon.png
            └── fxml/
                └── settings.fxml
```

---

## 개발 환경 설정

### 1. JDK 설치

Java 17 이상이 필요합니다. (JavaFX 21은 Java 17+ 필요)

```
# 설치 확인
java -version
# 출력 예: openjdk version "17.0.x"
```

권장: [Eclipse Temurin 17](https://adoptium.net/) 또는 [Oracle JDK 17](https://www.oracle.com/java/technologies/downloads/)

### 2. 프로젝트 클론 및 IDE 열기

```bash
git clone https://github.com/yourname/obsidian-backup.git
cd obsidian-backup
```

**IntelliJ IDEA** (권장)
- `File → Open` → 프로젝트 폴더 선택
- Gradle import 자동 감지 → `Import Gradle Project` 클릭
- SDK: `File → Project Structure → SDK → Java 17` 설정

**Eclipse**
- `File → Import → Gradle → Existing Gradle Project`
- 프로젝트 루트 선택 후 Finish

### 3. Gradle 의존성 다운로드

```bash
./gradlew dependencies
```

---

## 빌드 방법

### 일반 빌드 (컴파일 + 테스트)

```bash
./gradlew build
```

### 실행 가능한 JAR 빌드

```bash
./gradlew shadowJar
# 결과: build/libs/obsidian-backup-all.jar
```

### JAR 직접 실행

```bash
java -jar build/libs/obsidian-backup-all.jar
```

---

## 구동 방법

### 개발 중 실행 (Gradle)

```bash
./gradlew run
```

### JAR 실행

```bash
java -jar obsidian-backup-all.jar
```

앱 시작 시 동작:
1. 시스템 트레이에 아이콘 등록
2. `config.json` 로드 (없으면 기본값으로 생성)
3. 스케줄러 시작 (설정된 시각에 매일 백업 예약)
4. 최초 실행 시 설정창 자동 팝업

### 설정 파일 위치

```
%APPDATA%\ObsidianBackup\
├── config.json       # 앱 설정
├── history.json      # 백업 이력
└── app.log           # 실행 로그
```

Windows 기준 실제 경로 예시:
```
C:\Users\{사용자명}\AppData\Roaming\ObsidianBackup\
```

---

## 설정 파일 스펙

`config.json` 구조:

```json
{
  "sourceFolder": "C:\\Users\\user\\Documents\\Obsidian\\MyVault",
  "backupFolder": "D:\\Backup\\Obsidian",
  "backupHour": 2,
  "backupMinute": 0,
  "retentionDays": 30,
  "autoStart": true,
  "incrementalBackup": true,
  "showNotification": false,
  "lastBackupTime": "2026-05-12T02:00:00"
}
```

| 필드 | 타입 | 설명 | 기본값 |
|------|------|------|--------|
| `sourceFolder` | String | Obsidian Vault 경로 | `""` (필수 입력) |
| `backupFolder` | String | 백업 저장 경로 | `""` (필수 입력) |
| `backupHour` | int | 백업 실행 시 (0~23) | `2` |
| `backupMinute` | int | 백업 실행 분 (0~59) | `0` |
| `retentionDays` | int | 백업 보관 일수 | `30` |
| `autoStart` | boolean | Windows 시작 시 자동 실행 | `true` |
| `incrementalBackup` | boolean | 증분 백업 사용 여부 | `true` |
| `showNotification` | boolean | 완료 알림 표시 여부 | `false` |
| `lastBackupTime` | String | 마지막 백업 시각 (ISO-8601) | `null` |

---

## 백업 이력 파일 스펙

`history.json` 구조:

```json
[
  {
    "id": "20260512-020000",
    "startTime": "2026-05-12T02:00:00",
    "endTime": "2026-05-12T02:00:04",
    "status": "SUCCESS",
    "copiedFiles": 12,
    "totalFiles": 1243,
    "totalSizeBytes": 228589200,
    "durationMs": 4200,
    "errorMessage": null
  },
  {
    "id": "20260510-020000",
    "startTime": "2026-05-10T02:00:00",
    "endTime": "2026-05-10T02:00:00",
    "status": "FAILED",
    "copiedFiles": 0,
    "totalFiles": 0,
    "totalSizeBytes": 0,
    "durationMs": 310,
    "errorMessage": "NoSuchFileException: C:\\Users\\user\\Documents\\Obsidian\\MyVault"
  }
]
```

`status` 가능 값: `SUCCESS` / `FAILED` / `IN_PROGRESS`

---

## 디버깅 방법

### 1. 로그 레벨 설정

`config.json`에 `"logLevel": "DEBUG"` 추가 시 상세 로그 출력:

```json
{
  "logLevel": "DEBUG"
}
```

로그 파일 위치: `%APPDATA%\ObsidianBackup\app.log`

### 2. IntelliJ 디버그 실행

1. `App.java` 열기
2. 중단점(Breakpoint) 설정
3. `Run → Debug 'App'` 또는 `Shift+F9`
4. Variables 패널에서 `AppConfig`, `BackupService` 상태 확인

### 3. 자주 발생하는 오류

| 오류 | 원인 | 해결 방법 |
|------|------|-----------|
| `NoSuchFileException` | 소스 폴더 경로 잘못됨 | 설정창에서 경로 재설정 |
| `AccessDeniedException` | 백업 폴더 쓰기 권한 없음 | 폴더 권한 확인 또는 경로 변경 |
| `SystemTray not supported` | 트레이 미지원 환경 | `java.awt.headless=false` 확인 |
| `JavaFX runtime not found` | JavaFX 모듈 누락 | `build.gradle` 의존성 확인 |
| `config.json` 파싱 오류 | JSON 형식 깨짐 | 파일 삭제 시 기본값으로 재생성 |

### 4. config.json 초기화

설정 파일이 깨진 경우 삭제하면 앱 재시작 시 기본값으로 자동 생성됩니다:

```bash
del "%APPDATA%\ObsidianBackup\config.json"
```

### 5. 로그 실시간 확인 (PowerShell)

```powershell
Get-Content "$env:APPDATA\ObsidianBackup\app.log" -Wait -Tail 50
```

---

## 배포 (.exe 빌드)

`jpackage`를 사용해 Windows 설치 파일을 생성합니다.

### 사전 조건
- JDK 17+ (jpackage 포함)
- WiX Toolset 설치 (`.msi` 생성 시 필요): https://wixtoolset.org/

### 빌드 명령

```bash
# 1. Shadow JAR 먼저 빌드
./gradlew shadowJar

# 2. jpackage로 exe 생성
jpackage \
  --input build/libs \
  --main-jar obsidian-backup-all.jar \
  --main-class com.obsidianbackup.App \
  --name "ObsidianBackup" \
  --app-version "1.0.0" \
  --icon src/main/resources/tray-icon.ico \
  --win-shortcut \
  --win-menu \
  --type exe \
  --dest dist/
```

결과물: `dist/ObsidianBackup-1.0.0.exe`

### Gradle 태스크로 자동화

```bash
./gradlew jpackage
# 결과: dist/ObsidianBackup-1.0.0.exe
```

---

## 개발 로드맵

| 단계 | 모듈 | 상태 |
|------|------|------|
| 1 | `AppConfig` + `ConfigManager` (설정 저장/불러오기) | ✅ 완료 |
| 2 | `BackupService` (파일 복사 핵심 로직) | ✅ 완료 |
| 3 | `SettingsWindow` (JavaFX UI) | ✅ 완료 |
| 4 | `TrayManager` (시스템 트레이) | ✅ 완료 |
| 5 | `BackupScheduler` (자동 스케줄) | ✅ 완료 |
| 6 | `jpackage` .exe 빌드 | ✅ 완료 |
