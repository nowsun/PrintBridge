package com.magicyuan.print;

import java.util.Locale;

import javax.swing.*;

import com.magicyuan.print.config.AppSettings;
import com.magicyuan.print.config.ThemeManager;
import com.magicyuan.print.i18n.I18nManager;
import com.magicyuan.print.ui.PrintMainFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 打印应用程序主入口类
 * 负责应用程序的启动和初始化
 *
 * @author Magic_yuan
 * @version 2.0.0
 */
public class PrintApplication {

    private static final Logger logger = LoggerFactory.getLogger(PrintApplication.class);

    /**
     * 应用程序入口方法
     */
    public static void main(String[] args) {
        // 设置应用程序名称(用于系统通知显示)
        setupApplicationProperties();

        // 加载设置并应用语言与主题
        AppSettings settings = AppSettings.load();
        settings.applyLoggingLevel();
        applyLanguage(settings.getLanguage());
        setupLookAndFeel(settings.getTheme());

        // 使用事件调度线程启动UI
        SwingUtilities.invokeLater(() -> {
            try {
                logger.info("========================================");
                logger.info("正在启动 PrintBridge 打印服务...");
                logger.info("版本: {}", com.magicyuan.print.config.AppConfig.APP_VERSION);
                logger.info("========================================");
                PrintMainFrame mainFrame = new PrintMainFrame();
                mainFrame.startInSystemTray();
                logger.info("PrintBridge 启动成功");
            } catch (Exception e) {
                logger.error("启动PrintBridge时发生错误: {}", e.getMessage(), e);
                JOptionPane.showMessageDialog(null, "启动PrintBridge时发生错误: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }

    /**
     * 设置应用程序属性
     */
    private static void setupApplicationProperties() {
        // 设置应用程序名称(在某些系统上可能影响通知标题)
        System.setProperty("apple.awt.application.name", "PrintBridge");
        System.setProperty("com.apple.mrj.application.apple.menu.about.name", "PrintBridge");

        // Windows任务管理器中的显示名称
        System.setProperty("sun.java.command", "PrintBridge");

        logger.debug("应用程序属性设置完成");
    }

    /**
     * 根据配置应用界面语言
     */
    private static void applyLanguage(String language) {
        if ("en".equals(language)) {
            I18nManager.setLocale(Locale.US);
        } else {
            I18nManager.setLocale(Locale.SIMPLIFIED_CHINESE);
        }
    }

    /**
     * 设置应用程序外观
     */
    private static void setupLookAndFeel(String theme) {
        try {
            ThemeManager.apply(theme);

            // 设置系统属性以获得更好的外观
            System.setProperty("awt.useSystemAAFontSettings", "on");
            System.setProperty("swing.aatext", "true");

            logger.debug("FlatLaf外观主题设置成功");

        } catch (Exception e) {
            logger.warn("设置外观主题失败，使用默认主题: {}", e.getMessage(), e);
        }
    }
}
