import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class ChatController {

    private static ChatController instance;
    private final ConcurrentHashMap<String, ClientHandler> onlineUsers = new ConcurrentHashMap<>();
    private final DatabaseService dbService = new DatabaseService();
    private final Gson gson = new Gson();
    private final AtomicLong audioFileCounter = new AtomicLong(System.currentTimeMillis());
    private static final String AUDIO_STORAGE_PATH = "server_audio_files/";
    private final CallManager callManager;

    // El constructor ahora es privado y recibe el CallManager.
    private ChatController(CallManager callManager) {
        this.callManager = callManager;
        new File(AUDIO_STORAGE_PATH).mkdirs();
    }

    // El patrón Singleton se adapta para inyectar la dependencia.
    public static synchronized ChatController getInstance(CallManager callManager) {
        if (instance == null) {
            instance = new ChatController(callManager);
        }
        return instance;
    }
    
    public static synchronized ChatController getInstance() {
         if (instance == null) {
            // Esto no debería pasar si Server.java se inicia primero.
            throw new IllegalStateException("ChatController no ha sido inicializado con un CallManager.");
        }
        return instance;
    }

    public void userLogout(String username) {
        if (username != null) {
            onlineUsers.remove(username);
            callManager.userDisconnected(username); // Notificar al CallManager
            broadcastMessage(createNotification(username + " se ha desconectado."));
            System.out.println(username + " se ha desconectado.");
        }
    }

    // --- Lógica de Llamadas ---
    public void requestCall(String requester, String callee) {
        ClientHandler requesterHandler = onlineUsers.get(requester);
        ClientHandler calleeHandler = onlineUsers.get(callee);
        
        if (requesterHandler == null) return;

        if (calleeHandler != null) {
            JsonObject callRequest = new JsonObject();
            callRequest.addProperty("type", "call_request");
            callRequest.addProperty("from", requester);
            calleeHandler.sendMessage(gson.toJson(callRequest));
            requesterHandler.sendMessage(createNotification("Llamando a " + callee + "..."));
        } else {
            requesterHandler.sendMessage(createNotification("El usuario '" + callee + "' no está conectado."));
        }
    }

    // MÉTODO MODIFICADO
    public void acceptCall(String accepter, String requester) {
        if (callManager.startCall(accepter, requester)) {
            JsonObject callAccepted = new JsonObject();
            callAccepted.addProperty("type", "call_accepted");
            
            callAccepted.addProperty("with", requester);
            onlineUsers.get(accepter).sendMessage(gson.toJson(callAccepted));

            callAccepted.addProperty("with", accepter);
            onlineUsers.get(requester).sendMessage(gson.toJson(callAccepted));
        } else {
            // BLOQUE AÑADIDO: Notificar a ambos usuarios que la llamada no se pudo establecer
            ClientHandler requesterHandler = onlineUsers.get(requester);
            if (requesterHandler != null) {
                requesterHandler.sendMessage(createNotification("No se pudo establecer la llamada con " + accepter + "."));
            }
            ClientHandler accepterHandler = onlineUsers.get(accepter);
            if (accepterHandler != null) {
                accepterHandler.sendMessage(createNotification("No se pudo establecer la llamada con " + requester + "."));
            }
        }
    }
    
    public void endCall(String username) {
        String partner = callManager.getCallPartner(username);
        callManager.endCall(username);

        JsonObject callEnded = new JsonObject();
        callEnded.addProperty("type", "call_ended");

        ClientHandler userHandler = onlineUsers.get(username);
        if(userHandler != null) userHandler.sendMessage(gson.toJson(callEnded));
        
        ClientHandler partnerHandler = onlineUsers.get(partner);
        if(partnerHandler != null) partnerHandler.sendMessage(gson.toJson(callEnded));
    }


    // Recopila y envía un resumen del historial (público, privado y de grupos)
    public void sendInitialHistoryToUser(ClientHandler handler) {
        String username = handler.getUsername();

        // 1. Historial Público
        handler.sendMessage(createNotification("--- Últimos 10 mensajes del chat general ---"));
        dbService.getPublicMessages(10).forEach(handler::sendMessage);

        // 2. Historial Privado
        handler.sendMessage(createNotification("--- Últimos mensajes privados ---"));
        List<String> privatePartners = dbService.getPrivateChatPartners(username);
        if (privatePartners.isEmpty()) {
            handler.sendMessage(createNotification("No tienes mensajes privados recientes."));
        } else {
            privatePartners.forEach(partner -> {
                handler.sendMessage(createNotification("--- Conversación con " + partner + " ---"));
                // Obtenemos solo los últimos 5 mensajes de cada conversación para no saturar
                dbService.getPrivateMessages(username, partner, 5).forEach(handler::sendMessage);
            });
        }

        // 3. Historial de Grupos
        handler.sendMessage(createNotification("--- Últimos mensajes de tus grupos ---"));
        List<String> userGroups = dbService.getUserGroups(username);
        if (!userGroups.isEmpty()) {
            userGroups.forEach(group -> {
                handler.sendMessage(createNotification("--- Mensajes de " + group + " ---"));
                dbService.getGroupMessages(group, 5).forEach(handler::sendMessage);
            });
        }
        
        handler.sendMessage(createNotification("------------------------------------"));
        handler.sendMessage(createNotification("¡Bienvenido!"));
    }


    // --- Otros métodos ---
    public synchronized boolean loginUser(String username, String password, ClientHandler handler) {
        if (dbService.isValidUser(username, password) && !onlineUsers.containsKey(username)) {
            onlineUsers.put(username, handler);
            handler.setUsername(username);
            broadcastToOthers(username, createNotification(username + " se ha conectado."));
            System.out.println(username + " se ha conectado.");
            return true;
        }
        return false;
    }

    public boolean registerUser(String username, String password) {
        return dbService.registerUser(username, password);
    }
    
    public void processPublicMessage(String sender, String text) {
        dbService.savePublicMessage(sender, text);
        String messageJson = createChatMessage("public", sender, sender, text, null);
        broadcastMessage(messageJson);
    }

    public void processPrivateMessage(String sender, String recipient, String text) {
        dbService.savePrivateMessage(sender, recipient, text, "TEXT");
        ClientHandler recipientHandler = onlineUsers.get(recipient);
        if (recipientHandler != null) {
            recipientHandler.sendMessage(createChatMessage("private_from", sender, sender, text, null));
        }
        ClientHandler senderHandler = onlineUsers.get(sender);
        if (senderHandler != null) {
            senderHandler.sendMessage(createChatMessage("private_to", sender, recipient, text, null));
        }
    }

    public void processGroupMessage(String sender, String groupName, String text) {
        if (dbService.isUserInGroup(sender, groupName)) {
            dbService.saveGroupMessage(sender, groupName, text, "TEXT");
            String messageJson = createChatMessage("group", sender, null, text, groupName);
            broadcastToGroup(sender, groupName, messageJson);
        } else {
            ClientHandler handler = onlineUsers.get(sender);
            if(handler != null) {
                handler.sendMessage(createNotification("No eres miembro del grupo '" + groupName + "'."));
            }
        }
    }

    public void createGroup(String owner, String groupName) {
        DatabaseService.GroupCreationResult result = dbService.createGroup(groupName, owner);
        ClientHandler handler = onlineUsers.get(owner);
        if (handler == null) return;
        switch (result) {
            case SUCCESS:
                handler.sendMessage(createNotification("Grupo '" + groupName + "' creado exitosamente."));
                break;
            case ALREADY_EXISTS:
                handler.sendMessage(createNotification("El grupo '" + groupName + "' ya existe."));
                break;
            case DB_ERROR:
                handler.sendMessage(createNotification("Ocurrió un error en la base de datos al crear el grupo."));
                break;
        }
    }

    public void inviteToGroup(String inviter, String groupName, String userToInvite) {
        ClientHandler inviterHandler = onlineUsers.get(inviter);
        if (inviterHandler == null) return;
        if (!dbService.isUserInGroup(inviter, groupName)) {
            inviterHandler.sendMessage(createNotification("No puedes invitar a un grupo del que no eres miembro."));
            return;
        }
        if (!dbService.doesUserExist(userToInvite)) {
            inviterHandler.sendMessage(createNotification("El usuario '" + userToInvite + "' no existe."));
            return;
        }
        if (dbService.addUserToGroup(userToInvite, groupName)) {
            broadcastToGroup(null, groupName, createNotification(userToInvite + " ha sido añadido al grupo por " + inviter + "."));
        } else {
            inviterHandler.sendMessage(createNotification("No se pudo añadir a '" + userToInvite + "' al grupo (quizás ya es miembro)."));
        }
    }
    
    public void leaveGroup(String username, String groupName) {
        ClientHandler handler = onlineUsers.get(username);
        if (handler == null) return;
        if (dbService.removeUserFromGroup(username, groupName)) {
            handler.sendMessage(createNotification("Has salido del grupo '" + groupName + "'."));
            broadcastToGroup(username, groupName, createNotification(username + " ha salido del grupo."));
        } else {
            handler.sendMessage(createNotification("No se pudo salir del grupo (quizás no eras miembro)."));
        }
    }

    public List<String> getPublicChatHistory(int limit) {
        return dbService.getPublicMessages(limit);
    }

    public List<String> getGroupChatHistory(String groupName, int limit) {
        return dbService.getGroupMessages(groupName, limit);
    }

    public List<String> getPrivateChatHistory(String user1, String user2, int limit) {
        return dbService.getPrivateMessages(user1, user2, limit);
    }
    
    public String saveAudioFile(String sender, String originalFileName, InputStream inStream, long fileSize) {
        String newFileName = String.format("audio_%s_%d.wav", sender, audioFileCounter.getAndIncrement());
        File targetFile = new File(AUDIO_STORAGE_PATH + newFileName);
        try (FileOutputStream fos = new FileOutputStream(targetFile)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            long totalBytesRead = 0;
            while (totalBytesRead < fileSize && (bytesRead = inStream.read(buffer, 0, (int)Math.min(buffer.length, fileSize - totalBytesRead))) != -1) {
                fos.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
            }
            if(totalBytesRead == fileSize) {
                return targetFile.getPath();
            }
        } catch (IOException e) {
             System.err.println("Error guardando archivo de audio: " + e.getMessage());
        }
        return null;
    }

    public void processAudioMessage(String sender, String recipient, String audioFilePath) {
        if (dbService.isGroup(recipient)) {
            dbService.saveGroupMessage(sender, recipient, audioFilePath, "AUDIO");
            String messageJson = createChatMessage("group_audio", sender, null, new File(audioFilePath).getName(), recipient);
            broadcastToGroup(sender, recipient, messageJson);
        } else {
            dbService.savePrivateMessage(sender, recipient, audioFilePath, "AUDIO");
            ClientHandler recipientHandler = onlineUsers.get(recipient);
            if (recipientHandler != null) {
                recipientHandler.sendMessage(createChatMessage("private_audio_from", sender, sender, new File(audioFilePath).getName(), null));
            }
            ClientHandler senderHandler = onlineUsers.get(sender);
            if (senderHandler != null) {
                senderHandler.sendMessage(createChatMessage("private_audio_to", sender, recipient, new File(audioFilePath).getName(), null));
            }
        }
    }
    
    public File getAudioFile(String fileName) {
        if (fileName == null || fileName.contains("..")) {
            return null;
        }
        File file = new File(AUDIO_STORAGE_PATH + fileName);
        return (file.exists() && !file.isDirectory()) ? file : null;
    }
    
    private void broadcastMessage(String message) {
        onlineUsers.values().forEach(handler -> handler.sendMessage(message));
    }

    private void broadcastToOthers(String excludedUsername, String message) {
        onlineUsers.values().stream()
            .filter(h -> !h.getUsername().equals(excludedUsername))
            .forEach(h -> h.sendMessage(message));
    }

    private void broadcastToGroup(String excludedUsername, String groupName, String message) {
        dbService.getGroupMembers(groupName).stream()
            .filter(member -> excludedUsername == null || !member.equals(excludedUsername))
            .map(onlineUsers::get)
            .filter(java.util.Objects::nonNull)
            .forEach(handler -> handler.sendMessage(message));
    }

    public String createNotification(String message) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "notification");
        json.addProperty("message", message);
        return gson.toJson(json);
    }

    private String createChatMessage(String subType, String sender, String party, String text, String group) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "chat");
        json.addProperty("sub_type", subType);
        json.addProperty("sender", sender);
        json.addProperty("party", party);
        json.addProperty("text", text);
        if (group != null) {
            json.addProperty("group", group);
        }
        // Agregar timestamp actual para mensajes nuevos
        json.addProperty("sent_at", java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return gson.toJson(json);
    }
    
    public void rejectCall(String rejecter, String requester) {
        ClientHandler requesterHandler = onlineUsers.get(requester);
        if (requesterHandler != null) {
            JsonObject callRejected = new JsonObject();
            callRejected.addProperty("type", "call_rejected");
            callRejected.addProperty("user", rejecter);
            requesterHandler.sendMessage(gson.toJson(callRejected));
        }
    }
    
    public List<String> getAllUsers() {
        return dbService.getAllUsers();
    }
    
    public List<String> getGroupMembers(String groupName) {
        return dbService.getGroupMembers(groupName);
    }
}


