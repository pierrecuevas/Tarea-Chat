import org.mindrot.jbcrypt.BCrypt;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class DatabaseService {

    private static final String DB_URL = "jdbc:postgresql://localhost:5432/chatdb";
    private static final String DB_USER = "chatuser";
    private static final String DB_PASSWORD = "chatpassword";

    public enum GroupCreationResult {
        SUCCESS,
        ALREADY_EXISTS,
        DB_ERROR
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    public boolean doesUserExist(String username) {
        String sql = "SELECT 1 FROM users WHERE username = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            return pstmt.executeQuery().next();
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean isValidUser(String username, String password) {
        String sql = "SELECT password_hash FROM users WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String storedHash = rs.getString("password_hash");
                return BCrypt.checkpw(password, storedHash);
            }
        } catch (SQLException e) {
            System.err.println("Error de base de datos al verificar usuario: " + e.getMessage());
        }
        return false;
    }

    public boolean registerUser(String username, String password) {
        String sql = "INSERT INTO users (username, password_hash) VALUES (?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
            pstmt.setString(1, username);
            pstmt.setString(2, hashedPassword);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public void savePublicMessage(String sender, String text) {
        String sql = "INSERT INTO public_messages (sender_username, message_content, message_type) VALUES (?, ?, 'TEXT')";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, sender);
            pstmt.setString(2, text);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error al guardar mensaje público: " + e.getMessage());
        }
    }
    
    public List<String> getPublicMessages(int limit) {
        List<String> messages = new ArrayList<>();
        String sql = "SELECT sender_username, message_content, message_type, sent_at FROM public_messages ORDER BY sent_at DESC LIMIT ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, limit);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String sender = rs.getString("sender_username");
                String content = rs.getString("message_content");
                String type = rs.getString("message_type");
                String sentAt = rs.getTimestamp("sent_at").toString();
                if ("AUDIO".equals(type)) {
                     messages.add(createChatMessage("public_audio", sender, sender, new java.io.File(content).getName(), null, sentAt));
                } else {
                     messages.add(createChatMessage("public", sender, sender, content, null, sentAt));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error al obtener historial público: " + e.getMessage());
        }
        return messages;
    }

    public GroupCreationResult createGroup(String groupName, String ownerUsername) {
        String checkSql = "SELECT 1 FROM chat_groups WHERE group_name = ?";
        String createSql = "INSERT INTO chat_groups (group_name) VALUES (?)";
        String addOwnerSql = "INSERT INTO group_members (group_name, username) VALUES (?, ?)";
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, groupName);
                if (checkStmt.executeQuery().next()) {
                    conn.rollback();
                    return GroupCreationResult.ALREADY_EXISTS;
                }
            }
            try (PreparedStatement createStmt = conn.prepareStatement(createSql)) {
                createStmt.setString(1, groupName);
                createStmt.executeUpdate();
            }
            try (PreparedStatement addOwnerStmt = conn.prepareStatement(addOwnerSql)) {
                addOwnerStmt.setString(1, groupName);
                addOwnerStmt.setString(2, ownerUsername);
                addOwnerStmt.executeUpdate();
            }
            conn.commit();
            return GroupCreationResult.SUCCESS;
        } catch (SQLException e) {
            System.err.println("Error de BD al crear grupo: " + e.getMessage());
            return GroupCreationResult.DB_ERROR;
        }
    }

    public boolean addUserToGroup(String username, String groupName) {
        String sql = "INSERT INTO group_members (username, group_name) VALUES (?, ?)";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, groupName);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }
    
    public boolean removeUserFromGroup(String username, String groupName) {
        String sql = "DELETE FROM group_members WHERE username = ? AND group_name = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, groupName);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean isUserInGroup(String username, String groupName) {
        String sql = "SELECT 1 FROM group_members WHERE username = ? AND group_name = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, groupName);
            return pstmt.executeQuery().next();
        } catch (SQLException e) {
            return false;
        }
    }
    
    public boolean isGroup(String name) {
        String sql = "SELECT 1 FROM chat_groups WHERE group_name = ?";
         try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            return pstmt.executeQuery().next();
        } catch (SQLException e) {
            return false;
        }
    }
    
    public List<String> getGroupMembers(String groupName) {
        List<String> members = new ArrayList<>();
        String sql = "SELECT username FROM group_members WHERE group_name = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, groupName);
            ResultSet rs = pstmt.executeQuery();
            while(rs.next()) {
                members.add(rs.getString("username"));
            }
        } catch (SQLException e) {
            System.err.println("Error al obtener miembros del grupo: " + e.getMessage());
        }
        return members;
    }
    
    public void saveGroupMessage(String sender, String groupName, String content, String type) {
        String sql = "INSERT INTO group_messages (sender_username, group_name, message_content, message_type) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, sender);
            pstmt.setString(2, groupName);
            pstmt.setString(3, content);
            pstmt.setString(4, type);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error al guardar mensaje de grupo: " + e.getMessage());
        }
    }

    public List<String> getGroupMessages(String groupName, int limit) {
        List<String> messages = new ArrayList<>();
        String sql = "SELECT sender_username, message_content, message_type, sent_at FROM group_messages WHERE group_name = ? ORDER BY sent_at ASC LIMIT ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, groupName);
            pstmt.setInt(2, limit);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String sender = rs.getString("sender_username");
                String content = rs.getString("message_content");
                String type = rs.getString("message_type");
                String sentAt = rs.getTimestamp("sent_at").toString();
                if ("AUDIO".equals(type)) {
                    messages.add(createChatMessage("group_audio", sender, null, new java.io.File(content).getName(), groupName, sentAt));
                } else {
                    messages.add(createChatMessage("group", sender, null, content, groupName, sentAt));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error al obtener historial de grupo: " + e.getMessage());
        }
        return messages;
    }
    
    public void savePrivateMessage(String sender, String recipient, String content, String type) {
        String sql = "INSERT INTO private_messages (sender_username, recipient_username, message_content, message_type) VALUES (?, ?, ?, ?)";
         try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, sender);
            pstmt.setString(2, recipient);
            pstmt.setString(3, content);
            pstmt.setString(4, type);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error al guardar mensaje privado: " + e.getMessage());
        }
    }

    public List<String> getPrivateMessages(String user1, String user2, int limit) {
        List<String> messages = new ArrayList<>();
        String sql = "SELECT sender_username, recipient_username, message_content, message_type, sent_at FROM private_messages WHERE (sender_username = ? AND recipient_username = ?) OR (sender_username = ? AND recipient_username = ?) ORDER BY sent_at ASC LIMIT ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, user1);
            pstmt.setString(2, user2);
            pstmt.setString(3, user2);
            pstmt.setString(4, user1);
            pstmt.setInt(5, limit);
            ResultSet rs = pstmt.executeQuery();
            while(rs.next()){
                String sender = rs.getString("sender_username");
                String recipient = rs.getString("recipient_username");
                String content = rs.getString("message_content");
                String type = rs.getString("message_type");
                String sentAt = rs.getTimestamp("sent_at").toString();
                String subType = sender.equals(user1) ? "private_to" : "private_from";
                String party = sender.equals(user1) ? recipient : sender;
                if ("AUDIO".equals(type)) {
                    messages.add(createChatMessage(subType + "_audio", sender, party, new java.io.File(content).getName(), null, sentAt));
                } else {
                    messages.add(createChatMessage(subType, sender, party, content, null, sentAt));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error al obtener historial privado: " + e.getMessage());
        }
        return messages;
    }


    public List<String> getPrivateChatPartners(String username) {
        List<String> partners = new ArrayList<>();
        String sql = "SELECT DISTINCT recipient_username FROM private_messages WHERE sender_username = ? " +
                     "UNION " +
                     "SELECT DISTINCT sender_username FROM private_messages WHERE recipient_username = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, username);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                partners.add(rs.getString(1));
            }
        } catch (SQLException e) {
            System.err.println("Error al obtener compañeros de chat privado: " + e.getMessage());
        }
        return partners;
    }

    public List<String> getUserGroups(String username) {
        List<String> groups = new ArrayList<>();
        String sql = "SELECT group_name FROM group_members WHERE username = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                groups.add(rs.getString("group_name"));
            }
        } catch (SQLException e) {
            System.err.println("Error al obtener los grupos del usuario: " + e.getMessage());
        }
        return groups;
    }

    private String createChatMessage(String subType, String sender, String party, String text, String group, String sentAt) {
        Gson gson = new Gson();
        JsonObject json = new JsonObject();
        json.addProperty("type", "chat");
        json.addProperty("sub_type", subType);
        json.addProperty("sender", sender);
        json.addProperty("party", party);
        json.addProperty("text", text);
        if (group != null) {
            json.addProperty("group", group);
        }
        if (sentAt != null) {
            json.addProperty("sent_at", sentAt);
        }
        return gson.toJson(json);
    }
    
    // Método sobrecargado para compatibilidad
    private String createChatMessage(String subType, String sender, String party, String text, String group) {
        return createChatMessage(subType, sender, party, text, group, null);
    }
    
    public List<String> getAllUsers() {
        List<String> users = new ArrayList<>();
        String sql = "SELECT username FROM users ORDER BY username";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                users.add(rs.getString("username"));
            }
        } catch (SQLException e) {
            System.err.println("Error al obtener todos los usuarios: " + e.getMessage());
        }
        return users;
    }
}

