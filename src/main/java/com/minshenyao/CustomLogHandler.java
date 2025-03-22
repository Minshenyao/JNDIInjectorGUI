package com.minshenyao;

import javax.swing.*;
import java.time.LocalDateTime;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

/**
 * 自定义日志处理器，将日志输出到GUI的文本区域
 */
public class CustomLogHandler extends Handler {
    private final JTextArea logArea;
    private final SimpleFormatter formatter = new SimpleFormatter();

    public CustomLogHandler(JTextArea logArea) {
        this.logArea = logArea;
    }

    @Override
    public void publish(LogRecord record) {
        // 确保在EDT线程中更新GUI
        SwingUtilities.invokeLater(() -> {
            String formattedMsg = "[" + LocalDateTime.now() + "] " +
                    formatter.formatMessage(record) + "\n";
            logArea.append(formattedMsg);
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() throws SecurityException {
    }
}