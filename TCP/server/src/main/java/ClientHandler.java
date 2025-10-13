import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;
    private String username;
    private final Gson gson = new Gson();
    private final ChatController controller;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
        this.controller = ChatController.getInstance();
    }

    @Override
    public void run() {
        try {
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            // Bucle para manejar login/registro hasta que sea exitoso
            while (this.username == null) {
                out.println("{\"status\": \"auth_required\", \"message\": \"Elige: login o register\"}");
                String authRequestJson = in.readLine();
                if (authRequestJson == null) return; // Cliente se desconectó
                
                handleAuth(authRequestJson);
            }

            // Bucle principal para procesar mensajes del chat una vez logueado
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                handleChatMessage(inputLine);
            }

        } catch (IOException e) {
            System.out.println("Conexión perdida con " + (username != null ? username : "un cliente"));
        } finally {
            controller.userLogout(this.username);
            try {
                if (!clientSocket.isClosed()) clientSocket.close();
            } catch (IOException e) { e.printStackTrace(); }
        }
    }

    private void handleAuth(String json) {
        try {
            JsonObject req = gson.fromJson(json, JsonObject.class);
            String command = req.get("command").getAsString();
            String user = req.get("username").getAsString();
            String pass = req.get("password").getAsString();

            if ("login".equals(command)) {
                if (controller.loginUser(user, pass, this)) {
                    out.println("{\"status\": \"ok\", \"message\": \"Login exitoso. ¡Bienvenido!\"}");
                } else {
                    out.println("{\"status\": \"error\", \"message\": \"Credenciales incorrectas o usuario ya conectado.\"}");
                }
            } else if ("register".equals(command)) {
                if (controller.registerUser(user, pass)) {
                    out.println("{\"status\": \"ok\", \"message\": \"Registro exitoso. Ahora puedes iniciar sesión.\"}");
                } else {
                    out.println("{\"status\": \"error\", \"message\": \"El nombre de usuario ya existe.\"}");
                }
            }
        } catch (JsonSyntaxException e) {
            sendMessage("{\"status\": \"error\", \"message\": \"Formato de autenticación inválido.\"}");
        }
    }

    private void handleChatMessage(String json) {
        try {
            JsonObject req = gson.fromJson(json, JsonObject.class);
            String command = req.get("command").getAsString();

            if ("public_msg".equals(command)) {
                String text = req.get("text").getAsString();
                controller.processPublicMessage(this.username, text);
            } else if ("private_msg".equals(command)) {
                String recipient = req.get("recipient").getAsString();
                String text = req.get("text").getAsString();
                controller.processPrivateMessage(this.username, recipient, text);
            }
            // Aquí irían más comandos: /create_group, etc.

        } catch (JsonSyntaxException e) {
            sendMessage("{\"status\": \"error\", \"message\": \"Formato de mensaje inválido.\"}");
        }
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void sendMessage(String message) {
        out.println(message);
    }
}

