import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * El cerebro del servidor de chat. Gestiona la lógica de negocio.
 * Es un Singleton para asegurar que solo haya una instancia controlando todo.
 */
public class ChatController {
    private static final ChatController instance = new ChatController();
    private final DatabaseService dbService;
    private final Map<String, ClientHandler> loggedInClients = new ConcurrentHashMap<>();

    private ChatController() {
        this.dbService = new DatabaseService();
    }

    public static ChatController getInstance() {
        return instance;
    }

    /**
     * Intenta autenticar a un usuario.
     * @return true si el login es exitoso, false en caso contrario.
     */
    public synchronized boolean loginUser(String username, String password, ClientHandler clientHandler) {
        if (dbService.isValidUser(username, password) && !loggedInClients.containsKey(username)) {
            loggedInClients.put(username, clientHandler);
            clientHandler.setUsername(username);
            System.out.println(username + " ha iniciado sesión.");
            broadcastMessage(username + " se ha unido al chat.", null);
            return true;
        }
        return false;
    }

    /**
     * Registra un nuevo usuario en la base de datos.
     * @return true si el registro es exitoso, false si el usuario ya existe.
     */
    public synchronized boolean registerUser(String username, String password) {
        return dbService.registerUser(username, password);
    }
    
    /**
     * Procesa un mensaje público y lo reenvía a todos los demás.
     */
    public void processPublicMessage(String senderUsername, String messageText) {
        String formattedMessage = senderUsername + ": " + messageText;
        // Guardar en el historial de la base de datos (o en JSON si se prefiere para mensajes)
        HistoryService.saveMessage(new Message("public_message", senderUsername, "all", messageText));
        broadcastMessage(formattedMessage, senderUsername);
    }

    /**
     * Procesa un mensaje privado para un usuario específico.
     */
    public void processPrivateMessage(String sender, String recipient, String text) {
        ClientHandler recipientHandler = loggedInClients.get(recipient);
        if (recipientHandler != null) {
            String formattedMessage = sender + " (privado): " + text;
            recipientHandler.sendMessage(formattedMessage);
            // Guardar en el historial
            HistoryService.saveMessage(new Message("private_message", sender, recipient, text));
        } else {
            // Notificar al emisor que el usuario no está conectado.
            ClientHandler senderHandler = loggedInClients.get(sender);
            if(senderHandler != null) {
                senderHandler.sendMessage("El usuario '" + recipient + "' no está conectado.");
            }
        }
    }
    
    /**
     * Elimina a un cliente de la lista de conectados cuando se desconecta.
     */
    public void userLogout(String username) {
        if (username != null) {
            loggedInClients.remove(username);
            System.out.println(username + " se ha desconectado.");
            broadcastMessage(username + " ha salido del chat.", null);
        }
    }

    /**
     * Envía un mensaje a todos los clientes conectados.
     * Si `excludeUsername` no es null, no se le envía el mensaje a ese usuario.
     */
    private void broadcastMessage(String message, String excludeUsername) {
        for (Map.Entry<String, ClientHandler> entry : loggedInClients.entrySet()) {
            if (!entry.getKey().equals(excludeUsername)) {
                entry.getValue().sendMessage(message);
            }
        }
    }
}
