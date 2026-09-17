package org.example.common;


public class MessageProtocol {
    // Ký tự phân cách chính giữa các tham số trong một câu lệnh
    public static final String DELIMITER = "|";

    // LỆNH GỬI TỪ PEER CLIENT -> CENTRAL DIRECTORY SERVER
    // Đăng ký Peer với Server: REGISTER|<username>|<p2pPort>
    public static final String CMD_REGISTER = "REGISTER";            
    public static final String CMD_UPDATE_FILES = "UPDATE_FILES";
    public static final String CMD_GET_PEERS = "GET_PEERS";
    public static final String CMD_SEARCH_FILES = "SEARCH_FILES";
    public static final String CMD_GET_ENDPOINT = "GET_ENDPOINT";
    public static final String CMD_LOGOUT = "LOGOUT";

    // PHẢN HỒI GỬI TỪ CENTRAL DIRECTORY SERVER -> PEER CLIENT
    public static final String RES_REGISTER_OK = "REGISTER_OK";
    public static final String RES_REGISTER_ERR = "REGISTER_ERR";
    public static final String RES_PEER_LIST = "PEER_LIST";
    public static final String RES_SEARCH_RESULTS = "SEARCH_RESULTS";
    public static final String RES_ENDPOINT = "ENDPOINT";
    public static final String RES_ERROR = "ERROR";

    // LỆNH TRUYỀN THÔNG TRỰC TIẾP GIỮA PEER <-> PEER (DIRECT P2P SOCKETS)
    public static final String P2P_CMD_CHAT = "P2P_CHAT";
    public static final String P2P_CMD_FILE_REQ = "P2P_FILE_REQ";
    public static final String P2P_RES_FILE_OK = "P2P_FILE_OK";
    public static final String P2P_RES_FILE_ERR = "P2P_FILE_ERR";

    // Hàm tiện ích ghép các token tham số thành một chuỗi lệnh hoàn chỉnh
    public static String buildMessage(String... tokens) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.length; i++) {
            if (i > 0) sb.append(DELIMITER);
            sb.append(tokens[i] != null ? tokens[i] : "");
        }
        return sb.toString();
    }
}
