package com.magicyuan.print.converter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.jodconverter.core.DocumentConverter;
import org.jodconverter.core.office.OfficeException;
import org.jodconverter.core.office.OfficeManager;
import org.jodconverter.local.LocalConverter;
import org.jodconverter.local.office.LocalOfficeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Office文档转换器
 * 优先使用本机已安装的 Microsoft Office / WPS(通过COM自动化) 转PDF,
 * 未安装时回退到 JODConverter + LibreOffice。
 *
 * @author Magic_yuan
 * @version 2.1.3
 */
public class OfficeConverter {

    private static final Logger logger = LoggerFactory.getLogger(OfficeConverter.class);
    private static final long NATIVE_TIMEOUT_SECONDS = 120L;

    // LibreOffice后端
    private OfficeManager officeManager;
    private DocumentConverter libreOfficeConverter;
    private boolean libreOfficeAvailable = false;

    // 原生Office/WPS后端
    private boolean wordAvailable = false;   // MS Word / WPS 文字
    private boolean excelAvailable = false;  // MS Excel / WPS 表格
    private boolean pptAvailable = false;    // MS PowerPoint / WPS 演示
    private boolean msOfficeAvailable = false; // 检测到 Microsoft Office(任一组件)
    private boolean wpsAvailable = false;      // 检测到 WPS Office(任一组件)
    private Path nativeScript;

    /**
     * 构造函数,自动检测原生Office/WPS与LibreOffice
     */
    public OfficeConverter() {
        detectNativeOffice();
        detectLibreOffice();
        extractNativeScript();
    }

    /**
     * 检测本机是否安装了Microsoft Office或WPS
     */
    private void detectNativeOffice() {
        // 逐格式可用性: MS或WPS任一ProgID注册即可。
        // 注意: WPS会占用 Word.Application/Excel.Application/PowerPoint.Application 等MS ProgID,
        // 但无论指向谁都能通过COM转PDF,所以这里只判断"能否打印",不判断厂商。
        wordAvailable = isProgIdRegistered("Word.Application") || isProgIdRegistered("KWPS.Application");
        excelAvailable = isProgIdRegistered("Excel.Application") || isProgIdRegistered("KET.Application");
        pptAvailable = isProgIdRegistered("PowerPoint.Application") || isProgIdRegistered("KWPP.Application");

        // 微软Office判定: 只有真正的MS Office会注册 WINWORD/EXCEL/POWERPNT 的 App Paths
        msOfficeAvailable = isMsAppRegistered("WINWORD.EXE")
            || isMsAppRegistered("EXCEL.EXE")
            || isMsAppRegistered("POWERPNT.EXE");

        // WPS判定: WPS独有ProgID
        wpsAvailable = isProgIdRegistered("KWPS.Application")
            || isProgIdRegistered("KET.Application")
            || isProgIdRegistered("KWPP.Application");

        logger.info("原生Office检测: Word={}, Excel={}, PowerPoint={} (Microsoft Office={}, WPS={})",
            wordAvailable, excelAvailable, pptAvailable, msOfficeAvailable, wpsAvailable);
    }

    /**
     * 检测微软Office可执行文件的 App Paths 是否注册(覆盖32/64位视图)
     */
    private boolean isMsAppRegistered(String exeName) {
        return isRegistryKeyPresent("HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\App Paths\\" + exeName)
            || isRegistryKeyPresent("HKLM\\SOFTWARE\\WOW6432Node\\Microsoft\\Windows\\CurrentVersion\\App Paths\\" + exeName);
    }

    /**
     * 检测某COM ProgID是否已注册(通过注册表)
     */
    private boolean isProgIdRegistered(String progId) {
        return isRegistryKeyPresent("HKCR\\" + progId);
    }

