package com.obsidianbackup.util;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * 빌드 시 아이콘 PNG를 자동 생성하는 유틸리티.
 *
 * Gradle의 generateIcon 태스크가 이 클래스를 실행한다:
 *   gradle generateIcon
 *
 * 출력:
 *   src/main/resources/tray-icon.png   (16×16, 시스템 트레이용)
 *   src/main/resources/app-icon.png    (256×256, jpackage용)
 */
public class IconGenerator {

    public static void main(String[] args) throws IOException {
        String resourceDir = args.length > 0
            ? args[0] : "src/main/resources";

        generate(new File(resourceDir, "tray-icon.png"), 16);
        generate(new File(resourceDir, "app-icon.png"),  256);

        System.out.println("Icons generated in: " + resourceDir);
    }

    /**
     * 파란 원형 배경에 'O'를 그린 아이콘 PNG를 생성한다.
     */
    public static void generate(File dest, int size) throws IOException {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,    RenderingHints.VALUE_RENDER_QUALITY);

        // 배경: 파란 원
        g.setColor(new Color(58, 124, 200));
        g.fillOval(0, 0, size, size);

        // 테두리: 진한 파란 링
        g.setColor(new Color(30, 80, 150));
        g.setStroke(new BasicStroke(Math.max(1, size / 16f)));
        g.drawOval(1, 1, size - 2, size - 2);

        // 문자: 흰색 'O' (Obsidian)
        int fontSize = (int) (size * 0.55);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, fontSize));
        FontMetrics fm = g.getFontMetrics();
        String ch = "O";
        int tx = (size - fm.stringWidth(ch)) / 2;
        int ty = (size - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString(ch, tx, ty);

        g.dispose();

        dest.getParentFile().mkdirs();
        ImageIO.write(img, "PNG", dest);
        System.out.println("  " + dest.getName() + " (" + size + "x" + size + ")");
    }
}
