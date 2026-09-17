package org.example.gui;

import org.example.common.FileDescriptor;
import org.example.common.MessageProtocol;
import org.example.peer.P2PClientManager;
import org.example.peer.P2PServer;
import org.example.peer.SharedFileManager;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;


public class MainGUI extends JFrame implements P2PServer.P2PEventListener {

    // --- Quản lý Mạng & Luồng P2P ---
    private Socket serverSocket;
    private PrintWriter serverOut;
    private BufferedReader serverIn;
    private boolean isConnected = false;
    private Thread serverListenThread;

    private String myUsername;
    private int myP2pPort;
    private File mySharedFolder;
    private File myDownloadsFolder;

    private SharedFileManager sharedFileManager;
    private P2PServer p2pServer;
    private final P2PClientManager p2pClientManager = new P2PClientManager();

    // Lưu danh sách Peer online: Username -> "IP:Port"
    private final Map<String, String> onlinePeersMap = new ConcurrentHashMap<>();

    // --- UI Controls: Thanh kết nối phía trên ---
    private JTextField serverHostField;
    private JTextField serverPortField;
    private JTextField usernameField;
    private JTextField p2pPortField;
    private JButton connectButton;
    private JLabel statusLabel;
    private JButton sharedFolderButton;
    private JLabel sharedFolderLabel;

    // --- UI Controls: Tab 1 - P2P Chat ---
    private JList<String> peerListWidget;
    private DefaultListModel<String> peerListModel;
    private JTextPane chatTranscriptPane;
    private JTextField chatInputField;
    private JButton sendChatButton;
    private JLabel selectedChatPeerLabel;
    private String currentChatPeer = null;

    // --- UI Controls: Tab 2 - P2P File Sharing ---
    private JTextField searchField;
    private JButton searchButton;
    private JTable searchResultsTable;
    private DefaultTableModel searchResultsTableModel;

    private JTable downloadsTable;
    private DefaultTableModel downloadsTableModel;
    private final Map<String, Integer> downloadRowMap = new ConcurrentHashMap<>();

    private JList<String> localSharedFilesWidget;
    private DefaultListModel<String> localSharedFilesModel;

    public MainGUI() {
        super("P2P Hybrid Chat & File Transfer Client (Skype/Napster Model)");
        initDefaults();
        initUI();
    }

    private void initDefaults() {
        int randomPort = 9000 + (int) (Math.random() * 900);
        myP2pPort = randomPort;

        String userHome = System.getProperty("user.home");
        mySharedFolder = new File(userHome + File.separator + "P2P_Shared");
        if (!mySharedFolder.exists()) mySharedFolder.mkdirs();

        myDownloadsFolder = new File(userHome + File.separator + "P2P_Downloads");
        if (!myDownloadsFolder.exists()) myDownloadsFolder.mkdirs();

        sharedFileManager = new SharedFileManager(mySharedFolder);
    }

