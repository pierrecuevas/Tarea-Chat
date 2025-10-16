import java.io.IOException;
import java.net.Socket;

public class Client {

    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 12345;
    
    private volatile ClientState currentState = ClientState.IDLE;
    private String username;
    private String currentChatContext = "General";
    private String incomingCallFrom = null;
    private Socket socket; 

    public static void main(String[] args) {
        new Client().start();
    }

    public void start() {
        CallHandler callHandler = null;
        try {
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            System.out.println("Conectado al servidor.");

            AuthHandler authHandler = new AuthHandler(socket);
            this.username = authHandler.authenticate();

            if (this.username != null) {
                AudioService audioService = new AudioService();
                callHandler = new CallHandler(this, audioService);
                callHandler.registerWithServer();

                new Thread(new ServerListener(this, socket, audioService, callHandler)).start();
                new UserInputHandler(this, socket, audioService, callHandler).run();
            }
        } catch (IOException e) {
            
        } finally {
            if (callHandler != null) {
                callHandler.closeSocket();
            }
            closeConnection();
            System.out.println("\nCliente cerrado.");
        }
    }
    
    public void closeConnection() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {}
    }

    // --- Métodos de gestión de estado ---
    public synchronized ClientState getCurrentState() { return currentState; }
    public synchronized void setCurrentState(ClientState state) { this.currentState = state; }
    public synchronized String getIncomingCallFrom() { return incomingCallFrom; }
    public synchronized void setIncomingCallFrom(String username) { this.incomingCallFrom = username; }
    public synchronized String getCurrentChatContext() { return currentChatContext; }
    public synchronized void setCurrentChatContext(String context) { this.currentChatContext = context; }
    public String getUsername() { return username; }
}

