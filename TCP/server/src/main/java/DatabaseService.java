import org.mindrot.jbcrypt.BCrypt;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseService {
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/chatdb";
    private static final String DB_USER = "chatuser";
    private static final String DB_PASSWORD = "chatpassword";

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    public boolean isValidUser(String username, String password) {
        String sql = "SELECT password_hash FROM users WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String storedHash = rs.getString("password_hash");
                    return BCrypt.checkpw(password, storedHash);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error de base de datos al verificar usuario: " + e.getMessage());
        }
        return false;
    }

    public boolean registerUser(String username, String password) {
        String checkUserSql = "SELECT id FROM users WHERE username = ?";
        String insertUserSql = "INSERT INTO users(username, password_hash) VALUES(?, ?)";

        try (Connection conn = getConnection()) {
            // Verificar si el usuario ya existe
            try (PreparedStatement checkStmt = conn.prepareStatement(checkUserSql)) {
                checkStmt.setString(1, username);
                if (checkStmt.executeQuery().next()) {
                    return false; // El usuario ya existe
                }
            }

            // Insertar el nuevo usuario
            try (PreparedStatement insertStmt = conn.prepareStatement(insertUserSql)) {
                String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
                insertStmt.setString(1, username);
                insertStmt.setString(2, hashedPassword);
                int affectedRows = insertStmt.executeUpdate();
                return affectedRows > 0;
            }
        } catch (SQLException e) {
            System.err.println("Error de base de datos al registrar usuario: " + e.getMessage());
            return false;
        }
    }

    public void savePublicMessage(String sender, String message) {
        String sql = "INSERT INTO public_messages(sender_username, message_text) VALUES(?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, sender);
            pstmt.setString(2, message);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error al guardar mensaje público: " + e.getMessage());
        }
    }
    
    public List<String> getPublicHistory(int limit) {
        List<String> history = new ArrayList<>();
        String sql = "SELECT sender_username, message_text, sent_at FROM public_messages ORDER BY sent_at DESC LIMIT ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, limit);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String sender = rs.getString("sender_username");
                String message = rs.getString("message_text");
                history.add(0, String.format("[%s]: %s", sender, message)); // Añadir al principio para orden cronológico
            }
        } catch (SQLException e) {
            System.err.println("Error al obtener historial de mensajes: " + e.getMessage());
        }
        return history;
    }
    
    public GroupCreationResult createGroup(String groupName, String ownerUsername) {
        String checkGroupSql = "SELECT group_name FROM chat_groups WHERE group_name = ?";
        String createGroupSql = "INSERT INTO chat_groups(group_name) VALUES(?)";
        String addMemberSql = "INSERT INTO group_members(group_name, username) VALUES(?, ?)";
    
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false); // Iniciar transacción
    
            // 1. Verificar si el grupo ya existe
            try (PreparedStatement checkStmt = conn.prepareStatement(checkGroupSql)) {
                checkStmt.setString(1, groupName);
                if (checkStmt.executeQuery().next()) {
                    return GroupCreationResult.ALREADY_EXISTS;
                }
            }
    
            // 2. Crear el grupo
            try (PreparedStatement createStmt = conn.prepareStatement(createGroupSql)) {
                createStmt.setString(1, groupName);
                createStmt.executeUpdate();
            }
    
            // 3. Añadir al dueño como primer miembro
            try (PreparedStatement addStmt = conn.prepareStatement(addMemberSql)) {
                addStmt.setString(1, groupName);
                addStmt.setString(2, ownerUsername);
                addStmt.executeUpdate();
            }
    
            conn.commit(); // Confirmar transacción
            return GroupCreationResult.SUCCESS;
    
        } catch (SQLException e) {
            System.err.println("Error en transacción de crear grupo: " + e.getMessage());
            return GroupCreationResult.DB_ERROR;
        }
    }
    
    public boolean isUserMemberOfGroup(String username, String groupName) {
        String sql = "SELECT 1 FROM group_members WHERE username = ? AND group_name = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, groupName);
            return pstmt.executeQuery().next();
        } catch (SQLException e) {
            System.err.println("Error al verificar membresía de grupo: " + e.getMessage());
            return false;
        }
    }
    
    public boolean addUserToGroup(String username, String groupName) {
        // Asume que la verificación de que el invitador es miembro se hace en el Controller
        String sql = "INSERT INTO group_members(group_name, username) VALUES(?, ?) ON CONFLICT DO NOTHING";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, groupName);
            pstmt.setString(2, username);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error al añadir usuario a grupo: " + e.getMessage());
            return false;
        }
    }

    // --- NUEVOS MÉTODOS PARA HISTORIAL ---

    public void saveGroupMessage(String groupName, String sender, String message) {
        String sql = "INSERT INTO group_messages(group_name, sender_username, message_text) VALUES(?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, groupName);
            pstmt.setString(2, sender);
            pstmt.setString(3, message);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error al guardar mensaje de grupo: " + e.getMessage());
        }
    }
    
    public List<String> getGroupHistory(String groupName, int limit) {
        List<String> history = new ArrayList<>();
        String sql = "SELECT sender_username, message_text FROM group_messages WHERE group_name = ? ORDER BY sent_at DESC LIMIT ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, groupName);
            pstmt.setInt(2, limit);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                history.add(0, String.format("[%s]: %s", rs.getString("sender_username"), rs.getString("message_text")));
            }
        } catch (SQLException e) {
            System.err.println("Error al obtener historial de grupo: " + e.getMessage());
        }
        return history;
    }

    public void savePrivateMessage(String sender, String recipient, String message) {
        String sql = "INSERT INTO private_messages(sender_username, recipient_username, message_text) VALUES(?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, sender);
            pstmt.setString(2, recipient);
            pstmt.setString(3, message);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error al guardar mensaje privado: " + e.getMessage());
        }
    }

    public List<String> getPrivateHistory(String user1, String user2, int limit) {
        List<String> history = new ArrayList<>();
        String sql = "SELECT sender_username, message_text FROM private_messages " +
                     "WHERE (sender_username = ? AND recipient_username = ?) OR (sender_username = ? AND recipient_username = ?) " +
                     "ORDER BY sent_at DESC LIMIT ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, user1);
            pstmt.setString(2, user2);
            pstmt.setString(3, user2);
            pstmt.setString(4, user1);
            pstmt.setInt(5, limit);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                history.add(0, String.format("[%s]: %s", rs.getString("sender_username"), rs.getString("message_text")));
            }
        } catch (SQLException e) {
            System.err.println("Error al obtener historial privado: " + e.getMessage());
        }
        return history;
    }
}