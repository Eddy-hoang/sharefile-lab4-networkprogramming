package org.example.server;

import org.example.common.FileDescriptor;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;


public class PeerInfo {
    private String username;        // Tên tài khoản Peer đăng ký
    private String ipAddress;       // Địa chỉ IP máy tính chứa nút Peer
    private int p2pPort;           // Cổng Socket mà nút Peer đang mở để lắng nghe kết nối P2P
    private long connectedAt;       // Thời điểm nút Peer kết nối vào hệ thống (Milisecond)
    
    // Danh sách các file mà Peer này đăng ký chia sẻ trên mạng P2P (Đảm bảo an toàn đa luồng)
    private final List<FileDescriptor> sharedFiles = new CopyOnWriteArrayList<>();

    public PeerInfo(String username, String ipAddress, int p2pPort) {
        this.username = username;
        this.ipAddress = ipAddress;
        this.p2pPort = p2pPort;
        this.connectedAt = System.currentTimeMillis();
    }

    public String getUsername() {
        return username;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public int getP2pPort() {
        return p2pPort;
    }

    public void setP2pPort(int p2pPort) {
        this.p2pPort = p2pPort;
    }

    public long getConnectedAt() {
        return connectedAt;
    }

    public List<FileDescriptor> getSharedFiles() {
        return sharedFiles;
    }

    public void updateSharedFiles(List<FileDescriptor> newFiles) {
        sharedFiles.clear();
        if (newFiles != null) {
            sharedFiles.addAll(newFiles);
        }
    }

    @Override
    public String toString() {
        return username + " (" + ipAddress + ":" + p2pPort + ") - Số file chia sẻ: " + sharedFiles.size();
    }
}
