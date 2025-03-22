package com.minshenyao;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());
    private static JTextArea logArea;
    private static final ExecutorService executor = Executors.newFixedThreadPool(3);
    private static final String DEFAULT_CODEBASE_URL = "http://localhost:8000/#";
    private static final int DEFAULT_RMI_PORT = 1099;
    private static final int DEFAULT_LDAP_PORT = 1389;
    private static final int DEFAULT_HTTP_PORT = 8000;
    private static final String DEFAULT_HTTP_DIR = System.getProperty("user.dir");

    // 服务状态跟踪器
    private static boolean rmiServiceRunning = false;
    private static boolean ldapServiceRunning = false;
    private static boolean httpServiceRunning = false;

    // UI 组件
    private static JButton startRmiButton;
    private static JButton stopRmiButton;
    private static JButton startLdapButton;
    private static JButton stopLdapButton;
    private static JButton startHttpButton;
    private static JButton stopHttpButton;
    private static JButton chooseDirectoryButton;
    private static JTextField codebaseField;
    private static JTextField rmiPortField;
    private static JTextField ldapPortField;
    private static JTextField httpPortField;
    private static JTextField httpDirField;

    public static void main(String[] args) {
        System.setProperty("java.awt.screenScale", "1");
        SwingUtilities.invokeLater(Main::createAndShowGUI);
    }

    private static void createAndShowGUI() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "设置系统外观失败", e);
        }

        JFrame frame = new JFrame("RMI、LDAP 和 HTTP 服务工具  —— By Minshenyao");
        frame.setSize(600, 650);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridx = 0;
        gbc.weightx = 1.0;

        // 配置面板
        gbc.gridy = 0;
        gbc.weighty = 0;
        JPanel configPanel = createConfigPanel();
        mainPanel.add(configPanel, gbc);

        // 控制面板
        gbc.gridy = 1;
        gbc.weighty = 0;
        JPanel controlPanel = createControlPanel();
        mainPanel.add(controlPanel, gbc);

        // 日志区域
        gbc.gridy = 2;
        gbc.weighty = 1.0;
        logArea = new JTextArea(10, 30);
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(logArea);
        mainPanel.add(scrollPane, gbc);

        frame.add(mainPanel);
        frame.setVisible(true);
        frame.setLocationRelativeTo(null);

        updateButtonStates();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            stopServices();
            executor.shutdown();
        }));

        configureLogging();
    }

    /**
     * 配置日志系统，将所有服务的日志重定向到GUI
     */
    private static void configureLogging() {
        // 获取根日志记录器
        Logger rootLogger = Logger.getLogger("");

        // 移除所有现有的处理器（包括默认的控制台处理器）
        Handler[] handlers = rootLogger.getHandlers();
        for (Handler handler : handlers) {
            rootLogger.removeHandler(handler);
        }

        // 创建自定义日志处理器并添加到根日志记录器
        Handler logHandler = new CustomLogHandler(logArea);
        rootLogger.addHandler(logHandler);

        // 记录初始消息
        log("应用已启动，等待操作...");
    }

    private static JPanel createConfigPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("配置"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 恶意类 URL
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("恶意类 URL:"), gbc);

        codebaseField = new JTextField(DEFAULT_CODEBASE_URL, 25);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.gridwidth = 1;
        panel.add(codebaseField, gbc);

        // RMI 端口
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.0;
        gbc.gridwidth = 1;
        panel.add(new JLabel("RMI 端口:"), gbc);

        rmiPortField = new JTextField(String.valueOf(DEFAULT_RMI_PORT), 5);
        gbc.gridx = 1;
        panel.add(rmiPortField, gbc);

        // LDAP 端口
        gbc.gridx = 0;
        gbc.gridy = 2;
        panel.add(new JLabel("LDAP 端口:"), gbc);

        ldapPortField = new JTextField(String.valueOf(DEFAULT_LDAP_PORT), 5);
        gbc.gridx = 1;
        panel.add(ldapPortField, gbc);

        // HTTP 端口
        gbc.gridx = 0;
        gbc.gridy = 3;
        panel.add(new JLabel("HTTP 端口:"), gbc);

        httpPortField = new JTextField(String.valueOf(DEFAULT_HTTP_PORT), 5);
        gbc.gridx = 1;
        panel.add(httpPortField, gbc);

        // HTTP 文件目录
        gbc.gridx = 0;
        gbc.gridy = 4;
        panel.add(new JLabel("HTTP 目录:"), gbc);

        httpDirField = new JTextField(DEFAULT_HTTP_DIR, 25);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        panel.add(httpDirField, gbc);

        // 选择目录按钮
        chooseDirectoryButton = new JButton("浏览...");
        chooseDirectoryButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
            fileChooser.setCurrentDirectory(new File(httpDirField.getText()));
            int result = fileChooser.showOpenDialog(panel);
            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                httpDirField.setText(selectedFile.getAbsolutePath());
            }
        });
        gbc.gridx = 2;
        gbc.weightx = 0.0;
        panel.add(chooseDirectoryButton, gbc);

        return panel;
    }

    private static JPanel createControlPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("服务控制"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        // RMI 服务控制
        JPanel rmiPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        rmiPanel.setBorder(BorderFactory.createTitledBorder("RMI 服务"));
        startRmiButton = createStyledButton("启动", e -> startRmiService());
        stopRmiButton = createStyledButton("停止", e -> stopRmiService());
        rmiPanel.add(startRmiButton);
        rmiPanel.add(stopRmiButton);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        panel.add(rmiPanel, gbc);

        // LDAP 服务控制
        JPanel ldapPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        ldapPanel.setBorder(BorderFactory.createTitledBorder("LDAP 服务"));
        startLdapButton = createStyledButton("启动", e -> startLdapService());
        stopLdapButton = createStyledButton("停止", e -> stopLdapService());
        ldapPanel.add(startLdapButton);
        ldapPanel.add(stopLdapButton);

        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(ldapPanel, gbc);

        // HTTP 服务控制
        JPanel httpPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        httpPanel.setBorder(BorderFactory.createTitledBorder("HTTP 文件服务"));
        startHttpButton = createStyledButton("启动", e -> startHttpService());
        stopHttpButton = createStyledButton("停止", e -> stopHttpService());
        httpPanel.add(startHttpButton);
        httpPanel.add(stopHttpButton);

        gbc.gridx = 0;
        gbc.gridy = 2;
        panel.add(httpPanel, gbc);

        return panel;
    }

    private static JButton createStyledButton(String text, java.awt.event.ActionListener listener) {
        JButton button = new JButton(text);
//        button.setFont(new Font("Helvetica Neue", Font.PLAIN, 14));
        button.setFont(UIManager.getFont("Button.font"));
        button.setBackground(new Color(144, 202, 249)); // 更淡的蓝色 (#90CAF9)
        button.setForeground(new Color(51, 51, 51)); // 深灰色 (#333333)
        button.setFocusPainted(false);
        button.addActionListener(listener);

        return button;
    }

    private static void updateButtonStates() {
        startRmiButton.setEnabled(!rmiServiceRunning);
        stopRmiButton.setEnabled(rmiServiceRunning);
        startLdapButton.setEnabled(!ldapServiceRunning);
        stopLdapButton.setEnabled(ldapServiceRunning);
        startHttpButton.setEnabled(!httpServiceRunning);
        stopHttpButton.setEnabled(httpServiceRunning);
    }

    private static void startRmiService() {
        try {
            String codebaseUrl = codebaseField.getText().trim();
            int port = Integer.parseInt(rmiPortField.getText().trim());

            executor.submit(() -> {
                try {
                    URL url = new URL(codebaseUrl);
                    RmiService.startRmiService(url, port);
                    SwingUtilities.invokeLater(() -> {
                        rmiServiceRunning = true;
                        updateButtonStates();
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        ex.printStackTrace();
                    });
                }
            });
        } catch (NumberFormatException e) {
            log("错误: 端口必须是一个数字");
        }
    }

    private static void stopRmiService() {
        executor.submit(() -> {
            try {
                RmiService.stopRmiService();
                SwingUtilities.invokeLater(() -> {
                    rmiServiceRunning = false;
                    updateButtonStates();
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                });
            }
        });
    }

    private static void startLdapService() {
        try {
            String codebaseUrl = codebaseField.getText().trim();
            int port = Integer.parseInt(ldapPortField.getText().trim());

            executor.submit(() -> {
                try {
                    LdapService.startLdapService(codebaseUrl, port);
                    SwingUtilities.invokeLater(() -> {
                        ldapServiceRunning = true;
                        updateButtonStates();
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        ex.printStackTrace();
                    });
                }
            });
        } catch (NumberFormatException e) {
            log("错误: 端口必须是一个数字");
        }
    }

    private static void stopLdapService() {
        executor.submit(() -> {
            try {
                LdapService.stopLdapService();
                SwingUtilities.invokeLater(() -> {
                    ldapServiceRunning = false;
                    updateButtonStates();
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                });
            }
        });
    }

    private static void startHttpService() {
        try {
            String directory = httpDirField.getText().trim();
            int port = Integer.parseInt(httpPortField.getText().trim());

            executor.submit(() -> {
                try {
                    HttpFileServer.startHttpServer(directory, port);
                    SwingUtilities.invokeLater(() -> {
                        httpServiceRunning = true;
                        updateButtonStates();
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                    });
                }
            });
        } catch (NumberFormatException e) {
            log("错误: 端口必须是一个数字");
        }
    }

    private static void stopHttpService() {
        executor.submit(() -> {
            try {
                HttpFileServer.stopHttpServer();
                SwingUtilities.invokeLater(() -> {
                    httpServiceRunning = false;
                    updateButtonStates();
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                });
            }
        });
    }

    private static void stopServices() {
        if (rmiServiceRunning) {
            RmiService.stopRmiService();
        }
        if (ldapServiceRunning) {
            LdapService.stopLdapService();
        }
        if (httpServiceRunning) {
            HttpFileServer.stopHttpServer();
        }
    }

    private static void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + java.time.LocalDateTime.now().toString() + "] " + message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
}