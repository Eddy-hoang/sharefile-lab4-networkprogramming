package org.example.server;

import org.example.common.FileDescriptor;
import org.example.common.MessageProtocol;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;


public class ClientHandler implements Runnable {
    private final Socket socket;           // Socket kết nối TCP trực tiếp với Peer client
    private final ServerMain server;       // Tham chiếu đến Server chính để gọi các hàm quản lý chung
    private PrintWriter out;               // Luồng ghi dữ liệu phản hồi cho Client
    private BufferedReader in;             // Luồng đọc dữ liệu yêu cầu từ Client
    private PeerInfo peerInfo;             // Đối tượng lưu thông tin của Peer này sau khi đăng ký
    private boolean running = true;

    public ClientHandler(Socket socket, ServerMain server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            out = new PrintWriter(socket.getOutputStream(), true);

            String clientIp = socket.getInetAddress().getHostAddress();

            // Tự động chuyển đổi 127.0.0.1 thành IP LAN thật (Wi-Fi) của máy chủ nếu Peer chạy cùng máy với Server
            if ("127.0.0.1".equals(clientIp) || "0:0:0:0:0:0:0:1".equals(clientIp)) {
                clientIp = getLocalLanIp();
            }

            String inputLine;

            // Vòng lặp liên tục đọc các câu lệnh nhận được từ Socket của Client
            while (running && (inputLine = in.readLine()) != null) {
                server.log("Server nhận từ " + (peerInfo != null ? peerInfo.getUsername() : clientIp) + ": " + inputLine);
                processMessage(inputLine, clientIp);
            }
        } catch (Exception e) {
            server.log("Kết nối bị ngắt đối với " + (peerInfo != null ? peerInfo.getUsername() : "Client") + ": " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    /**
     * Tự động lấy địa chỉ IP LAN (Wi-Fi) thật của máy thay vì 127.0.0.1
     */
    private static String getLocalLanIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }

    // Phân tích câu lệnh dựa trên giao thức MessageProtocol và điều hướng xử lý
    private void processMessage(String rawMsg, String clientIp) {
        if (rawMsg == null || rawMsg.trim().isEmpty()) return;
        String[] tokens = rawMsg.split("\\" + MessageProtocol.DELIMITER, -1);
        String command = tokens[0];

        switch (command) {
            case MessageProtocol.CMD_REGISTER:
                handleRegister(tokens, clientIp);
                break;
            case MessageProtocol.CMD_UPDATE_FILES:
                handleUpdateFiles(tokens);
                break;
            case MessageProtocol.CMD_GET_PEERS:
                handleGetPeers();
                break;
            case MessageProtocol.CMD_SEARCH_FILES:
                handleSearchFiles(tokens);
                break;
            case MessageProtocol.CMD_GET_ENDPOINT:
                handleGetEndpoint(tokens);
                break;
            case MessageProtocol.CMD_LOGOUT:
                running = false;
                break;
            default:
                sendResponse(MessageProtocol.buildMessage(MessageProtocol.RES_ERROR, "Lệnh không hợp lệ: " + command));
                break;
        }
    }

    /**
     * Xử lý lệnh Đăng ký nút Peer với Server
     */
    private void handleRegister(String[] tokens, String clientIp) {
        if (tokens.length < 3) {
            sendResponse(MessageProtocol.buildMessage(MessageProtocol.RES_REGISTER_ERR, "Thiếu tham số đăng ký."));
            return;
        }
        String username = tokens[1].trim();
        int p2pPort;
        try {
            p2pPort = Integer.parseInt(tokens[2].trim());
        } catch (NumberFormatException e) {
            sendResponse(MessageProtocol.buildMessage(MessageProtocol.RES_REGISTER_ERR, "Cổng P2P không hợp lệ."));
            return;
        }

        if (username.isEmpty()) {
            sendResponse(MessageProtocol.buildMessage(MessageProtocol.RES_REGISTER_ERR, "Username không được để trống."));
            return;
        }

        // Đăng ký vào danh sách onlinePeers của Server chính
        boolean success = server.registerPeer(username, clientIp, p2pPort, this);
        if (success) {
            this.peerInfo = server.getPeer(username);
            sendResponse(MessageProtocol.buildMessage(MessageProtocol.RES_REGISTER_OK, "Đăng ký thành công tài khoản: " + username));
            // Phát sóng (Broadcast) danh sách Peer mới cho toàn bộ các Peer khác
            server.broadcastPeerList();
        } else {
            sendResponse(MessageProtocol.buildMessage(MessageProtocol.RES_REGISTER_ERR, "Tên tài khoản '" + username + "' đã có người sử dụng."));
        }
    }

    // Xử lý lệnh Cập nhật danh sách file chia sẻ của Peer
    private void handleUpdateFiles(String[] tokens) {
        if (peerInfo == null) {
            sendResponse(MessageProtocol.buildMessage(MessageProtocol.RES_ERROR, "Chưa đăng ký tài khoản."));
            return;
        }
        List<FileDescriptor> list = new ArrayList<>();
        if (tokens.length >= 2 && !tokens[1].isEmpty()) {
            String[] fileEntries = tokens[1].split("#");
            for (String entry : fileEntries) {
                FileDescriptor fd = FileDescriptor.fromProtocolString(entry);
                if (fd != null) {
                    fd.setOwnerUsername(peerInfo.getUsername());
                    fd.setOwnerIp(peerInfo.getIpAddress());
                    fd.setOwnerP2pPort(peerInfo.getP2pPort());
                    list.add(fd);
                }
            }
        }
        peerInfo.updateSharedFiles(list);
        server.log("Peer " + peerInfo.getUsername() + " đã cập nhật " + list.size() + " file chia sẻ.");
    }

    // Xử lý lệnh yêu cầu danh sách các Peer đang online
    private void handleGetPeers() {
        sendResponse(server.buildPeerListResponse());
    }

    // Xử lý lệnh Tìm kiếm file từ chỉ mục danh sách file của Server trung tâm (Napster Model)
    private void handleSearchFiles(String[] tokens) {
        String query = (tokens.length >= 2) ? tokens[1].trim().toLowerCase() : "";
        List<FileDescriptor> results = server.searchFiles(query, peerInfo != null ? peerInfo.getUsername() : "");
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            if (i > 0) sb.append("#");
            sb.append(results.get(i).toProtocolString());
        }
        sendResponse(MessageProtocol.buildMessage(MessageProtocol.RES_SEARCH_RESULTS, sb.toString()));
    }

    // Trả về thông tin IP & Cổng P2P của 1 Peer cụ thể
    private void handleGetEndpoint(String[] tokens) {
        if (tokens.length < 2) return;
        String targetUser = tokens[1].trim();
        PeerInfo targetPeer = server.getPeer(targetUser);
        if (targetPeer != null) {
            sendResponse(MessageProtocol.buildMessage(MessageProtocol.RES_ENDPOINT, targetPeer.getUsername(), targetPeer.getIpAddress(), String.valueOf(targetPeer.getP2pPort())));
        } else {
            sendResponse(MessageProtocol.buildMessage(MessageProtocol.RES_ERROR, "Peer '" + targetUser + "' không tồn tại hoặc offline."));
        }
    }

    // Gửi chuỗi tin nhắn phản hồi về cho Client kết nối với Handler này
    public void sendResponse(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    // Dọn dẹp tài nguyên khi Peer ngắt kết nối
    private void cleanup() {
        running = false;
        if (peerInfo != null) {
            server.unregisterPeer(peerInfo.getUsername());
            server.broadcastPeerList();
        }
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (Exception ignored) {}
    }
}
