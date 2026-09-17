package org.example;

import org.example.gui.MainGUI;
import org.example.server.ServerMain;

import javax.swing.*;

/**
 * ============================================================================
 * PACKAGE: org.example
 * FILE: Main.java
 * ============================================================================
 * CHỨC NĂNG:
 * Điểm khởi chạy chính (Entry Point) của toàn bộ ứng dụng.
 * Cung cấp hộp thoại chọn chạy Máy chủ Trung tâm (ServerMain) hoặc Nút Peer Client (MainGUI).
 * 
 * CÁCH HOẠT ĐỘNG:
 * 1. Đặt Look & Feel giao diện hệ thống cho Swing.
 * 2. Hiển thị hộp thoại JOptionPane cho người dùng lựa chọn:
 *    - Lựa chọn 0: Mở Central Directory Server (ServerMain).
 *    - Lựa chọn 1: Mở Nút Peer Client (MainGUI).
 */
public class Main {
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        String[] options = {"Chạy Server Trung Tâm (Directory Server)", "Chạy Nút Peer Client"};
        int choice = JOptionPane.showOptionDialog(
                null,
                "Chọn thành phần bạn muốn khởi chạy trong ứng dụng P2P Chat & File Transfer:",
                "P2P Application Launcher",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[1]
        );

        if (choice == 0) {
            SwingUtilities.invokeLater(() -> new ServerMain().setVisible(true));
        } else if (choice == 1) {
            SwingUtilities.invokeLater(() -> new MainGUI().setVisible(true));
        }
    }
}