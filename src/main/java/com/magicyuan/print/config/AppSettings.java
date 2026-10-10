package com.magicyuan.print.config;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 应用设置模型
 * 负责加载、保存和管理可配置项(端口、默认打印机、默认纸张、Origin白名单)
 *
 * @author Magic_yuan
 * @version 2.1.4
 */
public class AppSettings {

    private static final Logger logger = LoggerFactory.getLogger(AppSettings.class);

    /** 配置文件路径(相对工作目录) */
    public static final String CONFIG_FILE = "printbridge.ini";

    public static final int DEFAULT_PORT = 8281;
    public static final double DEFAULT_PAPER_WIDTH_MM = 210.0;
    public static final double DEFAULT_PAPER_HEIGHT_MM = 297.0;

    /** 默认允许的网页来源,保持与原硬编码一致 */
    private static final List<String> DEFAULT_ORIGINS = Arrays.asList(
        "https://siksoft.com",
        "http://localhost:8080",
        "http://127.0.0.1:8080"
    );

    private int port = DEFAULT_PORT;
    private String printerName = "";
    private double paperWidthMm = DEFAULT_PAPER_WIDTH_MM;
    private double paperHeightMm = DEFAULT_PAPER_HEIGHT_MM;
    private List<String> allowedOrigins = new ArrayList<String>(DEFAULT_ORIGINS);
    private boolean autostart = false;
    private String theme = "system";
    private String language = "zh-CN";

    /**
     * 从配置文件加载设置,文件不存在或读取失败时返回默认配置。
     */
    public static AppSettings load() {
        File file = new File(CONFIG_FILE);
        if (!file.exists()) {
            logger.info("未找到配置文件 {}, 使用默认配置", CONFIG_FILE);
            return new AppSettings();
        }
        try {
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            AppSettings settings = JSON.parseObject(content, AppSettings.class);
            if (settings == null) {
                logger.warn("配置文件内容为空, 使用默认配置");
                return new AppSettings();
            }
            settings.normalize();
            logger.info("配置已加载: {}", CONFIG_FILE);
            return settings;
        } catch (Exception e) {
            logger.warn("读取配置文件失败, 使用默认配置: {}", e.getMessage());
            return new AppSettings();
        }
    }

    /**
     * 保存设置到配置文件。
     */
    public void save() {
        try {
            String json = JSON.toJSONString(this, true);
            Files.write(Paths.get(CONFIG_FILE), json.getBytes(StandardCharsets.UTF_8));
            logger.info("配置已保存到 {}", CONFIG_FILE);
        } catch (IOException e) {
            logger.error("保存配置失败: {}", e.getMessage(), e);
        }
    }

    private void normalize() {
        if (port <= 0 || port > 65535) {
            port = DEFAULT_PORT;
        }
        if (printerName == null) {
            printerName = "";
        }
        if (paperWidthMm <= 0) {
            paperWidthMm = DEFAULT_PAPER_WIDTH_MM;
        }
        if (paperHeightMm <= 0) {
            paperHeightMm = DEFAULT_PAPER_HEIGHT_MM;
        }
        if (allowedOrigins == null) {
            allowedOrigins = new ArrayList<String>(DEFAULT_ORIGINS);
        }
        if (theme == null || !("light".equals(theme) || "dark".equals(theme) || "system".equals(theme))) {
            theme = "system";
        }
        if (language == null || !("zh-CN".equals(language) || "en".equals(language))) {
            language = "zh-CN";
        }
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getPrinterName() {
        return printerName;
    }

    public void setPrinterName(String printerName) {
        this.printerName = printerName;
    }

    public double getPaperWidthMm() {
        return paperWidthMm;
    }

    public void setPaperWidthMm(double paperWidthMm) {
        this.paperWidthMm = paperWidthMm;
    }

    public double getPaperHeightMm() {
        return paperHeightMm;
    }

    public void setPaperHeightMm(double paperHeightMm) {
        this.paperHeightMm = paperHeightMm;
    }

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public boolean isAutostart() {
        return autostart;
    }

    public void setAutostart(boolean autostart) {
        this.autostart = autostart;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }
}
