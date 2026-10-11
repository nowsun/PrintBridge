package com.magicyuan.print.dto;

public class PrintDTO {

    private String fileUrl;

    private String fileName;

    private boolean landscape = false;

    /** 任务进入队列的时间戳(毫秒) */
    private long createTime;

    /** 实际使用的打印机名称 */
    private String printerName;

    /** 任务来源: web(网页) / test(测试) */
    private String source;

    /** 来源网址(网页URL或上传来源页面Origin), 用于任务列表展示 */
    private String sourceUrl;

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public boolean isLandscape() {
        return landscape;
    }

    public void setLandscape(boolean landscape) {
        this.landscape = landscape;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public String getPrinterName() {
        return printerName;
    }

    public void setPrinterName(String printerName) {
        this.printerName = printerName;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    @Override
    public String toString() {
        return fileName;
    }
}