    private void initUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(980, 680);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(5, 5));

        // Khối kết nối máy chủ & cấu hình thư mục phía trên
        JPanel topContainer = new JPanel(new BorderLayout());
        JPanel connPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        connPanel.setBorder(BorderFactory.createTitledBorder("Kết nối Máy chủ Trung tâm & Cấu hình P2P"));

        connPanel.add(new JLabel("Server Host:"));
        serverHostField = new JTextField("localhost", 9);
        connPanel.add(serverHostField);

        connPanel.add(new JLabel("Port:"));
        serverPortField = new JTextField("8888", 5);
        connPanel.add(serverPortField);

        connPanel.add(new JLabel("Username:"));
        usernameField = new JTextField("User" + (int)(Math.random() * 100), 8);
        connPanel.add(usernameField);

        connPanel.add(new JLabel("Cổng P2P của tôi:"));
        p2pPortField = new JTextField(String.valueOf(myP2pPort), 5);
        connPanel.add(p2pPortField);

        connectButton = new JButton("Kết nối Mạng P2P");
        connectButton.setBackground(new Color(41, 128, 185));
        connectButton.setForeground(Color.BLACK);
        connectButton.setFocusPainted(false);
        connectButton.addActionListener(e -> toggleConnection());
        connPanel.add(connectButton);

        statusLabel = new JLabel("Trạng thái: OFFLINE");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD));
        statusLabel.setForeground(Color.RED);
        connPanel.add(statusLabel);

        // Thanh đổi thư mục chia sẻ
        JPanel folderPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        sharedFolderButton = new JButton("Chọn Thư mục Chia sẻ...");
        sharedFolderButton.setForeground(Color.BLACK);
        sharedFolderButton.addActionListener(e -> chooseSharedFolder());
        folderPanel.add(sharedFolderButton);

        sharedFolderLabel = new JLabel("Thư mục Chia sẻ: " + mySharedFolder.getAbsolutePath());
        sharedFolderLabel.setFont(new Font("SansSerif", Font.ITALIC, 11));
        sharedFolderLabel.setForeground(Color.BLACK);
        folderPanel.add(sharedFolderLabel);

        topContainer.add(connPanel, BorderLayout.NORTH);
        topContainer.add(folderPanel, BorderLayout.SOUTH);
        add(topContainer, BorderLayout.NORTH);

        // Khối các Tab giao diện
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Direct P2P Chat (Skype Model)", createChatTab());
        tabbedPane.addTab("P2P File Sharing & Search (Napster Model)", createFileSharingTab());

        add(tabbedPane, BorderLayout.CENTER);
    }

    private JPanel createChatTab() {
        JPanel chatPanel = new JPanel(new BorderLayout(6, 6));

        // Bên trái: Danh sách các Peer Online
        peerListModel = new DefaultListModel<>();
        peerListWidget = new JList<>(peerListModel);
        peerListWidget.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        peerListWidget.setFont(new Font("SansSerif", Font.PLAIN, 13));
        peerListWidget.setForeground(Color.BLACK);
        peerListWidget.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selected = peerListWidget.getSelectedValue();
                if (selected != null) {
                    currentChatPeer = selected;
                    selectedChatPeerLabel.setText("Phiên Chat P2P Trực tiếp với: " + currentChatPeer + " (" + onlinePeersMap.get(selected) + ")");
                }
            }
        });
        JScrollPane peerListScroll = new JScrollPane(peerListWidget);
        peerListScroll.setPreferredSize(new Dimension(200, 0));
        peerListScroll.setBorder(BorderFactory.createTitledBorder("Danh sách Peer Online"));

        // Bên phải: Khung tin nhắn & ô nhập tin nhắn
        JPanel rightChatPanel = new JPanel(new BorderLayout(5, 5));

        selectedChatPeerLabel = new JLabel("Chọn một Peer đang online để bắt đầu Chat P2P");
        selectedChatPeerLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        selectedChatPeerLabel.setForeground(Color.BLACK);
        selectedChatPeerLabel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        rightChatPanel.add(selectedChatPeerLabel, BorderLayout.NORTH);

        chatTranscriptPane = new JTextPane();
        chatTranscriptPane.setEditable(false);
        chatTranscriptPane.setFont(new Font("SansSerif", Font.PLAIN, 12));
        chatTranscriptPane.setForeground(Color.BLACK);
        JScrollPane transcriptScroll = new JScrollPane(chatTranscriptPane);
        transcriptScroll.setBorder(BorderFactory.createTitledBorder("Nội dung Nhắn tin P2P"));
        rightChatPanel.add(transcriptScroll, BorderLayout.CENTER);

        JPanel inputPanel = new JPanel(new BorderLayout(5, 5));
        chatInputField = new JTextField();
        chatInputField.setForeground(Color.BLACK);
        chatInputField.addActionListener(e -> sendDirectMessage());
        sendChatButton = new JButton("Gửi Tin Nhắn");
        sendChatButton.setBackground(new Color(39, 174, 96));
        sendChatButton.setForeground(Color.BLACK);
        sendChatButton.addActionListener(e -> sendDirectMessage());

        inputPanel.add(chatInputField, BorderLayout.CENTER);
        inputPanel.add(sendChatButton, BorderLayout.EAST);
        inputPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        rightChatPanel.add(inputPanel, BorderLayout.SOUTH);

        chatPanel.add(peerListScroll, BorderLayout.WEST);
        chatPanel.add(rightChatPanel, BorderLayout.CENTER);

        return chatPanel;
    }

    private JPanel createFileSharingTab() {
        JPanel filePanel = new JPanel(new BorderLayout(6, 6));

        // Khung tìm kiếm ở phía trên
        JPanel searchBarPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        searchBarPanel.setBorder(BorderFactory.createTitledBorder("Tìm kiếm File toàn Mạng (Chỉ mục Trung tâm)"));

        searchBarPanel.add(new JLabel("Từ khóa:"));
        searchField = new JTextField(20);
        searchField.setForeground(Color.BLACK);
        searchField.addActionListener(e -> performSearch());
        searchBarPanel.add(searchField);

        searchButton = new JButton("Tìm File");
        searchButton.setBackground(new Color(142, 68, 173));
        searchButton.setForeground(Color.BLACK);
        searchButton.addActionListener(e -> performSearch());
        searchBarPanel.add(searchButton);

        JButton refreshLocalFilesBtn = new JButton("Quét lại File Chia sẻ");
        refreshLocalFilesBtn.setForeground(Color.BLACK);
        refreshLocalFilesBtn.addActionListener(e -> refreshAndPublishSharedFiles());
        searchBarPanel.add(refreshLocalFilesBtn);

        filePanel.add(searchBarPanel, BorderLayout.NORTH);

        // Bảng kết quả tìm kiếm (Ở trên) và Bảng quản lý tiến trình tải (Ở dưới)
        searchResultsTableModel = new DefaultTableModel(new String[]{"Tên File", "Kích thước", "Peer Sở hữu", "Endpoint", "Hành động"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 4; // Chỉ cột Nút Tải về là bấm được
            }
        };
        searchResultsTable = new JTable(searchResultsTableModel);
        searchResultsTable.setRowHeight(24);
        searchResultsTable.setForeground(Color.BLACK);

        // Gắn Renderer & Editor hiển thị Nút "Tải xuống" trong bảng
        searchResultsTable.getColumn("Hành động").setCellRenderer(new ButtonRenderer());
        searchResultsTable.getColumn("Hành động").setCellEditor(new ButtonEditor(new JCheckBox(), this::onDownloadButtonClicked));

        JScrollPane searchScroll = new JScrollPane(searchResultsTable);
        searchScroll.setBorder(BorderFactory.createTitledBorder("Kết quả Tìm kiếm File toàn Mạng"));

        // Bảng danh sách tiến trình tải file P2P
        downloadsTableModel = new DefaultTableModel(new String[]{"Tên File", "Kích thước", "Tiến trình (%)", "Tốc độ (KB/s)", "Trạng thái"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        downloadsTable = new JTable(downloadsTableModel);
        downloadsTable.setRowHeight(22);
        downloadsTable.setForeground(Color.BLACK);
        downloadsTable.getColumn("Tiến trình (%)").setCellRenderer(new ProgressCellRenderer());

        JScrollPane downloadsScroll = new JScrollPane(downloadsTable);
        downloadsScroll.setBorder(BorderFactory.createTitledBorder("Tiến trình Tải File P2P"));

        // Danh sách File chia sẻ cục bộ
        localSharedFilesModel = new DefaultListModel<>();
        localSharedFilesWidget = new JList<>(localSharedFilesModel);
        localSharedFilesWidget.setForeground(Color.BLACK);
        JScrollPane localFilesScroll = new JScrollPane(localSharedFilesWidget);
        localFilesScroll.setPreferredSize(new Dimension(240, 0));
        localFilesScroll.setBorder(BorderFactory.createTitledBorder("File Chia sẻ của Tôi"));

        JPanel bottomPanel = new JPanel(new BorderLayout(4, 4));
        bottomPanel.add(downloadsScroll, BorderLayout.CENTER);
        bottomPanel.add(localFilesScroll, BorderLayout.EAST);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, searchScroll, bottomPanel);
        splitPane.setResizeWeight(0.5);

        filePanel.add(splitPane, BorderLayout.CENTER);

        return filePanel;
    }

    private synchronized void toggleConnection() {
        if (!isConnected) {
            String host = serverHostField.getText().trim();
            int port;
            int p2pPort;
            myUsername = usernameField.getText().trim();

            try {
                port = Integer.parseInt(serverPortField.getText().trim());
                p2pPort = Integer.parseInt(p2pPortField.getText().trim());
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "Cổng không hợp lệ.", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (myUsername.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Username không được trống.", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }

            try {
                this.myP2pPort = p2pPort;
                p2pServer = new P2PServer(myP2pPort, sharedFileManager, this);
                p2pServer.start();

                serverSocket = new Socket(host, port);
                serverOut = new PrintWriter(new OutputStreamWriter(serverSocket.getOutputStream(), "UTF-8"), true);
                serverIn = new BufferedReader(new InputStreamReader(serverSocket.getInputStream(), "UTF-8"));

                serverOut.println(MessageProtocol.buildMessage(MessageProtocol.CMD_REGISTER, myUsername, String.valueOf(myP2pPort)));

                String response = serverIn.readLine();
                if (response != null && response.startsWith(MessageProtocol.RES_REGISTER_OK)) {
                    isConnected = true;
                    connectButton.setText("Ngắt kết nối");
                    connectButton.setBackground(new Color(192, 57, 43));
                    connectButton.setForeground(Color.BLACK);
                    statusLabel.setText("Trạng thái: ONLINE (" + myUsername + ")");
                    statusLabel.setForeground(new Color(39, 174, 96));
                    disableHeaderInputs(true);

                    serverListenThread = new Thread(this::listenToServer);
                    serverListenThread.start();

                    refreshAndPublishSharedFiles();

                    appendChatLog("Hệ thống", "Đã kết nối thành công đến Máy chủ Trung tâm tại " + host + ":" + port);
                } else {
                    String err = (response != null) ? response : "Đăng ký thất bại.";
                    JOptionPane.showMessageDialog(this, err, "Lỗi đăng ký", JOptionPane.ERROR_MESSAGE);
                    p2pServer.stop();
                    serverSocket.close();
                }
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Lỗi kết nối: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                if (p2pServer != null) p2pServer.stop();
            }
        } else {
            disconnect();
        }
    }

    private void listenToServer() {
        try {
            String line;
            while (isConnected && (line = serverIn.readLine()) != null) {
                processServerMessage(line);
            }
        } catch (Exception e) {
            if (isConnected) {
                appendChatLog("Hệ thống", "Mất kết nối đến Máy chủ Trung tâm.");
            }
        } finally {
            if (isConnected) {
                SwingUtilities.invokeLater(this::disconnect);
            }
        }
    }

    private void processServerMessage(String msg) {
        if (msg == null || msg.trim().isEmpty()) return;
        String[] tokens = msg.split("\\" + MessageProtocol.DELIMITER, -1);
        String command = tokens[0];

        switch (command) {
            case MessageProtocol.RES_PEER_LIST:
                handlePeerListResponse(tokens);
                break;
            case MessageProtocol.RES_SEARCH_RESULTS:
                handleSearchResultsResponse(tokens);
                break;
        }
    }

    private void handlePeerListResponse(String[] tokens) {
        SwingUtilities.invokeLater(() -> {
            peerListModel.clear();
            onlinePeersMap.clear();

            if (tokens.length >= 2 && !tokens[1].isEmpty()) {
                String[] entries = tokens[1].split("#");
                for (String entry : entries) {
                    String[] parts = entry.split(";");
                    if (parts.length >= 3) {
                        String user = parts[0];
                        String ip = parts[1];
                        String port = parts[2];

                        if (!user.equalsIgnoreCase(myUsername)) {
                            onlinePeersMap.put(user, ip + ":" + port);
                            peerListModel.addElement(user);
                        }
                    }
                }
            }
        });
    }

    private void handleSearchResultsResponse(String[] tokens) {
        SwingUtilities.invokeLater(() -> {
            searchResultsTableModel.setRowCount(0);
            if (tokens.length >= 2 && !tokens[1].isEmpty()) {
                String[] entries = tokens[1].split("#");
                for (String entry : entries) {
                    FileDescriptor fd = FileDescriptor.fromProtocolString(entry);
                    if (fd != null && !fd.getOwnerUsername().equalsIgnoreCase(myUsername)) {
                        searchResultsTableModel.addRow(new Object[]{
                                fd.getFileName(),
                                FileDescriptor.formatFileSize(fd.getFileSize()),
                                fd.getOwnerUsername(),
                                fd.getOwnerIp() + ":" + fd.getOwnerP2pPort(),
                                "Tải xuống (" + fd.getFileName() + ")"
                        });
                    }
                }
            }
        });
    }

    private void sendDirectMessage() {
        if (!isConnected) {
            JOptionPane.showMessageDialog(this, "Hãy kết nối vào mạng P2P trước.", "Cảnh báo", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (currentChatPeer == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một Peer từ danh sách Online.", "Cảnh báo", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String text = chatInputField.getText().trim();
        if (text.isEmpty()) return;

        String endpointStr = onlinePeersMap.get(currentChatPeer);
        if (endpointStr == null) {
            JOptionPane.showMessageDialog(this, "Không tìm thấy địa chỉ kết nối của Peer.", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String[] parts = endpointStr.split(":");
        String targetIp = parts[0];
        int targetPort = Integer.parseInt(parts[1]);

        appendChatLog("Tôi -> " + currentChatPeer, text);
        chatInputField.setText("");

        p2pClientManager.sendDirectChatMessageAsync(targetIp, targetPort, myUsername, text, new P2PClientManager.ChatCallback() {
            @Override
            public void onSuccess() {}

            @Override
            public void onFailure(String error) {
                appendChatLog("Lỗi Hệ Thống", "Không thể gửi tin nhắn P2P tới " + currentChatPeer + ": " + error);
            }
        });
    }

    private void performSearch() {
        if (!isConnected || serverOut == null) {
            JOptionPane.showMessageDialog(this, "Chưa kết nối đến máy chủ trung tâm.", "Cảnh báo", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String query = searchField.getText().trim();
        serverOut.println(MessageProtocol.buildMessage(MessageProtocol.CMD_SEARCH_FILES, query));
    }

    private void onDownloadButtonClicked(int row) {
        if (row < 0 || row >= searchResultsTableModel.getRowCount()) return;

        String fileName = (String) searchResultsTableModel.getValueAt(row, 0);
        String ownerUser = (String) searchResultsTableModel.getValueAt(row, 2);
        String endpoint = (String) searchResultsTableModel.getValueAt(row, 3);

        String[] parts = endpoint.split(":");
        String targetIp = parts[0];
        int targetPort = Integer.parseInt(parts[1]);

        int downloadRow = downloadsTableModel.getRowCount();
        downloadsTableModel.addRow(new Object[]{fileName, searchResultsTableModel.getValueAt(row, 1), 0, "0.0", "Đang tải..."});
        downloadRowMap.put(fileName, downloadRow);

        p2pClientManager.downloadFileAsync(targetIp, targetPort, myUsername, fileName, myDownloadsFolder, new P2PClientManager.DownloadProgressListener() {
            @Override
            public void onProgressUpdate(String fName, long bytesDownloaded, long totalBytes, double speedKBps) {
                SwingUtilities.invokeLater(() -> {
                    Integer r = downloadRowMap.get(fName);
                    if (r != null) {
                        int percent = (int) ((bytesDownloaded * 100.0) / totalBytes);
                        downloadsTableModel.setValueAt(percent, r, 2);
                        downloadsTableModel.setValueAt(String.format("%.1f", speedKBps), r, 3);
                        downloadsTableModel.setValueAt("Đang tải (" + percent + "%)", r, 4);
                    }
                });
            }

            @Override
            public void onDownloadComplete(String fName, File savedFile) {
                SwingUtilities.invokeLater(() -> {
                    Integer r = downloadRowMap.get(fName);
                    if (r != null) {
                        downloadsTableModel.setValueAt(100, r, 2);
                        downloadsTableModel.setValueAt("0.0", r, 3);
                        downloadsTableModel.setValueAt("HOÀN THÀNH", r, 4);
                    }
                    JOptionPane.showMessageDialog(MainGUI.this, "Đã tải xong file P2P: " + savedFile.getAbsolutePath(), "Tải File Thành Công", JOptionPane.INFORMATION_MESSAGE);
                    try {
                        Files.copy(savedFile.toPath(), new File(mySharedFolder, fName).toPath(), StandardCopyOption.REPLACE_EXISTING);
                        refreshAndPublishSharedFiles();
                    } catch (IOException ignored) {}
                });
            }

            @Override
            public void onDownloadFailed(String fName, String reason) {
                SwingUtilities.invokeLater(() -> {
                    Integer r = downloadRowMap.get(fName);
                    if (r != null) {
                        downloadsTableModel.setValueAt(0, r, 2);
                        downloadsTableModel.setValueAt("0.0", r, 3);
                        downloadsTableModel.setValueAt("THẤT BẠI: " + reason, r, 4);
                    }
                    JOptionPane.showMessageDialog(MainGUI.this, "Lỗi tải file " + fName + ": " + reason, "Lỗi Tải File P2P", JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }

    public synchronized void refreshAndPublishSharedFiles() {
        List<FileDescriptor> localFiles = sharedFileManager.scanSharedFiles(myUsername, "127.0.0.1", myP2pPort);

        SwingUtilities.invokeLater(() -> {
            localSharedFilesModel.clear();
            for (FileDescriptor fd : localFiles) {
                localSharedFilesModel.addElement(fd.getFileName() + " (" + FileDescriptor.formatFileSize(fd.getFileSize()) + ")");
            }
        });

        if (isConnected && serverOut != null) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < localFiles.size(); i++) {
                if (i > 0) sb.append("#");
                sb.append(localFiles.get(i).toProtocolString());
            }
            serverOut.println(MessageProtocol.buildMessage(MessageProtocol.CMD_UPDATE_FILES, sb.toString()));
        }
    }

    private void chooseSharedFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setSelectedFile(mySharedFolder);
        int ret = chooser.showOpenDialog(this);
        if (ret == JFileChooser.APPROVE_OPTION) {
            mySharedFolder = chooser.getSelectedFile();
            sharedFileManager.setSharedDirectory(mySharedFolder);
            sharedFolderLabel.setText("Thư mục Chia sẻ: " + mySharedFolder.getAbsolutePath());
            refreshAndPublishSharedFiles();
        }
    }

    private synchronized void disconnect() {
        isConnected = false;
        if (serverOut != null) {
            serverOut.println(MessageProtocol.buildMessage(MessageProtocol.CMD_LOGOUT));
        }
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}

        if (p2pServer != null) {
            p2pServer.stop();
        }

        connectButton.setText("Kết nối Mạng P2P");
        connectButton.setBackground(new Color(41, 128, 185));
        connectButton.setForeground(Color.BLACK);
        statusLabel.setText("Trạng thái: OFFLINE");
        statusLabel.setForeground(Color.RED);
        disableHeaderInputs(false);

        peerListModel.clear();
        onlinePeersMap.clear();
        searchResultsTableModel.setRowCount(0);

        appendChatLog("Hệ thống", "Đã ngắt kết nối.");
    }

    private void disableHeaderInputs(boolean disable) {
        serverHostField.setEnabled(!disable);
        serverPortField.setEnabled(!disable);
        usernameField.setEnabled(!disable);
        p2pPortField.setEnabled(!disable);
    }

    public void appendChatLog(String sender, String message) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
            String formatted = "[" + timestamp + "] " + sender + ": " + message + "\n";
            chatTranscriptPane.setText(chatTranscriptPane.getText() + formatted);
            chatTranscriptPane.setCaretPosition(chatTranscriptPane.getDocument().getLength());
        });
    }

    @Override
    public void onDirectMessageReceived(String senderUsername, String message, String senderIp) {
        appendChatLog("P2P Trực tiếp từ " + senderUsername, message);
    }

    @Override
    public void onFileTransferStarted(String requesterUsername, String fileName) {
        appendChatLog("Hệ thống P2P", "Peer '" + requesterUsername + "' đang tải trực tiếp file '" + fileName + "' từ máy của bạn.");
    }

    @Override
    public void onFileTransferCompleted(String requesterUsername, String fileName, long bytesSent) {
        appendChatLog("Hệ thống P2P", "Đã truyền xong file '" + fileName + "' (" + FileDescriptor.formatFileSize(bytesSent) + ") cho Peer '" + requesterUsername + "'.");
    }

    @Override
    public void onFileTransferFailed(String requesterUsername, String fileName, String reason) {
        appendChatLog("Lỗi P2P", "Truyền file '" + fileName + "' tới '" + requesterUsername + "' thất bại: " + reason);
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            MainGUI gui = new MainGUI();
            gui.setVisible(true);
        });
    }

    // --- Class tùy chỉnh Hiển thị Nút Tải về trong Bảng Swing (Màu chữ đen rõ nét) ---
    static class ButtonRenderer extends JButton implements javax.swing.table.TableCellRenderer {
        public ButtonRenderer() {
            setOpaque(true);
            setBackground(new Color(46, 204, 113));
            setForeground(Color.BLACK);
            setFocusPainted(false);
            setFont(getFont().deriveFont(Font.BOLD));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            setText((value == null) ? "Tải xuống" : value.toString());
            setForeground(Color.BLACK);
            return this;
        }
    }

    static class ButtonEditor extends DefaultCellEditor {
        private final JButton button;
        private String label;
        private boolean isPushed;
        private final java.util.function.Consumer<Integer> clickConsumer;
        private int currentRow;

        public ButtonEditor(JCheckBox checkBox, java.util.function.Consumer<Integer> clickConsumer) {
            super(checkBox);
            this.clickConsumer = clickConsumer;
            button = new JButton();
            button.setOpaque(true);
            button.setBackground(new Color(46, 204, 113));
            button.setForeground(Color.BLACK);
            button.setFont(button.getFont().deriveFont(Font.BOLD));
            button.addActionListener(e -> fireEditingStopped());
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            label = (value == null) ? "Tải xuống" : value.toString();
            button.setText(label);
            button.setForeground(Color.BLACK);
            isPushed = true;
            currentRow = row;
            return button;
        }

        @Override
        public Object getCellEditorValue() {
            if (isPushed && clickConsumer != null) {
                clickConsumer.accept(currentRow);
            }
            isPushed = false;
            return label;
        }

        @Override
        public boolean stopCellEditing() {
            isPushed = false;
            return super.stopCellEditing();
        }
    }

    static class ProgressCellRenderer extends JProgressBar implements javax.swing.table.TableCellRenderer {
        public ProgressCellRenderer() {
            setStringPainted(true);
            setForeground(new Color(41, 128, 185));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            int progress = 0;
            if (value instanceof Number) {
                progress = ((Number) value).intValue();
            }
            setValue(progress);
            return this;
        }
    }
}