    /**
     * 检测注册表键是否存在(通过reg query)
     */
    private boolean isRegistryKeyPresent(String key) {
        try {
            Process process = new ProcessBuilder("reg", "query", key)
                    .redirectErrorStream(true)
                    .start();
            // 排空输出,避免阻塞
            try (InputStream in = process.getInputStream()) {
                byte[] buf = new byte[1024];
                while (in.read(buf) != -1) {
                    // ignore
                }
            }
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 提取打包进jar的原生转换PowerShell脚本到临时目录
     */
    private void extractNativeScript() {
        try (InputStream in = OfficeConverter.class.getResourceAsStream("/scripts/native-office-convert.ps1")) {
            if (in == null) {
                logger.warn("未找到原生转换脚本,禁用原生Office转换");
                wordAvailable = excelAvailable = pptAvailable = false;
                return;
            }
            Path tmp = Files.createTempFile("printbridge_native_", ".ps1");
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            nativeScript = tmp;
            logger.debug("原生转换脚本已提取: {}", nativeScript);
        } catch (Exception e) {
            logger.warn("提取原生转换脚本失败,禁用原生Office转换: {}", e.getMessage());
            wordAvailable = excelAvailable = pptAvailable = false;
        }
    }

    /**
     * 检测并初始化LibreOffice后端
     */
    private void detectLibreOffice() {
        try {
            String libreOfficePath = detectLibreOfficePath();
            if (libreOfficePath != null) {
                initializeLibreOffice(libreOfficePath);
            } else {
                logger.warn("未检测到LibreOffice安装,将仅依赖原生Office/WPS");
            }
        } catch (Exception e) {
            logger.error("初始化LibreOffice转换器失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 初始化LibreOffice转换器
     */
    private void initializeLibreOffice(String libreOfficePath) throws OfficeException {
        logger.info("初始化Office转换器,LibreOffice路径: {}", libreOfficePath);

        officeManager = LocalOfficeManager.builder()
            .officeHome(libreOfficePath)
            .taskExecutionTimeout(120_000L)
            .maxTasksPerProcess(50)
            .build();

        officeManager.start();
        libreOfficeConverter = LocalConverter.make(officeManager);
        libreOfficeAvailable = true;
        logger.info("LibreOffice转换器初始化成功");
    }

    /**
     * 检测LibreOffice安装路径
     */
    private String detectLibreOfficePath() {
        String os = System.getProperty("os.name").toLowerCase();
        String[] paths;

        if (os.contains("win")) {
            paths = new String[] {
                "C:\\Program Files\\LibreOffice",
                "C:\\Program Files (x86)\\LibreOffice",
                System.getenv("ProgramFiles") + "\\LibreOffice",
                System.getenv("ProgramFiles(x86)") + "\\LibreOffice"
            };
        } else if (os.contains("mac")) {
            paths = new String[] {
                "/Applications/LibreOffice.app/Contents"
            };
        } else {
            paths = new String[] {
                "/usr/lib/libreoffice",
                "/usr/lib64/libreoffice",
                "/opt/libreoffice",
                "/snap/libreoffice/current"
            };
        }

        for (String path : paths) {
            if (path != null && new File(path).exists()) {
                logger.info("检测到LibreOffice: {}", path);
                return path;
            }
        }

        return null;
    }

    /**
     * 从URL下载并转换Office文档为PDF
     */
    public File convertFromUrl(String fileUrl) throws Exception {
        if (!isAvailable()) {
            throw new IllegalStateException("Office转换器不可用,请安装Microsoft Office、WPS或LibreOffice");
        }

        logger.info("开始转换Office文档: {}", fileUrl);

        Path tempInput = Files.createTempFile("printbridge_input_", getExtensionFromUrl(fileUrl));

        try {
            URL url = new URL(fileUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);

            try (InputStream in = connection.getInputStream()) {
                Files.copy(in, tempInput, StandardCopyOption.REPLACE_EXISTING);
            }

            logger.info("文件下载完成: {}", tempInput.getFileName());

            Path tempOutput = Files.createTempFile("printbridge_output_", ".pdf");
            convertToPdf(tempInput.toFile(), tempOutput.toFile());

            logger.info("转换完成: {}", tempOutput.getFileName());
            return tempOutput.toFile();

        } finally {
            try {
                Files.deleteIfExists(tempInput);
            } catch (Exception e) {
                logger.warn("清理临时文件失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 转换本地文件
     */
    public File convert(File inputFile) throws Exception {
        String ext = extensionOf(inputFile.getName());
        if (!isSupportedFormat(ext)) {
            throw new IllegalStateException("不支持的Office格式: " + ext);
        }

        Path outputPath = Files.createTempFile("printbridge_output_", ".pdf");
        File outputFile = outputPath.toFile();

        logger.info("转换文档: {} -> {}", inputFile.getName(), outputFile.getName());

        convertToPdf(inputFile, outputFile);

        if (!outputFile.exists() || outputFile.length() == 0) {
            throw new IOException("转换未生成有效的PDF文件");
        }

        logger.info("转换完成: {}", outputFile.getAbsolutePath());
        return outputFile;
    }

    /**
     * 根据文件扩展名选择原生Office/WPS或LibreOffice进行转换
     */
    private void convertToPdf(File input, File output) throws Exception {
        String ext = extensionOf(input.getName());

        if (shouldUseNative(ext)) {
            // 原生转换: 先删除空的输出文件,由Office/WPS自己创建
            Files.deleteIfExists(output.toPath());
            convertNative(input, output, kindOf(ext));
        } else {
            if (!libreOfficeAvailable) {
                throw new IllegalStateException("没有可用的Office转换引擎");
            }
            libreOfficeConverter.convert(input).to(output).execute();
        }
    }

    /**
     * 通过PowerShell调用COM自动化,用原生Office/WPS转PDF
     */
    private void convertNative(File input, File output, String kind) throws Exception {
        if (nativeScript == null) {
            throw new IOException("原生转换脚本不可用");
        }

        List<String> cmd = new ArrayList<>();
        cmd.add("powershell.exe");
        cmd.add("-NoProfile");
        cmd.add("-ExecutionPolicy");
        cmd.add("Bypass");
        cmd.add("-WindowStyle");
        cmd.add("Hidden");
        cmd.add("-File");
        cmd.add(nativeScript.toString());
        cmd.add("-Source");
        cmd.add(input.getAbsolutePath());
        cmd.add("-Target");
        cmd.add(output.getAbsolutePath());
        cmd.add("-Kind");
        cmd.add(kind);

        logger.info("使用原生Office/WPS转换: {} ({})", input.getName(), kind);

        Path logFile = Files.createTempFile("printbridge_native_log_", ".txt");
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        pb.redirectOutput(logFile.toFile());

        Process process = pb.start();
        boolean finished = process.waitFor(NATIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("Office转换超时(" + NATIVE_TIMEOUT_SECONDS + "秒),请检查Office/WPS是否卡在弹窗");
        }

        int exitCode = process.exitValue();
        String log = new String(Files.readAllBytes(logFile), StandardCharsets.UTF_8);
        Files.deleteIfExists(logFile);

        if (exitCode != 0) {
            throw new IOException("Office转换失败(exit=" + exitCode + "): " + log.trim());
        }
        if (!output.exists() || output.length() == 0) {
            throw new IOException("Office转换未生成PDF: " + log.trim());
        }
    }

    /**
     * 判断指定扩展名是否应使用原生Office/WPS
     */
    private boolean shouldUseNative(String ext) {
        String kind = kindOf(ext);
        if (kind == null) {
            return false;
        }
        switch (kind) {
            case "word": return wordAvailable;
            case "excel": return excelAvailable;
            case "ppt": return pptAvailable;
            default: return false;
        }
    }

    /**
     * 扩展名 -> 引擎类别
     */
    private String kindOf(String ext) {
        switch (ext) {
            case "doc": case "docx": case "rtf": case "txt": return "word";
            case "xls": case "xlsx": case "csv": return "excel";
            case "ppt": case "pptx": return "ppt";
            default: return null;
        }
    }

    /**
     * 检查是否可用(任一引擎可用)
     */
    public boolean isAvailable() {
        return libreOfficeAvailable || wordAvailable || excelAvailable || pptAvailable;
    }

    /**
     * Word格式是否可打印(原生Office/WPS或LibreOffice)
     */
    public boolean isWordAvailable() {
        return wordAvailable || libreOfficeAvailable;
    }

    /**
     * Excel格式是否可打印(原生Office/WPS或LibreOffice)
     */
    public boolean isExcelAvailable() {
        return excelAvailable || libreOfficeAvailable;
    }

    /**
     * PowerPoint格式是否可打印(原生Office/WPS或LibreOffice)
     */
    public boolean isPptAvailable() {
        return pptAvailable || libreOfficeAvailable;
    }

    /**
     * 是否检测到Microsoft Office(任一组件)
     */
    public boolean isMsOfficeAvailable() {
        return msOfficeAvailable;
    }

    /**
     * 是否检测到WPS Office(任一组件)
     */
    public boolean isWpsAvailable() {
        return wpsAvailable;
    }

    /**
     * 是否检测到LibreOffice
     */
    public boolean isLibreOfficeAvailable() {
        return libreOfficeAvailable;
    }

    /**
     * 检查是否支持该格式
     */
    public boolean isSupportedFormat(String fileExtension) {
        String ext = normalizeExt(fileExtension);

        switch (ext) {
            case "doc": case "docx": case "rtf": case "txt":
                return wordAvailable || libreOfficeAvailable;
            case "xls": case "xlsx": case "csv":
                return excelAvailable || libreOfficeAvailable;
            case "ppt": case "pptx":
                return pptAvailable || libreOfficeAvailable;
            case "odt": case "ods": case "odp":
                return libreOfficeAvailable;
            default:
                return false;
        }
    }

    /**
     * 关闭转换器
     */
    public void shutdown() {
        if (officeManager != null && officeManager.isRunning()) {
            try {
                logger.info("关闭LibreOffice转换器...");
                officeManager.stop();
                logger.info("LibreOffice转换器已关闭");
            } catch (OfficeException e) {
                logger.error("关闭LibreOffice转换器失败: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * 获取文件扩展名(小写、无点)
     */
    private String extensionOf(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < fileName.length() - 1) {
            return fileName.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }

    private String normalizeExt(String fileExtension) {
        return fileExtension.toLowerCase().replace(".", "");
    }

    /**
     * 从URL获取文件扩展名
     */
    private String getExtensionFromUrl(String url) {
        String path = url.substring(url.lastIndexOf('/') + 1);

        int queryIndex = path.indexOf('?');
        if (queryIndex > 0) {
            path = path.substring(0, queryIndex);
        }

        int lastDot = path.lastIndexOf('.');
        return (lastDot > 0) ? path.substring(lastDot) : ".tmp";
    }
}
