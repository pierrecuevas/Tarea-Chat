import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class ChatController {

    private static final ChatController instance = new ChatController();
    private final ConcurrentHashMap<String, ClientHandler> onlineUsers = new ConcurrentHashMap<>();
    private final DatabaseService dbService = new DatabaseService();
    private final Gson gson = new Gson();
    private final AtomicLong audioFileCounter = new AtomicLong(System.currentTimeMillis());
    private static final String AUDIO_STORAGE_PATH = "server_audio_files/";

    private ChatController() {
        new File(AUDIO_STORAGE_PATH).mkdirs();
    }

    public static ChatController getInstance() {
        return instance;
    }

    // --- Lógica de Autenticación y Usuarios ---
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

    public void userLogout(String username) {
        if (username != null) {
            onlineUsers.remove(username);
            broadcastMessage(createNotification(username + " se ha desconectado."));
            System.out.println(username + " se ha desconectado.");
        }
    }

    // --- Lógica de Mensajes y Grupos ---
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

    // --- Lógica de Historial ---
    public List<String> getPublicChatHistory(int limit) {
        List<String> history = dbService.getPublicMessages(limit);
        Collections.reverse(history);
        return history;
    }

    public List<String> getGroupChatHistory(String groupName, int limit) {
        List<String> history = dbService.getGroupMessages(groupName, limit);
        Collections.reverse(history);
        return history;
    }

    public List<String> getPrivateChatHistory(String user1, String user2, int limit) {
        List<String> history = dbService.getPrivateMessages(user1, user2, limit);
        Collections.reverse(history);
        return history;
    }

    // --- Lógica de Transferencia de Audio ---
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
            return (totalBytesRead == fileSize) ? targetFile.getPath() : null;
        } catch (IOException e) {
            return null;
        }
    }

    public void processAudioMessage(String sender, String recipient, String audioFilePath) {
        if (dbService.isGroup(recipient)) {
            dbService.saveGroupMessage(sender, recipient, audioFilePath, "AUDIO");
            String notification = createChatMessage("group_audio", sender, null, new File(audioFilePath).getName(), recipient);
            broadcastToGroup(sender, recipient, notification);
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
        if (file.exists() && !file.isDirectory()) {
            return file;
        }
        return null;
    }

    // --- Métodos de Utilidad ---
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
        return gson.toJson(json);
    }
}

