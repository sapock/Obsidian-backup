package com.obsidianbackup.util;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.*;

/**
 * 파일 기반 로깅을 설정하는 유틸리티.
 *
 * App.start() 최초에 한 번 호출하면
 * {appDataDir}/app.log 에 5 MB 롤링 로그를 기록한다.
 */
public class LoggingSetup {

    private static final String FORMAT =
        "%1$tF %1$tT [%4$-7s] %2$s: %5$s%6$s%n";

    private LoggingSetup() {}

    /**
     * 루트 로거에 FileHandler를 추가한다.
     *
     * @param appDataDir 로그 파일을 쓸 앱 데이터 디렉터리
     */
    public static void init(Path appDataDir) {
        System.setProperty("java.util.logging.SimpleFormatter.format", FORMAT);

        try {
            java.nio.file.Files.createDirectories(appDataDir);

            FileHandler fh = new FileHandler(
                appDataDir.resolve("app.log").toString(),
                5 * 1024 * 1024,  // 5 MB
                3,                 // 최대 3개 파일 순환
                true               // append
            );
            fh.setFormatter(new SimpleFormatter());
            fh.setLevel(Level.ALL);

            Logger root = Logger.getLogger("");
            root.addHandler(fh);
            root.setLevel(Level.INFO);

            // 콘솔 출력은 WARNING 이상만
            for (Handler h : root.getHandlers()) {
                if (h instanceof ConsoleHandler) {
                    h.setLevel(Level.WARNING);
                }
            }

        } catch (IOException e) {
            Logger.getLogger(LoggingSetup.class.getName())
                .warning("파일 로그 설정 실패: " + e.getMessage());
        }
    }

    /**
     * 앱 패키지 로거의 레벨을 설정한다.
     * config.logLevel="DEBUG" 이면 FINE, 그 외는 INFO.
     */
    public static void applyLogLevel(String logLevel) {
        Level level = "DEBUG".equalsIgnoreCase(logLevel) ? Level.FINE : Level.INFO;
        Logger.getLogger("com.obsidianbackup").setLevel(level);
    }
}
