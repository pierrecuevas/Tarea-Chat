import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.io.*;
import java.net.Socket;
import java.util.List;

public class ClientHandler implements Runnable {

    private final Socket clientSocket;
    private final ChatController chatController;
    private final Gson gson;
    private PrintWriter out;
    private BufferedReader in;
    private String username;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
        this.chatController = ChatController.getInstance();
        this.gson = new Gson();
    }

    @Override
    public void run() {
        try {
            this.in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            this.out = new PrintWriter(clientSocket.getOutputStream(), true);

            if (handleAuthentication()) {
                String jsonMessage;
                while ((jsonMessage = in.readLine()) != null) {
                    processMessage(jsonMessage);
                }
            }
        } catch (IOException e) {
            System.out.println("Cliente desconectado: " + (username != null ? username : clientSocket.getRemoteSocketAddress()));
        } finally {
            if (this.username != null) {
                chatController.userLogout(this.username);
            }
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean handleAuthentication() throws IOException {
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
                        sendInitialHistory();
                        return true;
                    } else {
                        sendMessage("{\"status\": \"error\", \"message\": \"Credenciales incorrectas o usuario ya conectado.\"}");
                    }
                } else if ("register".equals(command)) {
                    if (chatController.registerUser(user, pass) && chatController.loginUser(user, pass, this)) {
                        sendMessage("{\"status\": \"ok\", \"message\": \"Registro y login exitosos. ¡Bienvenido!\"}");
                        sendInitialHistory();
                        return true;
                    } else {
                        sendMessage("{\"status\": \"error\", \"message\": \"El nombre de usuario ya existe.\"}");
                    }
                }
            } catch (JsonSyntaxException | NullPointerException e) {
                 sendMessage("{\"status\": \"error\", \"message\": \"Petición de autenticación mal formada.\"}");
            }
        }
        return false;
    }

    private void sendInitialHistory() {
        sendMessage(chatController.createNotification("--- Últimos 10 mensajes del chat general ---"));
        chatController.getPublicChatHistory(10).forEach(this::sendMessage);
        sendMessage(chatController.createNotification("------------------------------------"));
    }

    private void processMessage(String jsonMessage) {
        try {
            JsonObject message = gson.fromJson(jsonMessage, JsonObject.class);
            String command = message.get("command").getAsString();

            // Handle file transfers separately as they consume the input stream
            if ("send_audio".equals(command)) {
                handleAudioUpload(message);
                return;
            } else if ("request_audio".equals(command)) {
                handleAudioRequest(message);
                return;
            }

            switch (command) {
                case "public_message":
                    chatController.processPublicMessage(this.username, message.get("text").getAsString());
                    break;
                case "private_message":
                    chatController.processPrivateMessage(this.username, message.get("recipient").getAsString(), message.get("text").getAsString());
                    break;
                case "group_message":
                    chatController.processGroupMessage(this.username, message.get("group_name").getAsString(), message.get("text").getAsString());
                    break;
                case "create_group":
                    chatController.createGroup(this.username, message.get("group_name").getAsString());
                    break;
                case "invite_to_group":
                    chatController.inviteToGroup(this.username, message.get("group_name").getAsString(), message.get("user_to_invite").getAsString());
                    break;
                case "leave_group":
                    chatController.leaveGroup(this.username, message.get("group_name").getAsString());
                    break;
                case "get_group_history":
                    handleGroupHistoryRequest(message);
                    break;
                case "get_private_history":
                    handlePrivateHistoryRequest(message);
                    break;
                default:
                    sendMessage(chatController.createNotification("Comando desconocido."));
                    break;
            }
        } catch (JsonSyntaxException | NullPointerException | IOException e) {
            System.err.println("Error procesando mensaje de " + username + ": " + e.getMessage());
        }
    }

    private void handleAudioUpload(JsonObject message) {
        try {
            long fileSize = message.get("file_size").getAsLong();
            String savedFilePath = chatController.saveAudioFile(this.username, message.get("file_name").getAsString(), clientSocket.getInputStream(), fileSize);
            if (savedFilePath != null) {
                chatController.processAudioMessage(this.username, message.get("recipient").getAsString(), savedFilePath);
            } else {
                sendMessage(chatController.createNotification("Error al transferir el archivo de audio."));
            }
        } catch (IOException e) {
            System.err.println("Error durante la subida de audio de " + this.username + ": " + e.getMessage());
        }
    }

    private void handleAudioRequest(JsonObject message) throws IOException {
        String fileName = message.get("file_name").getAsString();
        File audioFile = chatController.getAudioFile(fileName);

        if (audioFile != null && audioFile.exists()) {
            JsonObject response = new JsonObject();
            response.addProperty("type", "audio_transfer");
            response.addProperty("file_name", fileName);
            response.addProperty("file_size", audioFile.length());
            sendMessage(gson.toJson(response));

            try (FileInputStream fis = new FileInputStream(audioFile)) {
                OutputStream socketOutStream = clientSocket.getOutputStream();
                fis.transferTo(socketOutStream);
                socketOutStream.flush();
                System.out.println("Archivo " + fileName + " enviado a " + this.username);
            }
        } else {
            sendMessage(chatController.createNotification("El archivo de audio '" + fileName + "' no se encontró en el servidor."));
        }
    }

    private void handleGroupHistoryRequest(JsonObject message) {
        String groupName = message.get("group_name").getAsString();
        sendMessage(chatController.createNotification("--- Últimos 15 mensajes de " + groupName + " ---"));
        chatController.getGroupChatHistory(groupName, 15).forEach(this::sendMessage);
    }

    private void handlePrivateHistoryRequest(JsonObject message) {
        String withUser = message.get("with_user").getAsString();
        sendMessage(chatController.createNotification("--- Tu historial privado con " + withUser + " ---"));
        chatController.getPrivateChatHistory(this.username, withUser, 15).forEach(this::sendMessage);
    }

    public void sendMessage(String message) {
        out.println(message);
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}

