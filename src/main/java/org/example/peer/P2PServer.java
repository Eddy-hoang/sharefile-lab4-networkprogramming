package org.example.peer;

import org.example.common.MessageProtocol;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ============================================================================
 * FOLDER: org.example.peer
 * FILE: P2PServer.java
 * ============================================================================
 * CHỨC NĂNG:
 * Máy chủ Socket P2P chạy trực tiếp ở phía mỗi Nút Peer (Peer-to-Peer Socket Listener).
 * Giúp mỗi Peer có khả năng tự nhận các kết nối trực tiếp từ các Peer khác.
 * 
 * CÁCH HOẠT ĐỘNG:
 * 1. Mở một ServerSocket trên cổng riêng của Peer (VD: 9001, 9002).
 * 2. Lắng nghe các kết nối trực tiếp đến từ các nút Peer khác:
 *    a) Nếu nhận lệnh P2P_CHAT: Đọc tin nhắn và kích hoạt callback hiển thị lên cửa sổ Chat.
 *    b) Nếu nhận lệnh P2P_FILE_REQ: Mở tệp tin trong thư mục chia sẻ, gửi phản hồi
 *       P2P_FILE_OK kèm dung lượng, sau đó đọc từng khối dữ liệu nhị phân (Byte chunks)
 *       bằng đệm 8KB và ghi trực tiếp ra luồng Socket đến Peer yêu cầu tải.
 */
public class P2PServer {
    private final int port;                             // Cổng P2P lắng nghe của Peer này
    private final SharedFileManager fileManager;       // Bộ quản lý file chia sẻ cục bộ
    private final P2PEventListener listener;           // Interface callback lắng nghe sự kiện để cập nhật lên UI

    private ServerSocket serverSocket;
    private boolean running = false;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * Interface định nghĩa các hàm Callback thông báo sự kiện P2P ngược lên Giao diện GUI
     */
    public interface P2PEventListener {
        void onDirectMessageReceived(String senderUsername, String message, String senderIp);
        void onFileTransferStarted(String requesterUsername, String fileName);
        void onFileTransferCompleted(String requesterUsername, String fileName, long bytesSent);
        void onFileTransferFailed(String requesterUsername, String fileName, String reason);
    }

    public P2PServer(int port, SharedFileManager fileManager, P2PEventListener listener) {
        this.port = port;
        this.fileManager = fileManager;
        this.listener = listener;
    }

    /**
     * Bắt đầu mở ServerSocket P2P lắng nghe trên cổng riêng
     */
    public synchronized void start() throws IOException {
        if (running) return;
        serverSocket = new ServerSocket(port);
        running = true;
        executor.execute(this::listen);
    }

    /**
     * Vòng lặp liên tục chờ nhận kết nối trực tiếp từ các Peer khác
     */
    private void listen() {
        while (running && serverSocket != null && !serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                // Mỗi khi có một Peer kết nối trực tiếp, xử lý trên một luồng riêng
                executor.execute(() -> handleIncomingPeerConnection(socket));
            } catch (IOException e) {
                if (running) {
                    System.err.println("Lỗi chấp nhận kết nối P2P: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Xử lý yêu cầu truyền thông P2P trực tiếp (Nhắn tin hoặc Yêu cầu tải File)
     */
    private void handleIncomingPeerConnection(Socket socket) {
        try (InputStream rawIn = socket.getInputStream();
             OutputStream rawOut = socket.getOutputStream();
             BufferedReader br = new BufferedReader(new InputStreamReader(rawIn, "UTF-8"));
             PrintWriter pw = new PrintWriter(new OutputStreamWriter(rawOut, "UTF-8"), true)) {

            String firstLine = br.readLine();
            if (firstLine == null || firstLine.trim().isEmpty()) return;

            String[] tokens = firstLine.split("\\" + MessageProtocol.DELIMITER, -1);
            String command = tokens[0];

            // 1. Trường hợp: Nhận Tin nhắn Chat P2P trực tiếp
            if (MessageProtocol.P2P_CMD_CHAT.equals(command)) {
                if (tokens.length >= 3) {
                    String sender = tokens[1];
                    String msg = tokens[2];
                    String senderIp = socket.getInetAddress().getHostAddress();
                    if (listener != null) {
                        listener.onDirectMessageReceived(sender, msg, senderIp);
                    }
                }
            } 
            // 2. Trường hợp: Đặt hàng tải File P2P trực tiếp
            else if (MessageProtocol.P2P_CMD_FILE_REQ.equals(command)) {
                if (tokens.length >= 3) {
                    String requester = tokens[1];
                    String fileName = tokens[2];
                    handleFileTransferRequest(requester, fileName, rawOut, pw);
                }
            }

        } catch (Exception e) {
            System.err.println("Lỗi xử lý kết nối P2P: " + e.getMessage());
        }
    }

    /**
     * Xử lý gửi dòng dữ liệu tệp tin (Binary Stream) trực tiếp qua Socket P2P cho Peer tải xuống
     */
    private void handleFileTransferRequest(String requester, String fileName, OutputStream out, PrintWriter pw) {
        File file = fileManager.getFileByName(fileName);
        if (file == null || !file.exists()) {
            pw.println(MessageProtocol.buildMessage(MessageProtocol.P2P_RES_FILE_ERR, "Không tìm thấy file trên máy chủ P2P này."));
            if (listener != null) {
                listener.onFileTransferFailed(requester, fileName, "Không tìm thấy file");
            }
            return;
        }

        long fileSize = file.length();
        if (listener != null) {
            listener.onFileTransferStarted(requester, fileName);
        }

        // 1. Gửi Header xác nhận thông tin file trước
        pw.println(MessageProtocol.buildMessage(MessageProtocol.P2P_RES_FILE_OK, fileName, String.valueOf(fileSize)));

        // 2. Tiến hành đọc dữ liệu file nhị phân và gửi qua OutputStream của Socket P2P
        byte[] buffer = new byte[8192];
        long bytesSent = 0;
        try (FileInputStream fis = new FileInputStream(file)) {
            int read;
            while ((read = fis.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                bytesSent += read;
            }
            out.flush();
            if (listener != null) {
                listener.onFileTransferCompleted(requester, fileName, bytesSent);
            }
        } catch (IOException e) {
            if (listener != null) {
                listener.onFileTransferFailed(requester, fileName, e.getMessage());
            }
        }
    }

    /**
     * Dừng máy chủ P2P Socket
     */
    public synchronized void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}
        executor.shutdownNow();
    }
}
