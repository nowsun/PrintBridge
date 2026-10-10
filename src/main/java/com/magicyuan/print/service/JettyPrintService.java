package com.magicyuan.print.service;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.magicyuan.print.config.AppSettings;
import com.magicyuan.print.dto.PrintDTO;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONObject;

/**
 * 基于Jetty的HTTP打印服务实现
 * 提供企业级的HTTP服务能力，支持高并发和完善的错误处理
 */
public class JettyPrintService {

    private static final Logger logger = LoggerFactory.getLogger(JettyPrintService.class);

    private static final int DEFAULT_PORT = 8281;

    private static final int MIN_THREADS = 10;

    private static final int MAX_THREADS = 100;

    private static final int MAX_PDF_BYTES = 50 * 1024 * 1024;

    private Server server;

    private boolean isRunning = false;
    
    private PrintService printService;
    private volatile Set<String> allowedOrigins;

    /**
     * 构造函数
     * @param printService 打印服务实例
     */
    public JettyPrintService(PrintService printService) {
        this.printService = printService;
        this.allowedOrigins = new HashSet<String>(AppSettings.load().getAllowedOrigins());
    }

    /**
     * 启动Jetty服务
     * @param port 监听端口
     * @return 是否启动成功
     */
    public boolean start(int port) {
        try {
            if (isRunning) {
                logger.warn("Jetty服务已经在运行中");
                return true;
            }

            // 配置线程池
            QueuedThreadPool threadPool = new QueuedThreadPool(MAX_THREADS, MIN_THREADS);
            threadPool.setName("jetty-print-service");

            // 创建服务器
            server = new Server(threadPool);

            // 创建HTTP连接器
            org.eclipse.jetty.server.ServerConnector connector = new org.eclipse.jetty.server.ServerConnector(server);
            connector.setPort(port);
            connector.setHost("127.0.0.1");

            server.addConnector(connector);

            // 创建上下文处理器集合
            ContextHandlerCollection contexts = new ContextHandlerCollection();

            // 打印服务上下文
            ContextHandler printContext = new ContextHandler("/print");
            printContext.setHandler(new PrintHandler());

            // 健康检查上下文
            ContextHandler healthContext = new ContextHandler("/health");
            healthContext.setHandler(new HealthHandler());

            // 根路径处理（OPTIONS预检请求）
            ContextHandler rootContext = new ContextHandler("/");
            rootContext.setHandler(new CorsHandler());

            contexts.setHandlers(new org.eclipse.jetty.server.Handler[] { printContext, healthContext, rootContext });

            server.setHandler(contexts);

            // 启动服务器
            server.start();
            isRunning = true;

            logger.info("PrintBridge服务已启动，监听端口: " + port);
            logger.info("允许的网页来源: {}", allowedOrigins);
            logger.info("服务端点:");
            logger.info("  - POST /print       : URL打印服务");
            logger.info("  - POST /print/pdf   : PDF上传并静默打印");
            logger.info("  - GET  /health : 健康检查");

            return true;

        } catch (Exception e) {
            logger.error("启动PrintBridge服务失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 启动服务（使用默认端口）
     */
    public boolean start() {
        return start(DEFAULT_PORT);
    }

    /**
     * 停止服务
     */
    public void stop() {
        if (server != null && isRunning) {
            try {
                server.stop();
                server.join();
                isRunning = false;
                logger.info("Jetty打印服务已停止");
            } catch (Exception e) {
                logger.error("停止Jetty服务时发生错误: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * 检查服务是否运行中
     */
    public boolean isRunning() {
        return isRunning && server != null && server.isRunning();
    }

    /**
     * 设置CORS头
     */
    private void setCorsHeaders(HttpServletRequest request, HttpServletResponse response) {
        String origin = request.getHeader("Origin");
        if (isAllowedOrigin(origin)) {
            response.setHeader("Access-Control-Allow-Origin", origin);
        }
        response.setHeader("Vary", "Origin");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
        response.setHeader("Access-Control-Max-Age", "3600");
        if ("true".equalsIgnoreCase(request.getHeader("Access-Control-Request-Private-Network"))) {
            response.setHeader("Access-Control-Allow-Private-Network", "true");
        }
    }

    private boolean isAllowedOrigin(String origin) {
        if (origin == null) {
            return false;
        }
        for (String allowed : allowedOrigins) {
            if (matchesOrigin(allowed, origin)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断请求 Origin 是否命中白名单。
     * 支持子域名: 白名单 https://siksoft.com 同时覆盖 https://*.siksoft.com(要求 scheme 与端口一致)。
     */
    private boolean matchesOrigin(String allowed, String origin) {
        if (allowed.equals(origin)) {
            return true;
        }
        try {
            java.net.URI a = java.net.URI.create(allowed);
            java.net.URI o = java.net.URI.create(origin);
            String allowedHost = a.getHost();
            String originHost = o.getHost();
            if (allowedHost == null || originHost == null) {
                return false;
            }
            if (!java.util.Objects.equals(a.getScheme(), o.getScheme())) {
                return false;
            }
            if (effectivePort(a) != effectivePort(o)) {
                return false;
            }
            // 子域名: originHost 以 "." + allowedHost 结尾(避免 "siksoft.com.evil.com" 误命中)
            return originHost.length() > allowedHost.length()
                && originHost.endsWith("." + allowedHost);
        } catch (Exception e) {
            return false;
        }
    }

    private int effectivePort(java.net.URI uri) {
        int port = uri.getPort();
        if (port >= 0) {
            return port;
        }
        if ("https".equalsIgnoreCase(uri.getScheme())) {
            return 443;
        }
        if ("http".equalsIgnoreCase(uri.getScheme())) {
            return 80;
        }
        return -1;
    }

    /**
     * 运行时更新允许的网页来源(白名单热生效)。
     */
    public void setAllowedOrigins(Collection<String> origins) {
        Set<String> set = new HashSet<String>(origins);
        this.allowedOrigins = Collections.unmodifiableSet(set);
        logger.info("已更新允许的网页来源: {}", allowedOrigins);
    }

    /**
     * 发送成功响应
     */
    private void sendSuccessResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json; charset=UTF-8");

        String jsonResponse = String.format("{\"success\":true,\"message\":\"%s\",\"timestamp\":%d}", message, System.currentTimeMillis());

        PrintWriter writer = response.getWriter();
        writer.write(jsonResponse);
        writer.flush();
    }

    /**
     * 发送错误响应
     */
    private void sendErrorResponse(HttpServletResponse response, int statusCode, String errorMessage) throws IOException {
        response.setStatus(statusCode);
        response.setContentType("application/json; charset=UTF-8");

        String errorResponse = String.format("{\"success\":false,\"error\":\"%s\",\"timestamp\":%d}", errorMessage, System.currentTimeMillis());

        PrintWriter writer = response.getWriter();
        writer.write(errorResponse);
        writer.flush();
    }

    /**
     * 打印请求处理器
     */
    private class PrintHandler extends AbstractHandler {

        @Override
        public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {

            // 设置CORS头
            setCorsHeaders(request, response);

            String method = request.getMethod();

            try {
                if ("OPTIONS".equals(method)) {
                    // 处理CORS预检请求
                    if (!isAllowedOrigin(request.getHeader("Origin"))) {
                        sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, "Origin not allowed");
                    } else {
                        handleOptionsRequest(response);
                    }
                } else if ("POST".equals(method)) {
                    if (!isAllowedOrigin(request.getHeader("Origin"))) {
                        sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, "Origin not allowed");
                    } else if (request.getRequestURI().endsWith("/print/pdf")) {
                        handlePdfUploadRequest(request, response);
                    } else {
                        handlePrintRequest(request, response);
                    }
                } else {
                    // 不支持的方法
                    sendErrorResponse(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Method Not Allowed: " + method);
                }
            } catch (Exception e) {
                logger.error("处理打印请求时发生错误: {}", e.getMessage(), e);
                sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error: " + e.getMessage());
            } finally {
                baseRequest.setHandled(true);
            }
        }

        private void handleOptionsRequest(HttpServletResponse response) throws IOException {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("text/plain; charset=UTF-8");
            response.getWriter().write("OK");
        }

        private void handlePdfUploadRequest(HttpServletRequest request, HttpServletResponse response)
                throws IOException {
            String contentType = request.getContentType();
            if (contentType == null
                    || !contentType.toLowerCase(Locale.ROOT).startsWith("application/pdf")) {
                sendErrorResponse(response, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE,
                        "Content-Type must be application/pdf");
                return;
            }

            int contentLength = request.getContentLength();
            if (contentLength > MAX_PDF_BYTES) {
                sendErrorResponse(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                        "PDF exceeds the 50 MB limit");
                return;
            }

            String fileName = request.getParameter("fileName");
            if (fileName == null || fileName.trim().isEmpty()) {
                fileName = "report.pdf";
            }
            fileName = fileName.replaceAll("[\\r\\n\\p{Cntrl}]", "");
            if (fileName.length() > 200) {
                fileName = fileName.substring(0, 200);
            }

            boolean landscape = Boolean.parseBoolean(request.getParameter("landscape"));
            byte[] pdfBytes;
            try (InputStream inputStream = request.getInputStream()) {
                pdfBytes = readPdfBytes(inputStream);
            } catch (IllegalArgumentException exception) {
                sendErrorResponse(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                        exception.getMessage());
                return;
            }
            if (pdfBytes.length == 0) {
                sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "PDF body is empty");
                return;
            }
            if (pdfBytes.length < 5
                    || !"%PDF-".equals(new String(pdfBytes, 0, 5, StandardCharsets.US_ASCII))) {
                sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid PDF data");
                return;
            }

            printService.printPdfBytesWithoutDialogAsync(pdfBytes, fileName, landscape);
            sendSuccessResponse(response, "PDF print job submitted");
        }

        private byte[] readPdfBytes(InputStream inputStream) throws IOException {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                total += read;
                if (total > MAX_PDF_BYTES) {
                    throw new IllegalArgumentException("PDF exceeds the 50 MB limit");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }

        private void handlePrintRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {

            // 读取请求体
            String requestBody = readRequestBody(request);

            if (requestBody == null || requestBody.trim().isEmpty()) {
                sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Request body is empty");
                return;
            }

            try {
                // 解析JSON数据
                List<PrintDTO> printList = JSONObject.parseArray(requestBody, PrintDTO.class);

                if (printList == null || printList.isEmpty()) {
                    sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid print data");
                    return;
                }

                for (PrintDTO dto : printList) {
                    dto.setSource("web");
                }

                logger.info("收到打印请求，文件数量: {}", printList.size());
                for (PrintDTO dto : printList) {
                    logger.info("  - 文件: {}, URL: {}", dto.getFileName(), dto.getFileUrl());
                }

                // 异步执行打印任务
                CompletableFuture.runAsync(() -> {
                    try {
                        printService.printWithoutDialog(printList);
                        logger.info("打印任务执行完成");
                    } catch (Exception e) {
                        logger.error("执行打印任务时发生错误: {}", e.getMessage(), e);
                    }
                });

                // 返回成功响应
                sendSuccessResponse(response, "Print job submitted successfully");

            } catch (Exception e) {
                logger.warn("解析打印数据失败: {}", e.getMessage(), e);
                sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid JSON format: " + e.getMessage());
            }
        }

        private String readRequestBody(HttpServletRequest request) throws IOException {
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = request.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }
            }
            return body.toString();
        }
    }

    /**
     * 健康检查处理器
     */
    private class HealthHandler extends AbstractHandler {

        @Override
        public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {

            setCorsHeaders(request, response);

            if ("GET".equals(request.getMethod())) {
                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentType("application/json; charset=UTF-8");

                String healthResponse = String.format("{\"status\":\"OK\",\"service\":\"Jetty Print Service\",\"timestamp\":%d,\"version\":\"1.0\"}", System.currentTimeMillis());

                response.getWriter().write(healthResponse);
            } else {
                sendErrorResponse(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Method Not Allowed");
            }

            baseRequest.setHandled(true);
        }
    }

    /**
     * CORS处理器（处理根路径的OPTIONS请求）
     */
    private class CorsHandler extends AbstractHandler {

        @Override
        public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {

            setCorsHeaders(request, response);

            if ("OPTIONS".equals(request.getMethod())) {
                if (isAllowedOrigin(request.getHeader("Origin"))) {
                    response.setStatus(HttpServletResponse.SC_OK);
                    response.setContentType("text/plain; charset=UTF-8");
                    response.getWriter().write("OK");
                } else {
                    sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, "Origin not allowed");
                }
                baseRequest.setHandled(true);
            }
            // 对于非OPTIONS请求，不设置handled，让其他处理器处理
        }
    }
}