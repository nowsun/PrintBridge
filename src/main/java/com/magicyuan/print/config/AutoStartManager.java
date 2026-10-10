package com.magicyuan.print.config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 开机自启管理
 * Windows 写入启动文件夹(.cmd), Linux 写入 XDG autostart(.desktop),
 * macOS 写入 launchd LaunchAgent(.plist)。
 *
 * @author Magic_yuan
 * @version 2.1.4
 */
public class AutoStartManager {

    private static final Logger logger = LoggerFactory.getLogger(AutoStartManager.class);

    private AutoStartManager() {
    }

    /**
     * 开启或取消开机自启。
     */
    public static void setEnabled(boolean enabled) {
        try {
            Path file = startupFile();
            if (enabled) {
                Files.createDirectories(file.getParent());
                Files.write(file, buildScript().getBytes(StandardCharsets.UTF_8));
                logger.info("已加入开机自启: {}", file);
            } else {
                Files.deleteIfExists(file);
                logger.info("已取消开机自启");
            }
        } catch (Exception e) {
            logger.error("设置开机自启失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 各平台的自启文件路径。
     */
    private static Path startupFile() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home");
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData == null) {
                appData = home;
            }
            return Paths.get(appData, "Microsoft", "Windows", "Start Menu", "Programs", "Startup", "PrintBridge.cmd");
        } else if (os.contains("mac")) {
            return Paths.get(home, "Library", "LaunchAgents", "com.genscript.printbridge.plist");
        }
        return Paths.get(home, ".config", "autostart", "PrintBridge.desktop");
    }

    /**
     * 根据平台生成对应的自启脚本内容。
     */
    private static String buildScript() throws Exception {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return buildWindowsScript();
        } else if (os.contains("mac")) {
            return buildMacScript();
        }
        return buildLinuxScript();
    }

    private static String buildWindowsScript() throws Exception {
        File jar = jarFile();
        File javaw = new File(System.getProperty("java.home"), "bin" + File.separator + "javaw.exe");
        String dir = jar.getParentFile().getAbsolutePath();
        return "@echo off\r\n"
            + "cd /d \"" + dir + "\"\r\n"
            + "start \"\" \"" + javaw.getAbsolutePath() + "\" -jar \"" + jar.getAbsolutePath() + "\"\r\n";
    }

    private static String buildLinuxScript() throws Exception {
        File jar = jarFile();
        String java = new File(System.getProperty("java.home"), "bin" + File.separator + "java").getAbsolutePath();
        return "[Desktop Entry]\n"
            + "Type=Application\n"
            + "Name=PrintBridge\n"
            + "Comment=PrintBridge print service\n"
            + "Exec=\"" + java + "\" -Dfile.encoding=UTF-8 -jar \"" + jar.getAbsolutePath() + "\"\n"
            + "Terminal=false\n"
            + "X-GNOME-Autostart-enabled=true\n";
    }

    private static String buildMacScript() throws Exception {
        File jar = jarFile();
        String java = new File(System.getProperty("java.home"), "bin" + File.separator + "java").getAbsolutePath();
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n"
            + "<plist version=\"1.0\">\n"
            + "<dict>\n"
            + "    <key>Label</key>\n"
            + "    <string>com.genscript.printbridge</string>\n"
            + "    <key>ProgramArguments</key>\n"
            + "    <array>\n"
            + "        <string>" + xmlEscape(java) + "</string>\n"
            + "        <string>-Dfile.encoding=UTF-8</string>\n"
            + "        <string>-jar</string>\n"
            + "        <string>" + xmlEscape(jar.getAbsolutePath()) + "</string>\n"
            + "    </array>\n"
            + "    <key>RunAtLoad</key>\n"
            + "    <true/>\n"
            + "</dict>\n"
            + "</plist>\n";
    }

    /** XML 转义(launchd plist 中路径可能含 & 等字符) */
    private static String xmlEscape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static File jarFile() throws Exception {
        return new File(AutoStartManager.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    }
}
