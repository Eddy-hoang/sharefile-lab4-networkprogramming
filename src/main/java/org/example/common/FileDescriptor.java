package org.example.common;

import java.io.Serializable;
import java.util.Objects;

public class FileDescriptor implements Serializable {
    private static final long serialVersionUID = 1L;

    private String fileName;       // Tên tệp tin (VD: document.pdf, music.mp3)
    private long fileSize;         // Kích thước tệp tin tính bằng byte
    private String ownerUsername;  // Tên người dùng sở hữu tệp tin này
    private String ownerIp;        // Địa chỉ IP của nút Peer giữ tệp tin
    private int ownerP2pPort;      // Cổng P2P của nút Peer đang lắng nghe kết nối truyền file

    public FileDescriptor() {
    }

    public FileDescriptor(String fileName, long fileSize, String ownerUsername, String ownerIp, int ownerP2pPort) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.ownerUsername = ownerUsername;
        this.ownerIp = ownerIp;
        this.ownerP2pPort = ownerP2pPort;
    }

    // --- Getter và Setter ---
    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getOwnerUsername() {
        return ownerUsername;
    }

    public void setOwnerUsername(String ownerUsername) {
        this.ownerUsername = ownerUsername;
    }

    public String getOwnerIp() {
        return ownerIp;
    }

    public void setOwnerIp(String ownerIp) {
        this.ownerIp = ownerIp;
    }

    public int getOwnerP2pPort() {
        return ownerP2pPort;
    }

    public void setOwnerP2pPort(int ownerP2pPort) {
        this.ownerP2pPort = ownerP2pPort;
    }


    public String toProtocolString() {
        return fileName + ";" + fileSize + ";" + ownerUsername + ";" + ownerIp + ";" + ownerP2pPort;
    }


    public static FileDescriptor fromProtocolString(String str) {
        if (str == null || str.trim().isEmpty()) return null;
        String[] parts = str.split(";");
        if (parts.length < 5) return null;
        try {
            String name = parts[0];
            long size = Long.parseLong(parts[1]);
            String owner = parts[2];
            String ip = parts[3];
            int port = Integer.parseInt(parts[4]);
            return new FileDescriptor(name, size, owner, ip, port);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FileDescriptor that = (FileDescriptor) o;
        return fileSize == that.fileSize &&
                ownerP2pPort == that.ownerP2pPort &&
                Objects.equals(fileName, that.fileName) &&
                Objects.equals(ownerUsername, that.ownerUsername);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileName, fileSize, ownerUsername, ownerP2pPort);
    }

    @Override
    public String toString() {
        return fileName + " (" + formatFileSize(fileSize) + ") - Chủ sở hữu: " + ownerUsername + " [" + ownerIp + ":" + ownerP2pPort + "]";
    }

    // Định dạng kích thước byte thành dạng dễ đọc (B, KB, MB, GB)
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %cB", bytes / Math.pow(1024, exp), pre);
    }
}
