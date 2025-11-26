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
    private String username;

    public ClientHandler(Socket socket, CallManager callManager) {
        this.clientSocket = socket;
        this.chatController = ChatController.getInstance(callManager);
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

        } finally {
            if (this.username != null) {
                chatController.userLogout(this.username);
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
                        sendMessage("{\"status\": \"ok\", \"message\": \"Login exitoso.\"}");
                        chatController.sendInitialHistoryToUser(this);
                        return true;
                    } else {
                        sendMessage(
                                "{\"status\": \"error\", \"message\": \"Credenciales incorrectas o usuario ya conectado.\"}");
                    }
                } else if ("register".equals(command)) {
                    if (chatController.registerUser(user, pass)) {
                        if (chatController.loginUser(user, pass, this)) {
                            sendMessage("{\"status\": \"ok\", \"message\": \"Registro y login exitosos.\"}");
                            chatController.sendInitialHistoryToUser(this);
                            return true;
                        } else {
                            sendMessage(
                                    "{\"status\": \"error\", \"message\": \"Registro exitoso, pero el login automático falló. Intenta iniciar sesión manualmente.\"}");
                        }
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

    private void processMessage(String jsonMessage) {
        try {
            JsonObject message = gson.fromJson(jsonMessage, JsonObject.class);

            // Verificar que el mensaje tenga el campo "command"
            if (!message.has("command")) {
                System.err.println("Mensaje sin comando recibido: " + jsonMessage);
                return;
            }

            String command = message.get("command").getAsString();

            switch (command) {
                case "send_audio":
                    handleAudioUpload(message);
                    break;
                case "request_audio":
                    handleAudioRequest(message);
                    break;
                case "public_message":
                    chatController.processPublicMessage(this.username, message.get("text").getAsString());
                    break;
                case "private_message":
                    chatController.processPrivateMessage(this.username, message.get("recipient").getAsString(),
                            message.get("text").getAsString());
                    break;
                case "group_message":
                    chatController.processGroupMessage(this.username, message.get("group_name").getAsString(),
                            message.get("text").getAsString());
                    break;
                case "create_group":
                    chatController.createGroup(this.username, message.get("group_name").getAsString());
                    break;
                case "invite_to_group":
                    chatController.inviteToGroup(this.username, message.get("group_name").getAsString(),
                            message.get("user_to_invite").getAsString());
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
                case "get_chat_history":
                    handleChatHistoryRequest(message);
                    break;
                case "call_request":
                    chatController.requestCall(this.username, message.get("callee").getAsString());
                    break;
                case "call_accept":
                    chatController.acceptCall(this.username, message.get("requester").getAsString());
                    break;
                case "call_hangup":
                    chatController.endCall(this.username);
                    break;
                case "webrtc_offer":
                case "webrtc_answer":
                case "ice_candidate":
                    // Forward WebRTC signaling messages to recipient
                    handleWebRTCSignaling(message);
                    break;
                case "get_all_users":
                    handleGetAllUsers();
                    break;
                case "get_group_members":
                    handleGetGroupMembers(message);
                    break;
                default:
                    sendMessage(chatController.createNotification("Comando desconocido."));
                    break;
            }
        } catch (Exception e) {
            System.err.println("Error procesando mensaje de " + username + ": " + e.getMessage());
        }
    }

    private void handleAudioUpload(JsonObject message) {
        try {
            long fileSize = message.get("file_size").getAsLong();
            String fileName = message.get("file_name").getAsString();

            // Llamar al controlador para que lea los bytes del stream del socket
            String savedFilePath = chatController.saveAudioFile(this.username, fileName, clientSocket.getInputStream(),
                    fileSize);

            if (savedFilePath != null) {
                chatController.processAudioMessage(this.username, message.get("recipient").getAsString(),
                        savedFilePath);
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
            // 1. Enviar el JSON de "aviso"
            JsonObject response = new JsonObject();
            response.addProperty("type", "audio_transfer");
            response.addProperty("file_name", fileName);
            response.addProperty("file_size", audioFile.length());
            sendMessage(gson.toJson(response));

            // 2. Enviar los bytes del archivo justo después
            try (FileInputStream fis = new FileInputStream(audioFile)) {
                OutputStream socketOutStream = clientSocket.getOutputStream();
                fis.transferTo(socketOutStream);
                socketOutStream.flush();
            }
        } else {
            sendMessage(chatController
                    .createNotification("El archivo de audio '" + fileName + "' no se encontró en el servidor."));
        }
    }

    private void handleGroupHistoryRequest(JsonObject message) {
        String groupName = message.get("group_name").getAsString();
        sendMessage(chatController.createNotification("--- Últimos 15 mensajes de " + groupName + " ---"));
        chatController.getGroupChatHistory(groupName, 15, 0).forEach(this::sendMessage);
    }

    private void handlePrivateHistoryRequest(JsonObject message) {
        String withUser = message.get("with_user").getAsString();
        sendMessage(chatController.createNotification("--- Tu historial privado con " + withUser + " ---"));
        chatController.getPrivateChatHistory(this.username, withUser, 15, 0).forEach(this::sendMessage);
    }

    private void handleChatHistoryRequest(JsonObject message) {
        String type = message.get("type").getAsString();
        String name = message.get("name").getAsString();
        int limit = message.has("limit") ? message.get("limit").getAsInt() : 50;
        int offset = message.has("offset") ? message.get("offset").getAsInt() : 0;

        List<String> messages;
        if ("general".equals(type) || "public".equals(type)) {
            messages = chatController.getPublicChatHistory(limit, offset);
        } else if ("private".equals(type)) {
            messages = chatController.getPrivateChatHistory(this.username, name, limit, offset);
        } else if ("group".equals(type)) {
            messages = chatController.getGroupChatHistory(name, limit, offset);
        } else {
            sendMessage(chatController.createNotification("Tipo de chat inválido."));
            return;
        }

        // Enviar los mensajes como un array JSON
        JsonObject response = new JsonObject();
        response.addProperty("type", "chat_history_response");
        response.addProperty("chat_type", type);
        response.addProperty("chat_name", name);
        com.google.gson.JsonArray messagesArray = new com.google.gson.JsonArray();
        for (String msg : messages) {
            try {
                messagesArray.add(gson.fromJson(msg, JsonObject.class));
            } catch (Exception e) {
                // Si no es JSON válido, crear un objeto de notificación
                JsonObject notif = new JsonObject();
                notif.addProperty("type", "notification");
                notif.addProperty("message", msg);
                messagesArray.add(notif);
            }
        }
        response.add("messages", messagesArray);
        sendMessage(gson.toJson(response));
    }

    private void handleGetAllUsers() {
        List<String> users = chatController.getAllUsers();
        JsonObject response = new JsonObject();
        response.addProperty("type", "all_users");
        com.google.gson.JsonArray usersArray = new com.google.gson.JsonArray();
        for (String user : users) {
            usersArray.add(user);
        }
        response.add("users", usersArray);
        sendMessage(gson.toJson(response));
    }

    private void handleGetGroupMembers(JsonObject message) {
        String groupName = message.get("group_name").getAsString();
        List<String> members = chatController.getGroupMembers(groupName);
        JsonObject response = new JsonObject();
        response.addProperty("type", "group_members");
        response.addProperty("group_name", groupName);
        com.google.gson.JsonArray membersArray = new com.google.gson.JsonArray();
        for (String member : members) {
            membersArray.add(member);
        }
        response.add("members", membersArray);
        sendMessage(gson.toJson(response));
    }

    private void handleWebRTCSignaling(JsonObject message) {
        try {
            if (!message.has("type") || !message.has("to")) {
                System.err.println("WebRTC signaling message missing 'type' or 'to': " + message);
                return;
            }

            String type = message.get("type").getAsString();
            String recipient = message.get("to").getAsString();

            // Get recipient's handler
            ClientHandler recipientHandler = chatController.getOnlineUser(recipient);
            if (recipientHandler != null) {
                // Forward the signaling message to the recipient
                JsonObject forwardMessage = new JsonObject();
                forwardMessage.addProperty("type", type);
                forwardMessage.addProperty("from", this.username);

                // Copy SDP or ICE candidate data
                if (message.has("sdp")) {
                    forwardMessage.add("sdp", message.get("sdp"));
                }
                if (message.has("candidate")) {
                    forwardMessage.add("candidate", message.get("candidate"));
                }

                recipientHandler.sendMessage(gson.toJson(forwardMessage));
                System.out.println("WebRTC " + type + " forwarded from " + this.username + " to " + recipient);
            } else {
                System.err.println("Cannot forward WebRTC message: recipient " + recipient + " not found");
            }
        } catch (Exception e) {
            System.err.println("Error handling WebRTC signaling: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void sendMessage(String message) {
        out.println(message);
    }

    public void sendAudioPacket(byte[] audioData) {
        try {
            String base64Audio = java.util.Base64.getEncoder().encodeToString(audioData);
            JsonObject json = new JsonObject();
            json.addProperty("type", "call_audio");
            json.addProperty("data", base64Audio);
            sendMessage(gson.toJson(json));
        } catch (Exception e) {
            System.err.println("Error enviando paquete de audio a " + username + ": " + e.getMessage());
        }
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
