import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChatController {
    private static final ChatController INSTANCE = new ChatController();
    private final Map<String, ClientHandler> onlineUsers = new ConcurrentHashMap<>();
    private final DatabaseService dbService = new DatabaseService();
    private final Gson gson = new Gson();

    private ChatController() {}

    public static ChatController getInstance() {
        return INSTANCE;
    }

    public synchronized boolean loginUser(String username, String password, ClientHandler handler) {
        if (onlineUsers.containsKey(username) || !dbService.isValidUser(username, password)) {
            return false;
        }
        handler.setUsername(username);
        onlineUsers.put(username, handler);
        System.out.println(username + " se ha conectado.");
        broadcastToOthers(username, createNotification(username + " se ha conectado."));
        return true;
    }

    public boolean registerUser(String username, String password) {
        return dbService.registerUser(username, password);
    }

    public void userLogout(String username) {
        if (username != null) {
            onlineUsers.remove(username);
            System.out.println(username + " se ha desconectado.");
            broadcastMessage(createNotification(username + " se ha desconectado."));
        }
    }

    public void processPublicMessage(String sender, String text) {
        dbService.savePublicMessage(sender, text);
        // CAMBIO: Ahora enviamos el texto en crudo, sin formato.
        broadcastMessage(createChatMessage("public", sender, text));
    }

    public void processPrivateMessage(String sender, String recipient, String text) {
        dbService.savePrivateMessage(sender, recipient, text);
        ClientHandler recipientHandler = onlineUsers.get(recipient);
        
        if (recipientHandler != null) {
            // El cliente del receptor se encargará de formatearlo.
            recipientHandler.sendMessage(createChatMessage("private_from", sender, text));
        }
        // Echo al emisor, también sin formato.
        onlineUsers.get(sender).sendMessage(createChatMessage("private_to", recipient, text));
    }

    public void createGroup(String groupName, String owner) {
        ClientHandler ownerHandler = onlineUsers.get(owner);
        GroupCreationResult result = dbService.createGroup(groupName, owner);
        switch (result) {
            case SUCCESS:
                ownerHandler.sendMessage(createNotification("Grupo '" + groupName + "' creado exitosamente."));
                break;
            case ALREADY_EXISTS:
                ownerHandler.sendMessage(createNotification("El grupo '" + groupName + "' ya existe."));
                break;
            case DB_ERROR:
                ownerHandler.sendMessage(createNotification("Ocurrió un error en la base de datos al crear el grupo."));
                break;
        }
    }

    public void inviteToGroup(String inviter, String groupName, String userToInvite) {
        ClientHandler inviterHandler = onlineUsers.get(inviter);
        if (!dbService.isUserMemberOfGroup(inviter, groupName)) {
            inviterHandler.sendMessage(createNotification("No eres miembro del grupo '" + groupName + "'."));
            return;
        }
        if (dbService.addUserToGroup(userToInvite, groupName)) {
            inviterHandler.sendMessage(createNotification(userToInvite + " ha sido añadido al grupo '" + groupName + "'."));
            ClientHandler invitedHandler = onlineUsers.get(userToInvite);
            if (invitedHandler != null) {
                invitedHandler.sendMessage(createNotification("Has sido añadido al grupo '" + groupName + "' por " + inviter + "."));
            }
        } else {
            inviterHandler.sendMessage(createNotification("No se pudo añadir a " + userToInvite + " (quizás ya es miembro)."));
        }
    }

    public void processGroupMessage(String groupName, String sender, String text) {
        if (!dbService.isUserMemberOfGroup(sender, groupName)) {
            onlineUsers.get(sender).sendMessage(createNotification("No eres miembro del grupo '" + groupName + "'."));
            return;
        }
        dbService.saveGroupMessage(groupName, sender, text);
        
        onlineUsers.forEach((username, handler) -> {
            if (dbService.isUserMemberOfGroup(username, groupName)) {
                // CAMBIO: Se envía el nombre del grupo para que el cliente lo formatee.
                handler.sendMessage(createGroupChatMessage(groupName, sender, text));
            }
        });
    }

    public void sendGroupHistory(String username, String groupName) {
        ClientHandler handler = onlineUsers.get(username);
        if (!dbService.isUserMemberOfGroup(username, groupName)) {
            handler.sendMessage(createNotification("No eres miembro de '" + groupName + "'."));
            return;
        }
        handler.sendMessage(createNotification("--- Últimos 15 mensajes de " + groupName + " ---"));
        List<String> history = dbService.getGroupHistory(groupName, 15);
        for (String msg : history) {
            handler.sendMessage(createChatMessage("history", "server", msg));
        }
        handler.sendMessage(createNotification("------------------------------------"));
    }

    public void sendPrivateHistory(String requester, String withUser) {
        ClientHandler handler = onlineUsers.get(requester);
        handler.sendMessage(createNotification("--- Tu historial con " + withUser + " ---"));
        List<String> history = dbService.getPrivateHistory(requester, withUser, 15);
        for (String msg : history) {
            handler.sendMessage(createChatMessage("history", "server", msg));
        }
        handler.sendMessage(createNotification("------------------------------------"));
    }

    public List<String> getPublicChatHistory(int limit) {
        return dbService.getPublicHistory(limit);
    }
    
    private void broadcastMessage(String message) {
        onlineUsers.values().forEach(handler -> handler.sendMessage(message));
    }

    private void broadcastToOthers(String excludeUser, String message) {
        onlineUsers.forEach((username, handler) -> {
            if (!username.equals(excludeUser)) {
                handler.sendMessage(message);
            }
        });
    }

    private String createNotification(String message) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "notification");
        json.addProperty("message", message);
        return gson.toJson(json);
    }

    private String createChatMessage(String subType, String senderOrRecipient, String text) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "chat");
        json.addProperty("sub_type", subType);
        // Para mensajes privados, este campo puede ser el emisor o el receptor.
        json.addProperty("party", senderOrRecipient); 
        json.addProperty("text", text);
        return gson.toJson(json);
    }
    
    private String createGroupChatMessage(String groupName, String sender, String text) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "chat");
        json.addProperty("sub_type", "group");
        json.addProperty("group", groupName);
        json.addProperty("sender", sender);
        json.addProperty("text", text);
        return gson.toJson(json);
    }
}