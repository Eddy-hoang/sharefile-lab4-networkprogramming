package org.example;

import org.example.gui.MainGUI;
import org.example.server.ServerMain;

import javax.swing.*;

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