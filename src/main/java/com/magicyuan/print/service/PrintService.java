package com.magicyuan.print.service;

import java.awt.print.Book;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.PrinterJob;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.magicyuan.print.config.AppSettings;
import com.magicyuan.print.converter.OfficeConverter;
import com.magicyuan.print.dto.PrintDTO;
import com.magicyuan.print.i18n.I18nManager;
import org.apache.commons.lang.StringUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.printing.PDFPrintable;
import org.apache.pdfbox.printing.Scaling;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.magicyuan.print.converter.ImageConverter;
import com.magicyuan.print.model.PrintQueueModel;

/**
 * 打印服务类
 * 处理所有打印相关的业务逻辑
 * 支持PDF、Office文档(Word/Excel/PPT)、图片等格式
 * 
 * @author Magic_yuan
 * @version 2.1.3
 */
public class PrintService {

    private static final Logger logger = LoggerFactory.getLogger(PrintService.class);

    private final PrintQueueModel printQueueModel;
    private final ExecutorService executorService;
    private final OfficeConverter officeConverter;
    private final ImageConverter imageConverter;

    private volatile String defaultPrinterName = "";
    private volatile double paperWidthMm = AppSettings.DEFAULT_PAPER_WIDTH_MM;
    private volatile double paperHeightMm = AppSettings.DEFAULT_PAPER_HEIGHT_MM;

    public PrintService(PrintQueueModel printQueueModel) {
        this.printQueueModel = printQueueModel;
        this.executorService = Executors.newCachedThreadPool();
        this.officeConverter = new OfficeConverter();
        this.imageConverter = new ImageConverter();
        
        // 输出格式支持信息
        logSupportedFormats();
    }
    
    /**
     * 输出支持的格式信息
     */
    private void logSupportedFormats() {
        logger.info("========== PrintBridge 格式支持 ==========");
        logger.info("✓ PDF格式: 完全支持");
        logger.info("✓ 图片格式: PNG, JPG, GIF, BMP");
        
        if (officeConverter.isAvailable()) {
            logger.info("✓ Office格式: Word, Excel, PowerPoint");
            logger.info("  已检测到Office/WPS/LibreOffice,多格式支持已启用");
        } else {
            logger.warn("⚠ Office格式: 不支持");
            logger.warn("  未检测到Microsoft Office、WPS或LibreOffice,仅支持PDF和图片格式");
            logger.warn("  安装以上任一软件以支持Word/Excel/PPT: https://www.libreoffice.org/");
        }
        logger.info("=========================================");
    }

    /**
     * 应用配置到打印引擎(默认打印机、默认纸张)。
     */
    public void applySettings(AppSettings settings) {
        this.defaultPrinterName = settings.getPrinterName() == null ? "" : settings.getPrinterName();
        this.paperWidthMm = settings.getPaperWidthMm() > 0 ? settings.getPaperWidthMm() : AppSettings.DEFAULT_PAPER_WIDTH_MM;
        this.paperHeightMm = settings.getPaperHeightMm() > 0 ? settings.getPaperHeightMm() : AppSettings.DEFAULT_PAPER_HEIGHT_MM;
        this.officeConverter.setConvertEngine(settings.getConvertEngine());
        logger.info("打印设置已应用: 打印机={}, 纸张={}x{}mm",
            defaultPrinterName.isEmpty() ? "系统默认" : defaultPrinterName, paperWidthMm, paperHeightMm);
    }

    /**
     * 按名称查找打印机。
     */
    private javax.print.PrintService findPrinterByName(String name) {
        javax.print.PrintService[] services = javax.print.PrintServiceLookup.lookupPrintServices(null, null);
        for (javax.print.PrintService service : services) {
            if (service.getName().equals(name)) {
                return service;
            }
        }
        return null;
    }

    /**
     * 异步打印文档（带对话框）
     */
    public CompletableFuture<Void> printWithDialogAsync(List<PrintDTO> printDTOList) {
        return CompletableFuture.runAsync(() -> {
            printWithDialog(printDTOList);
        }, executorService);
    }

