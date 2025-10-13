import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Gestiona todas las interacciones con la base de datos.
 */
public class DatabaseService {

    private static final String DB_URL = "jdbc:postgresql://localhost:5432/chatdb";
    private static final String DB_USER = "postgres"; // Cambiar por tu usuario
    private static final String DB_PASSWORD = "Yorick13"; // Cambiar por tu contraseña

    public DatabaseService() {
    }

    /**
     * Verifica si las credenciales de un usuario son correctas
     */
    public boolean isValidUser(String username, String password) {
        // --- LÓGICA JDBC VA AQUÍ ---
        // 1. Conectar a la BD
        // 2. Preparar un SELECT para buscar el usuario
        // 3. Comparar la contraseña (hasheada)
        // 4. Devolver true si coincide, false si no.
        System.out.println("ADVERTENCIA: La validación de contraseña no está implementada. Aceptando cualquier login.");
        return true; // Placeholder para desarrollo
    }

    /**
     * Registra un nuevo usuario en la base de datos.
     */
    public boolean registerUser(String username, String password) {
        // --- LÓGICA JDBC VA AQUÍ ---
        // 1. Conectar a la BD
        // 2. Verificar si el usuario ya existe con un SELECT
        // 3. Si no existe, hacer un INSERT con el nuevo usuario y la contraseña (hasheada)
        // 4. Devolver true si fue exitoso, false si el usuario ya existía.
        System.out.println("ADVERTENCIA: El registro de usuario no está implementado.");
        return true; // Placeholder para desarrollo
    }
}
