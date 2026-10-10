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
 * 通过向用户启动文件夹写入/删除启动脚本实现 Windows 开机自启。
 *
 * @author Magic_yuan
 * @version 2.1.4
 */
public class AutoStartManager {

    private static final Logger logger = LoggerFactory.getLogger(AutoStartManager.class);

    private static final String SCRIPT_NAME = "PrintBridge.cmd";

    private AutoStartManager() {
    }

    /**
     * 开启或取消开机自启。
     */
    public static void setEnabled(boolean enabled) {
        try {
            Path startupDir = startupDir();
            Path script = startupDir.resolve(SCRIPT_NAME);
            if (enabled) {
                Files.createDirectories(startupDir);
                Files.write(script, buildScript().getBytes(StandardCharsets.UTF_8));
                logger.info("已加入开机自启: {}", script);
            } else {
                Files.deleteIfExists(script);
                logger.info("已取消开机自启");
            }
        } catch (Exception e) {
            logger.error("设置开机自启失败: {}", e.getMessage(), e);
        }
    }

    private static Path startupDir() {
        String appData = System.getenv("APPDATA");
        if (appData == null) {
            appData = System.getProperty("user.home");
        }
        return Paths.get(appData, "Microsoft", "Windows", "Start Menu", "Programs", "Startup");
    }

    private static String buildScript() throws Exception {
        File jar = jarFile();
        File javaw = new File(System.getProperty("java.home"), "bin" + File.separator + "javaw.exe");
        String dir = jar.getParentFile().getAbsolutePath();
        return "@echo off\r\n"
            + "cd /d \"" + dir + "\"\r\n"
            + "start \"\" \"" + javaw.getAbsolutePath() + "\" -jar \"" + jar.getAbsolutePath() + "\"\r\n";
    }

    private static File jarFile() throws Exception {
        return new File(AutoStartManager.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    }
}
