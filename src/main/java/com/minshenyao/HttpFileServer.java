package com.minshenyao;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpFileServer {
    private static final Logger LOGGER = Logger.getLogger(HttpFileServer.class.getName());
    private static HttpServer server;
    private static final AtomicBoolean running = new AtomicBoolean(false);
    private static File rootDirectory;

    /**
     * 启动 HTTP 文件服务器
     * @param directory 要提供的文件目录
     * @param port HTTP 服务端口
     */
    public static synchronized void startHttpServer(String directory, int port) {
        if (running.compareAndSet(false, true)) {
            try {
                rootDirectory = new File(directory);
                if (!rootDirectory.isDirectory()) {
                    rootDirectory = rootDirectory.getParentFile();
                }
                if (!rootDirectory.exists() || !rootDirectory.isDirectory()) {
                    throw new IOException("目录不存在或不是有效目录: " + directory);
                }

                server = HttpServer.create(new InetSocketAddress(port), 0);
                server.createContext("/", new FileHandler(rootDirectory));
                server.setExecutor(null); // 使用默认执行器
                server.start();

                LOGGER.info("HTTP 文件服务器已启动，监听在端口: " + port);
                LOGGER.info("提供目录: " + rootDirectory.getAbsolutePath());
            } catch (Exception e) {
                running.set(false);
                LOGGER.log(Level.SEVERE, "启动 HTTP 服务器失败", e);
                throw new RuntimeException("启动 HTTP 服务器失败", e);
            }
        } else {
            LOGGER.warning("HTTP 服务器已在运行");
        }
    }

    /**
     * 停止 HTTP 文件服务器
     */
    public static synchronized void stopHttpServer() {
        if (running.compareAndSet(true, false)) {
            if (server != null) {
                server.stop(0);
                server = null;
                LOGGER.info("HTTP 文件服务器已停止");
            }
        }
    }

    /**
     * 检查服务器是否正在运行
     */
    public static boolean isRunning() {
        return running.get();
    }

    /**
     * 处理文件服务请求的处理器
     */
    static class FileHandler implements HttpHandler {
        private final File rootDir;

        public FileHandler(File rootDir) {
            this.rootDir = rootDir;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestPath = exchange.getRequestURI().getPath();
            // 去掉开头的 / 字符
            if (requestPath.startsWith("/")) {
                requestPath = requestPath.substring(1);
            }

            // 构建文件路径
            Path filePath = Paths.get(rootDir.getAbsolutePath(), requestPath);
            File file = filePath.toFile();

            // 检查路径周期
            if (!filePath.toAbsolutePath().startsWith(rootDir.toPath().toAbsolutePath())) {
                LOGGER.warning("尝试访问目录外的文件: " + filePath);
                exchange.sendResponseHeaders(403, -1); // Forbidden
                exchange.close();
                return;
            }

            if (file.exists()) {
                if (file.isDirectory()) {
                    // 处理目录列表
                    sendDirectoryListing(exchange, file, requestPath);
                } else {
                    // 发送文件内容
                    sendFile(exchange, file);
                }
            } else {
                // 文件不存在
                exchange.sendResponseHeaders(404, -1); // Not Found
                exchange.close();
            }
        }

        /**
         * 发送文件内容
         */
        private void sendFile(HttpExchange exchange, File file) throws IOException {
            // 获取文件 MIME 类型
            String contentType = Files.probeContentType(file.toPath());
            if (contentType == null) {
                // 为 .class 文件指定 MIME 类型
                if (file.getName().endsWith(".class")) {
                    contentType = "application/java-vm";
                } else {
                    contentType = "application/octet-stream";
                }
            }

            // 设置响应头
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, file.length());

            // 发送文件内容
            try (
                    OutputStream os = exchange.getResponseBody();
                    FileInputStream fis = new FileInputStream(file)
            ) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, count);
                }
                os.flush();
            }

            LOGGER.info("服务文件: " + file.getAbsolutePath());
            exchange.close();
        }

        /**
         * 发送目录列表
         */
        private void sendDirectoryListing(HttpExchange exchange, File directory, String path) throws IOException {
            File[] files = directory.listFiles();

            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html>\n")
                    .append("<html>\n")
                    .append("<head>\n")
                    .append("    <title>目录列表: ").append(path).append("</title>\n")
                    .append("    <style>\n")
                    .append("        body { font-family: Arial, sans-serif; margin: 20px; }\n")
                    .append("        h1 { color: #333; }\n")
                    .append("        ul { list-style-type: none; padding: 0; }\n")
                    .append("        li { margin: 5px 0; }\n")
                    .append("        a { text-decoration: none; color: #0366d6; }\n")
                    .append("        a:hover { text-decoration: underline; }\n")
                    .append("    </style>\n")
                    .append("</head>\n")
                    .append("<body>\n")
                    .append("    <h1>目录: ").append(path.isEmpty() ? "/" : path).append("</h1>\n")
                    .append("    <ul>\n");

            // 如果不是根目录，添加返回上级目录的链接
            if (!path.isEmpty()) {
                html.append("        <li><a href=\"../\">../</a> (上级目录)</li>\n");
            }

            // 列出所有文件和目录
            if (files != null) {
                for (File file : files) {
                    String fileName = file.getName();
                    String displayName = fileName + (file.isDirectory() ? "/" : "");
                    String link = path.isEmpty() ? fileName : path + "/" + fileName;
                    if (file.isDirectory() && !link.endsWith("/")) {
                        link += "/";
                    }
                    html.append("        <li><a href=\"/").append(link).append("\">")
                            .append(displayName).append("</a></li>\n");
                }
            }

            html.append("    </ul>\n")
                    .append("</body>\n")
                    .append("</html>");

            byte[] bytes = html.toString().getBytes();
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }

            exchange.close();
        }
    }
}
