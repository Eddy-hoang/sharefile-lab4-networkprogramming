package org.example.peer;

import org.example.common.FileDescriptor;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class SharedFileManager {
    private File sharedDirectory; // Thư mục lưu trữ các tệp tin được chia sẻ

    public SharedFileManager(File sharedDirectory) {
        setSharedDirectory(sharedDirectory);
    }

    public File getSharedDirectory() {
        return sharedDirectory;
    }

    /**
     * Thiết lập thư mục chia sẻ. Tự động tạo thư mục nếu chưa tồn tại.
     */
    public void setSharedDirectory(File dir) {
        if (dir != null && (!dir.exists() || !dir.isDirectory())) {
            dir.mkdirs();
        }
        this.sharedDirectory = dir;
    }

    /**
     * Quét thư mục chia sẻ và tạo danh sách metadata (FileDescriptor) đại diện cho các file.
     * @param ownerUsername Tên tài khoản Peer sở hữu
     * @param ownerIp Địa chỉ IP của Peer
     * @param ownerP2pPort Cổng P2P của Peer
     * @return Danh sách các FileDescriptor đại diện cho các file có sẵn trong thư mục
     */
    public List<FileDescriptor> scanSharedFiles(String ownerUsername, String ownerIp, int ownerP2pPort) {
        List<FileDescriptor> descriptors = new ArrayList<>();
        if (sharedDirectory == null || !sharedDirectory.exists()) {
            return descriptors;
        }

        File[] files = sharedDirectory.listFiles();
        if (files != null) {
            for (File f : files) {
                // Chỉ lấy các tệp tin (File), bỏ qua thư mục con và các file bị ẩn (hidden)
                if (f.isFile() && !f.isHidden()) {
                    descriptors.add(new FileDescriptor(
                            f.getName(),
                            f.length(),
                            ownerUsername,
                            ownerIp,
                            ownerP2pPort
                    ));
                }
            }
        }
        return descriptors;
    }

    /**
     * Tìm kiếm đối tượng File trong thư mục chia sẻ theo tên tệp tin
     */
    public File getFileByName(String fileName) {
        if (sharedDirectory == null || !sharedDirectory.exists() || fileName == null) {
            return null;
        }
        File f = new File(sharedDirectory, fileName);
        if (f.exists() && f.isFile()) {
            return f;
        }
        return null;
    }

    /**
     * Mở luồng đọc InputStream cho một file trong thư mục chia sẻ để phục vụ việc gửi file qua mạng
     */
    public InputStream openFileInputStream(String fileName) throws Exception {
        File file = getFileByName(fileName);
        if (file == null) {
            throw new IllegalArgumentException("Không tìm thấy file '" + fileName + "' trong thư mục chia sẻ.");
        }
        return new FileInputStream(file);
    }
}
