package com.magicyuan.print.config;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Timer;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 界面主题管理
 * 支持浅色 / 深色 / 跟随系统。
 * 跟随系统时轮询系统深色状态, 系统主题变化后自动刷新界面。
 *
 * @author Magic_yuan
 * @version 2.1.4
 */
public class ThemeManager {

    private static final Logger logger = LoggerFactory.getLogger(ThemeManager.class);

    /** 系统深色状态轮询间隔(毫秒) */
    private static final int SYSTEM_POLL_MS = 1500;

    private static String activeTheme = "light";
    private static boolean watcherActive = false;
    private static Boolean lastSystemDark = null;
    private static Timer systemWatcher = null;

    private static final List<Runnable> systemThemeListeners = new ArrayList<Runnable>();

    private ThemeManager() {
    }

    /**
     * 应用主题。
     *
     * @param theme light / dark / system
     */
    public static void apply(String theme) {
        activeTheme = theme;
        applyResolved(resolveDark(theme));
        configureSystemWatcher();
    }

    /**
     * 注册系统深色状态变化监听(仅在跟随系统时触发)。回调在 EDT 执行。
     */
    public static void addSystemThemeListener(Runnable listener) {
        if (listener != null) {
            systemThemeListeners.add(listener);
        }
    }

    private static boolean resolveDark(String theme) {
        if ("dark".equals(theme)) {
            return true;
        }
        if ("light".equals(theme)) {
            return false;
        }
        return isSystemDark();
    }

    private static void applyResolved(boolean dark) {
        try {
            if (dark) {
                FlatDarkLaf.setup();
            } else {
                FlatLightLaf.setup();
            }
            FlatLaf.updateUI();
            logger.info("已应用{}主题", dark ? "深色" : "浅色");
        } catch (Exception e) {
            logger.warn("应用主题失败: {}", e.getMessage());
        }
    }

    /** 仅在跟随系统时启动轮询; 切换到浅色/深色时停止。 */
    private static void configureSystemWatcher() {
        boolean needWatcher = "system".equals(activeTheme);
        if (needWatcher == watcherActive) {
            if (needWatcher) {
                lastSystemDark = isSystemDark();
            }
            return;
        }
        if (needWatcher) {
            lastSystemDark = isSystemDark();
            systemWatcher = new Timer(SYSTEM_POLL_MS, e -> {
                boolean nowDark = isSystemDark();
                if (!Boolean.valueOf(nowDark).equals(lastSystemDark)) {
                    lastSystemDark = nowDark;
                    applyResolved(nowDark);
                    notifySystemThemeListeners();
                }
            });
            systemWatcher.start();
        } else {
            if (systemWatcher != null) {
                systemWatcher.stop();
            }
            systemWatcher = null;
        }
        watcherActive = needWatcher;
    }

    private static void notifySystemThemeListeners() {
        for (Runnable listener : new ArrayList<Runnable>(systemThemeListeners)) {
            try {
                listener.run();
            } catch (Exception e) {
                logger.warn("系统主题监听回调失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 检测系统是否使用深色主题(Windows / macOS / Linux)。
     */
    private static boolean isSystemDark() {
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("win")) {
                return isWindowsDark();
            } else if (os.contains("mac")) {
                return isMacDark();
            } else {
                return isLinuxDark();
            }
        } catch (Exception e) {
            logger.debug("检测系统主题失败, 默认浅色: {}", e.getMessage());
        }
        return false;
    }

    /** Windows: 注册表 AppsUseLightTheme */
    private static boolean isWindowsDark() throws Exception {
        Process process = new ProcessBuilder("reg", "query",
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
            "/v", "AppsUseLightTheme").redirectErrorStream(true).start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("AppsUseLightTheme")) {
                    return line.trim().endsWith("0x0");
                }
            }
        }
        process.waitFor();
        return false;
    }

    /** macOS: defaults read -g AppleInterfaceStyle 返回 "Dark" 表示深色 */
    private static boolean isMacDark() {
        String out = runAndCapture("defaults", "read", "-g", "AppleInterfaceStyle");
        return out != null && out.trim().equalsIgnoreCase("Dark");
    }

    /** Linux(GNOME): gsettings color-scheme 含 dark; 兼容旧版 gtk-application-prefer-dark-theme */
    private static boolean isLinuxDark() {
        String scheme = runAndCapture("gsettings", "get", "org.gnome.desktop.interface", "color-scheme");
        if (scheme != null && scheme.toLowerCase().contains("dark")) {
            return true;
        }
        String legacy = runAndCapture("gsettings", "get", "org.gnome.desktop.interface", "gtk-application-prefer-dark-theme");
        return legacy != null && legacy.trim().equalsIgnoreCase("true");
    }

    /** 执行命令并捕获合并后的输出; 失败返回 null */
    private static String runAndCapture(String... cmd) {
        try {
            Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (sb.length() > 0) {
                        sb.append('\n');
                    }
                    sb.append(line);
                }
            }
            process.waitFor();
            return sb.toString();
        } catch (Exception e) {
            logger.debug("命令执行失败 {}: {}", cmd[0], e.getMessage());
            return null;
        }
    }
}
