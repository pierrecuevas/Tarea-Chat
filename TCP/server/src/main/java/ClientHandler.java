import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;

public class ClientHandler implements Runnable {

    private final Socket clientSocket;
    private final ChatController chatController;
    private final Gson gson;
    private PrintWriter out;
    private String username;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
        this.chatController = ChatController.getInstance();
        this.gson = new Gson();
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
            this.out = new PrintWriter(clientSocket.getOutputStream(), true);

            if (handleAuthentication(in)) {
                String jsonMessage;
                while ((jsonMessage = in.readLine()) != null) {
                    processMessage(jsonMessage);
                }
            }
        } catch (IOException e) {
            System.out.println("Cliente desconectado: " + (username != null ? username : clientSocket.getRemoteSocketAddress()));
        } finally {
            chatController.userLogout(this.username);
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean handleAuthentication(BufferedReader in) throws IOException {
        sendMessage("{\"status\": \"auth_required\", \"message\": \"Elige: login o register\"}");

        String authRequest;
        while ((authRequest = in.readLine()) != null) {
            try {
                JsonObject request = gson.fromJson(authRequest, JsonObject.class);
                String command = request.get("command").getAsString();
                String user = request.get("username").getAsString();
                String pass = request.get("password").getAsString();

                if ("login".equals(command)) {
                    if (chatController.loginUser(user, pass, this)) {
                        sendMessage("{\"status\": \"ok\", \"message\": \"Login exitoso. ¡Bienvenido!\"}");
                        sendPublicHistory();
                        return true;
                    } else {
                        sendMessage("{\"status\": \"error\", \"message\": \"Credenciales incorrectas o usuario ya conectado.\"}");
                    }
                } else if ("register".equals(command)) {
                    if (chatController.registerUser(user, pass)) {
                        // Iniciar sesión automáticamente después del registro
                        if (chatController.loginUser(user, pass, this)) {
                           sendMessage("{\"status\": \"ok\", \"message\": \"Registro exitoso. ¡Bienvenido!\"}");
                           sendPublicHistory();
                           return true;
                        }
                    } else {
                        sendMessage("{\"status\": \"error\", \"message\": \"El nombre de usuario ya existe.\"}");
                    }
                }
            } catch (JsonSyntaxException | NullPointerException e) {
                System.err.println("Error de sintaxis JSON en la autenticación: " + authRequest);
                sendMessage("{\"status\": \"error\", \"message\": \"Petición mal formada.\"}");
            }
        }
        return false;
    }
    
    private void sendPublicHistory() {
        sendMessage(createNotification("--- Últimos 10 mensajes del chat General ---"));
        List<String> history = chatController.getPublicChatHistory(10);
        for (String msg : history) {
            sendMessage(createChatMessage("history", "server", msg));
        }
        sendMessage(createNotification("------------------------------------"));
    }

    private void processMessage(String jsonMessage) {
        try {
            JsonObject message = gson.fromJson(jsonMessage, JsonObject.class);
            String command = message.get("command").getAsString();

            switch (command) {
                case "public_message":
                    chatController.processPublicMessage(this.username, message.get("text").getAsString());
                    break;
                case "private_message":
                    chatController.processPrivateMessage(this.username, message.get("recipient").getAsString(), message.get("text").getAsString());
                    break;
                case "create_group":
                    // CORREGIDO: Llamada al método correcto
                    chatController.createGroup(message.get("group_name").getAsString(), this.username);
                    break;
                case "invite_to_group":
                    // CORREGIDO: Llamada al método correcto
                    chatController.inviteToGroup(this.username, message.get("group_name").getAsString(), message.get("user_to_invite").getAsString());
                    break;
                case "group_message":
                    chatController.processGroupMessage(message.get("group_name").getAsString(), this.username, message.get("text").getAsString());
                    break;
                case "get_group_history":
                    chatController.sendGroupHistory(this.username, message.get("group_name").getAsString());
                    break;
                case "get_private_history":
                    chatController.sendPrivateHistory(this.username, message.get("with_user").getAsString());
                    break;
                default:
                    sendMessage(createNotification("Comando desconocido."));
                    break;
            }
        } catch (JsonSyntaxException | NullPointerException e) {
            System.err.println("Mensaje JSON mal formado recibido de " + username + ": " + jsonMessage);
        }
    }

    public void sendMessage(String message) {
        out.println(message);
    }
    
    private String createNotification(String message) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "notification");
        json.addProperty("message", message);
        return gson.toJson(json);
    }

    private String createChatMessage(String messageType, String sender, String text) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "chat");
        json.addProperty("sub_type", messageType);
        json.addProperty("sender", sender);
        json.addProperty("text", text);
        return gson.toJson(json);
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}