    /**
     * 异步打印文档（不带对话框）
     */
    public CompletableFuture<Void> printWithoutDialogAsync(List<PrintDTO> printDTOList) {
        return CompletableFuture.runAsync(() -> {
            printWithoutDialog(printDTOList);
        }, executorService);
    }

    /**
     * Asynchronously print a PDF uploaded by a local browser client without a dialog.
     */
    public CompletableFuture<Void> printPdfBytesWithoutDialogAsync(
            byte[] pdfBytes, String fileName, boolean landscape) {
        final byte[] documentBytes = pdfBytes;
        final PrintDTO printDTO = new PrintDTO();
        printDTO.setFileName(fileName);
        printDTO.setLandscape(landscape);
        printDTO.setSource("web");

        return CompletableFuture.runAsync(() -> {
            try (PDDocument document = PDDocument.load(documentBytes)) {
                executePrint(document, printDTO, false);
                printQueueModel.addPrintTask(printDTO);
            } catch (Exception exception) {
                handlePrintError(printDTO, exception);
            }
        }, executorService);
    }

    /**
     * 提交一张测试校准页, 使用当前默认打印机与纸张设置。
     */
    public CompletableFuture<Void> testPrintPageAsync() {
        final PrintDTO printDTO = new PrintDTO();
        printDTO.setFileName(I18nManager.zhEn("测试打印", "Test print"));
        printDTO.setLandscape(false);
        printDTO.setSource("test");

        return CompletableFuture.runAsync(() -> {
            PDDocument document = null;
            try {
                document = createTestPage();
                executePrint(document, printDTO, false);
                printQueueModel.addPrintTask(printDTO);
            } catch (Exception e) {
                handlePrintError(printDTO, e);
                throw new RuntimeException(e);
            } finally {
                if (document != null) {
                    try {
                        document.close();
                    } catch (IOException ignored) {
                        // 忽略关闭异常
                    }
                }
            }
        }, executorService);
    }

    /**
     * 生成一张简单的测试页 PDF。
     */
    private PDDocument createTestPage() throws IOException {
        PDDocument document = new PDDocument();
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);

