package org.example.server;

import org.example.common.FileDescriptor;
import org.example.common.MessageProtocol;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class ServerMain extends JFrame {
    private static final int DEFAULT_PORT = 8888;

    private final ConcurrentHashMap<String, PeerInfo> onlinePeers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ClientHandler> peerHandlers = new ConcurrentHashMap<>();

    private ServerSocket serverSocket;
    private boolean isRunning = false;
    private final ExecutorService threadPool = Executors.newCachedThreadPool();

    private JTextArea logArea;
    private JTable peerTable;
    private DefaultTableModel peerTableModel;
    private JLabel statusLabel;
    private JButton startStopButton;
    private JTextField portField;

    public ServerMain() {
        super("P2P Central Directory & Indexing Server (Napster/Skype Model)");
        initUI();
    }

    private void initUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8, 8));

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        topPanel.setBorder(BorderFactory.createTitledBorder("Cấu hình Máy chủ Trung tâm"));

        topPanel.add(new JLabel("Cổng Server:"));
        portField = new JTextField(String.valueOf(DEFAULT_PORT), 6);
        portField.setForeground(Color.BLACK);
        topPanel.add(portField);

        startStopButton = new JButton("Bắt đầu Server");
        startStopButton.setBackground(new Color(46, 139, 87));
        startStopButton.setForeground(Color.BLACK);
        startStopButton.setFont(startStopButton.getFont().deriveFont(Font.BOLD));
        startStopButton.setFocusPainted(false);
        startStopButton.addActionListener(e -> toggleServer());
        topPanel.add(startStopButton);

        statusLabel = new JLabel("Trạng thái: ĐÃ DỪNG");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD));
        statusLabel.setForeground(Color.RED);
        topPanel.add(statusLabel);

        add(topPanel, BorderLayout.NORTH);

        peerTableModel = new DefaultTableModel(new String[]{"Username", "Địa chỉ IP", "Cổng P2P", "Số File chia sẻ", "Thời gian kết nối"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        peerTable = new JTable(peerTableModel);
        peerTable.setForeground(Color.BLACK);
        JScrollPane peerTableScrollPane = new JScrollPane(peerTable);
        peerTableScrollPane.setBorder(BorderFactory.createTitledBorder("Danh sách Peer Đang Online"));

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        logArea.setBackground(new Color(30, 30, 30));
        logArea.setForeground(new Color(220, 220, 220));
        JScrollPane logScrollPane = new JScrollPane(logArea);
        logScrollPane.setBorder(BorderFactory.createTitledBorder("Nhật ký Hoạt động Máy chủ"));

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, peerTableScrollPane, logScrollPane);
        splitPane.setResizeWeight(0.5);
        add(splitPane, BorderLayout.CENTER);
    }

    private synchronized void toggleServer() {
        if (!isRunning) {
            int port;
            try {
                port = Integer.parseInt(portField.getText().trim());
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "Số cổng không hợp lệ", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }

            try {
                serverSocket = new ServerSocket(port);
                isRunning = true;
                startStopButton.setText("Dừng Server");
                startStopButton.setBackground(new Color(178, 34, 34));
                startStopButton.setForeground(Color.BLACK);
                statusLabel.setText("Trạng thái: ĐANG CHẠY (Port " + port + ")");
                statusLabel.setForeground(new Color(46, 139, 87));
                portField.setEnabled(false);

                log("Máy chủ Directory Server đã bật và lắng nghe trên cổng " + port + "...");

                threadPool.execute(this::acceptConnections);
            } catch (IOException e) {
                log("Không thể khởi động Server tại cổng " + port + ": " + e.getMessage());
                JOptionPane.showMessageDialog(this, "Lỗi bật Server: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            stopServer();
        }
    }

    private void acceptConnections() {
        while (isRunning && serverSocket != null && !serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                log("Kết nối mới đến từ IP: " + socket.getInetAddress().getHostAddress() + ":" + socket.getPort());
                threadPool.execute(new ClientHandler(socket, this));
            } catch (IOException e) {
                if (isRunning) {
                    log("Lỗi chấp nhận kết nối: " + e.getMessage());
                }
            }
        }
    }

    public synchronized void stopServer() {
        isRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}

        onlinePeers.clear();
        peerHandlers.clear();
        updatePeerTable();

        startStopButton.setText("Bắt đầu Server");
        startStopButton.setBackground(new Color(46, 139, 87));
        startStopButton.setForeground(Color.BLACK);
        statusLabel.setText("Trạng thái: ĐÃ DỪNG");
        statusLabel.setForeground(Color.RED);
        portField.setEnabled(true);
        log("Máy chủ đã dừng.");
    }

    public synchronized boolean registerPeer(String username, String ip, int p2pPort, ClientHandler handler) {
        if (onlinePeers.containsKey(username.toLowerCase())) {
            return false;
        }
        PeerInfo info = new PeerInfo(username, ip, p2pPort);
        onlinePeers.put(username.toLowerCase(), info);
        peerHandlers.put(username.toLowerCase(), handler);
        log("Đã đăng ký Peer: " + username + " -> " + ip + ":" + p2pPort);
        updatePeerTable();
        return true;
    }

    public synchronized void unregisterPeer(String username) {
        if (username != null) {
            onlinePeers.remove(username.toLowerCase());
            peerHandlers.remove(username.toLowerCase());
            log("Peer đã ngắt kết nối: " + username);
            updatePeerTable();
        }
    }

    public PeerInfo getPeer(String username) {
        if (username == null) return null;
        return onlinePeers.get(username.toLowerCase());
    }

    public List<FileDescriptor> searchFiles(String query, String requesterUser) {
        List<FileDescriptor> results = new ArrayList<>();
        String q = query != null ? query.toLowerCase() : "";

        for (PeerInfo peer : onlinePeers.values()) {
            if (peer.getUsername().equalsIgnoreCase(requesterUser)) continue;

            for (FileDescriptor fd : peer.getSharedFiles()) {
                if (q.isEmpty() || fd.getFileName().toLowerCase().contains(q)) {
                    results.add(fd);
                }
            }
        }
        return results;
    }

    public String buildPeerListResponse() {
        StringBuilder sb = new StringBuilder();
        sb.append(MessageProtocol.RES_PEER_LIST);

        boolean first = true;
        for (PeerInfo peer : onlinePeers.values()) {
            sb.append(first ? MessageProtocol.DELIMITER : "#");
            sb.append(peer.getUsername()).append(";").append(peer.getIpAddress()).append(";").append(peer.getP2pPort());
            first = false;
        }
        return sb.toString();
    }

    public void broadcastPeerList() {
        String msg = buildPeerListResponse();
        for (ClientHandler handler : peerHandlers.values()) {
            handler.sendResponse(msg);
        }
    }

    private void updatePeerTable() {
        SwingUtilities.invokeLater(() -> {
            peerTableModel.setRowCount(0);
            for (PeerInfo peer : onlinePeers.values()) {
                peerTableModel.addRow(new Object[]{
                        peer.getUsername(),
                        peer.getIpAddress(),
                        peer.getP2pPort(),
                        peer.getSharedFiles().size(),
                        new Date(peer.getConnectedAt()).toString()
                });
            }
        });
    }

    public void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + new java.text.SimpleDateFormat("HH:mm:ss").format(new Date()) + "] " + msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            ServerMain serverUI = new ServerMain();
            serverUI.setVisible(true);
        });
    }
}
