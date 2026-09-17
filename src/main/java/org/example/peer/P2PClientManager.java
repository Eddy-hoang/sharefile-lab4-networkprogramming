package org.example.peer;

import org.example.common.MessageProtocol;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class P2PClientManager {
    // Thread pool chuyên phục vụ cho các tác vụ tải file bất đồng bộ
    private final ExecutorService downloadExecutor = Executors.newCachedThreadPool();

    // Interface lắng nghe và cập nhật tiến trình tải file P2P về cho UI
    public interface DownloadProgressListener {
        void onProgressUpdate(String fileName, long bytesDownloaded, long totalBytes, double speedKBps);
        void onDownloadComplete(String fileName, File savedFile);
        void onDownloadFailed(String fileName, String reason);
    }

    public interface ChatCallback {
        void onSuccess();
        void onFailure(String error);
    }


    public void sendDirectChatMessageAsync(String targetIp, int targetP2pPort, String myUsername, String message, ChatCallback callback) {
        new Thread(() -> {
            try (Socket socket = new Socket(targetIp, targetP2pPort);
                 OutputStream out = socket.getOutputStream();
                 PrintWriter pw = new PrintWriter(new OutputStreamWriter(out, "UTF-8"), true)) {

                // Gửi lệnh P2P_CHAT trực tiếp qua Socket
                pw.println(MessageProtocol.buildMessage(MessageProtocol.P2P_CMD_CHAT, myUsername, message));
                if (callback != null) callback.onSuccess();
            } catch (Exception e) {
                if (callback != null) callback.onFailure(e.getMessage());
            }
        }).start();
    }


    public void downloadFileAsync(String targetIp, int targetP2pPort, String myUsername, String fileName, File destinationDir, DownloadProgressListener listener) {
        downloadExecutor.execute(() -> {
            File destinationFile = new File(destinationDir, fileName);

            try (Socket socket = new Socket(targetIp, targetP2pPort);
                 InputStream rawIn = socket.getInputStream();
                 OutputStream rawOut = socket.getOutputStream();
                 BufferedReader br = new BufferedReader(new InputStreamReader(rawIn, "UTF-8"));
                 PrintWriter pw = new PrintWriter(new OutputStreamWriter(rawOut, "UTF-8"), true)) {

                // 1. Gửi câu lệnh yêu cầu tải file
                pw.println(MessageProtocol.buildMessage(MessageProtocol.P2P_CMD_FILE_REQ, myUsername, fileName));

                // 2. Đọc dòng phản hồi Header từ P2PServer của Peer đích
                String responseHeader = br.readLine();
                if (responseHeader == null || responseHeader.isEmpty()) {
                    if (listener != null) listener.onDownloadFailed(fileName, "Không nhận được phản hồi từ Peer.");
                    return;
                }

                String[] tokens = responseHeader.split("\\" + MessageProtocol.DELIMITER, -1);
                if (MessageProtocol.P2P_RES_FILE_ERR.equals(tokens[0])) {
                    String reason = tokens.length >= 2 ? tokens[1] : "Lỗi phía máy chủ Peer";
                    if (listener != null) listener.onDownloadFailed(fileName, reason);
                    return;
                }

                if (!MessageProtocol.P2P_RES_FILE_OK.equals(tokens[0]) || tokens.length < 3) {
                    if (listener != null) listener.onDownloadFailed(fileName, "Header giao thức sai định dạng: " + responseHeader);
                    return;
                }

                long totalBytes = Long.parseLong(tokens[2]);

                // 3. Tiến hành đọc dữ liệu nhị phân truyền về và ghi vào File đĩa cứng
                try (FileOutputStream fos = new FileOutputStream(destinationFile)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    long bytesDownloaded = 0;

                    long startTime = System.currentTimeMillis();
                    long lastUpdateTime = startTime;
                    long bytesSinceLastUpdate = 0;

                    while (bytesDownloaded < totalBytes && (read = rawIn.read(buffer)) != -1) {
                        fos.write(buffer, 0, read);
                        bytesDownloaded += read;
                        bytesSinceLastUpdate += read;

                        long now = System.currentTimeMillis();
                        // Cập nhật giao diện tiến trình sau mỗi 250ms
                        if (now - lastUpdateTime >= 250 || bytesDownloaded == totalBytes) {
                            double timeDiffSec = (now - lastUpdateTime) / 1000.0;
                            double speed = timeDiffSec > 0 ? (bytesSinceLastUpdate / 1024.0) / timeDiffSec : 0;
                            lastUpdateTime = now;
                            bytesSinceLastUpdate = 0;

                            if (listener != null) {
                                listener.onProgressUpdate(fileName, bytesDownloaded, totalBytes, speed);
                            }
                        }
                    }

                    fos.flush();

                    if (bytesDownloaded >= totalBytes) {
                        if (listener != null) {
                            listener.onDownloadComplete(fileName, destinationFile);
                        }
                    } else {
                        if (listener != null) {
                            listener.onDownloadFailed(fileName, "Tải chưa hoàn tất (" + bytesDownloaded + "/" + totalBytes + " bytes)");
                        }
                    }
                }

            } catch (Exception e) {
                if (listener != null) {
                    listener.onDownloadFailed(fileName, e.getMessage());
                }
            }
        });
    }
}