        PDPageContentStream content = new PDPageContentStream(document, page);
        try {
            content.beginText();
            content.setFont(PDType1Font.HELVETICA_BOLD, 22);
            content.newLineAtOffset(70, 760);
            content.showText("PrintBridge Test Page");
            content.endText();

            content.beginText();
            content.setFont(PDType1Font.HELVETICA, 12);
            content.newLineAtOffset(70, 730);
            content.showText(new java.util.Date().toString());
            content.endText();
        } finally {
            content.close();
        }
        return document;
    }

    /**
     * 打印文档（带对话框）
     */
    public void printWithDialog(List<PrintDTO> printDTOList) {
        for (PrintDTO printDTO : printDTOList) {
            try {
                if (printDocument(printDTO, true)) {
                    // 打印成功，可以添加到历史记录
                    printQueueModel.addPrintTask(printDTO);
                }
            } catch (Exception e) {
                handlePrintError(printDTO, e);
            }
        }
    }

    /**
     * 打印文档（不带对话框）
     */
    public void printWithoutDialog(List<PrintDTO> printDTOList) {
        for (PrintDTO printDTO : printDTOList) {
            try {
                if (printDocument(printDTO, false)) {
                    // 打印成功，添加到队列
                    printQueueModel.addPrintTask(printDTO);
                }
            } catch (Exception e) {
                handlePrintError(printDTO, e);
            }
        }
    }

    /**
     * 打印单个文档(支持多种格式)
     */
    private boolean printDocument(PrintDTO printDTO, boolean showDialog) throws Exception {
        String fileUrl = printDTO.getFileUrl();
        logger.info("正在打印: {}, 文件名: {}", fileUrl, printDTO.getFileName());

        // 判断文件类型并转换
        String fileExtension = getFileExtension(fileUrl);
        File pdfFile = null;
        boolean isTemporaryFile = false;

        try {
            // 根据文件类型处理
            if (isPdfFormat(fileExtension)) {
                // PDF格式,直接打印
                logger.info("检测到PDF格式,直接打印");
                return printPdfFromUrl(fileUrl, printDTO, showDialog);
                
            } else if (imageConverter.isSupportedFormat(fileExtension)) {
                // 图片格式,转换为PDF
                logger.info("检测到图片格式({}),转换为PDF后打印", fileExtension);
                pdfFile = imageConverter.convertFromUrl(fileUrl);
                isTemporaryFile = true;
                return printPdfFile(pdfFile, printDTO, showDialog);
                
            } else if (officeConverter.isSupportedFormat(fileExtension)) {
                // Office格式,转换为PDF
                if (!officeConverter.isAvailable()) {
                    throw new UnsupportedOperationException(
                        "不支持Office格式: 未检测到Microsoft Office、WPS或LibreOffice。\n" +
                        "请安装以上任一软件以支持Word/Excel/PPT格式: https://www.libreoffice.org/"
                    );
                }
                logger.info("检测到Office格式({}),转换为PDF后打印", fileExtension);
                pdfFile = officeConverter.convertFromUrl(fileUrl);
                isTemporaryFile = true;
                return printPdfFile(pdfFile, printDTO, showDialog);
                
            } else {
                throw new UnsupportedOperationException(
                    "不支持的文件格式: " + fileExtension + "\n" +
                    "支持的格式: PDF, PNG, JPG, GIF, BMP" +
                    (officeConverter.isAvailable() ? ", DOCX, XLSX, PPTX" : "")
                );
            }
            
        } finally {
            // 清理临时文件
            if (isTemporaryFile && pdfFile != null) {
                try {
                    Files.deleteIfExists(pdfFile.toPath());
                    logger.debug("临时文件已清理: {}", pdfFile.getName());
                } catch (Exception e) {
                    logger.warn("清理临时文件失败: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * 从URL打印PDF
     */
    private boolean printPdfFromUrl(String pdfUrl, PrintDTO printDTO, boolean showDialog) throws Exception {
        URL url = new URL(pdfUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        PDDocument document = PDDocument.load(connection.getInputStream());

        try {
            return executePrint(document, printDTO, showDialog);
        } finally {
            document.close();
        }
    }

    /**
     * 打印本地PDF文件
     */
    private boolean printPdfFile(File pdfFile, PrintDTO printDTO, boolean showDialog) throws Exception {
        // 先整体读入内存再打印:虚拟打印机(如 Microsoft Print to PDF)会弹"另存为"并阻塞,
        // 若以 File 方式加载,PDFBox 会一直占用该文件句柄,导致转换产物"被占用、打不开"。
        byte[] pdfBytes = Files.readAllBytes(pdfFile.toPath());
        PDDocument document = PDDocument.load(pdfBytes);

        try {
            return executePrint(document, printDTO, showDialog);
        } finally {
            document.close();
        }
    }

    /**
     * 执行打印操作
     */
    private boolean executePrint(PDDocument document, PrintDTO printDTO, boolean showDialog) throws Exception {
        PrinterJob job = PrinterJob.getPrinterJob();

        // 应用默认打印机
        if (StringUtils.isNotBlank(defaultPrinterName)) {
            javax.print.PrintService targetPrinter = findPrinterByName(defaultPrinterName);
            if (targetPrinter != null) {
                try {
                    job.setPrintService(targetPrinter);
                } catch (java.awt.print.PrinterException e) {
                    logger.warn("设置打印机失败, 使用系统默认打印机: {}", e.getMessage());
                }
            } else {
                logger.warn("未找到配置的打印机: {}, 使用系统默认打印机", defaultPrinterName);
            }
        }

        // 记录实际使用的打印机(未配置时回退系统默认)
        javax.print.PrintService usedService = job.getPrintService();
        if (usedService == null) {
            usedService = javax.print.PrintServiceLookup.lookupDefaultPrintService();
        }
        printDTO.setPrinterName(usedService != null ? usedService.getName() : "");

        // 设置页面格式(纸张尺寸)
        PageFormat pageFormat = new PageFormat();
        Paper paper = new Paper();
        double paperWidthPt = paperWidthMm * 72.0 / 25.4;
        double paperHeightPt = paperHeightMm * 72.0 / 25.4;
        paper.setSize(paperWidthPt, paperHeightPt);
        paper.setImageableArea(0, 0, paperWidthPt, paperHeightPt);
        pageFormat.setPaper(paper);
        if (printDTO.isLandscape()) {
            pageFormat.setOrientation(PageFormat.LANDSCAPE);
        }

        // 设置作业名称
        if (StringUtils.isNotBlank(printDTO.getFileName())) {
            job.setJobName(printDTO.getFileName());
        }

        // 创建打印书籍
        Book book = new Book();
        book.append(new PDFPrintable(document, Scaling.SCALE_TO_FIT), pageFormat, document.getNumberOfPages());
        job.setPageable(book);

        // 根据参数决定是否显示打印对话框
        if (showDialog) {
            boolean shouldPrint = job.printDialog();
            if (shouldPrint) {
                job.print();
                return true;
            }
            return false;
        } else {
            job.print();
            return true;
        }
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String fileUrl) {
        String path = fileUrl.toLowerCase();
        
        // 移除查询参数
        int queryIndex = path.indexOf('?');
        if (queryIndex > 0) {
            path = path.substring(0, queryIndex);
        }
        
        int lastDot = path.lastIndexOf('.');
        if (lastDot > 0 && lastDot < path.length() - 1) {
            return path.substring(lastDot + 1);
        }
        
        return "";
    }

    /**
     * 判断是否为PDF格式
     */
    private boolean isPdfFormat(String extension) {
        return "pdf".equalsIgnoreCase(extension);
    }

    /**
     * 处理打印错误
     */
    private void handlePrintError(PrintDTO printDTO, Exception e) {
        logger.error("打印失败: {} - {}", printDTO.getFileName(), e.getMessage(), e);
        printQueueModel.addErrorTask(printDTO);
    }

    /**
     * 验证打印任务
     */
    public boolean validatePrintTasks(List<PrintDTO> printDTOList) {
        if (printDTOList == null || printDTOList.isEmpty()) {
            return false;
        }

        for (PrintDTO printDTO : printDTOList) {
            if (StringUtils.isBlank(printDTO.getFileUrl())) {
                return false;
            }
        }

        return true;
    }

    /**
     * 获取可用的打印机列表
     */
    public String[] getAvailablePrinters() {
        javax.print.PrintService[] printServices = javax.print.PrintServiceLookup.lookupPrintServices(null, null);

        String[] printerNames = new String[printServices.length];
        for (int i = 0; i < printServices.length; i++) {
            printerNames[i] = printServices[i].getName();
        }

        return printerNames;
    }

    /**
     * 关闭服务
     */
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
        
        // 关闭Office转换器
        if (officeConverter != null) {
            officeConverter.shutdown();
        }
    }
    
    /**
     * 获取Office转换器状态
     */
    public boolean isOfficeConverterAvailable() {
        return officeConverter != null && officeConverter.isAvailable();
    }

    /**
     * Word格式是否可打印
     */
    public boolean isWordAvailable() {
        return officeConverter != null && officeConverter.isWordAvailable();
    }

    /**
     * Excel格式是否可打印
     */
    public boolean isExcelAvailable() {
        return officeConverter != null && officeConverter.isExcelAvailable();
    }

    /**
     * PowerPoint格式是否可打印
     */
    public boolean isPptAvailable() {
        return officeConverter != null && officeConverter.isPptAvailable();
    }

    /**
     * 是否检测到Microsoft Office
     */
    public boolean isMsOfficeAvailable() {
        return officeConverter != null && officeConverter.isMsOfficeAvailable();
    }

    /**
     * 是否检测到WPS Office
     */
    public boolean isWpsAvailable() {
        return officeConverter != null && officeConverter.isWpsAvailable();
    }

    /**
     * 是否检测到LibreOffice
     */
    public boolean isLibreOfficeAvailable() {
        return officeConverter != null && officeConverter.isLibreOfficeAvailable();
    }
}