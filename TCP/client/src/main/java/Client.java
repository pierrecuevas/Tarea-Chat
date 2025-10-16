import java.io.IOException;
import java.net.Socket;

/**
 * The main entry point for the client application.
 * Establishes the connection and coordinates all other client-side components.
 */
public class Client {

    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 12345;
    
    private volatile ClientState currentState = ClientState.IDLE;
    private String username;
    private String currentChatContext = "General";
    private String incomingCallFrom = null;

    public static void main(String[] args) {
        new Client().start();
    }

    public void start() {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
            System.out.println("Conectado al servidor.");

            AuthHandler authHandler = new AuthHandler(socket);
            this.username = authHandler.authenticate();

            if (this.username != null) {
                AudioService audioService = new AudioService();
                CallHandler callHandler = new CallHandler(this, audioService);

                new Thread(new ServerListener(this, socket, audioService, callHandler)).start();
                new UserInputHandler(this, socket, audioService, callHandler).run();
            }
        } catch (IOException e) {
            System.err.println("No se pudo conectar al servidor: " + e.getMessage());
        } finally {
            System.out.println("\nCliente cerrado.");
        }
    }

    // --- Métodos de gestión de estado ---
    public synchronized ClientState getCurrentState() {
        return currentState;
    }

    public synchronized void setCurrentState(ClientState state) {
        this.currentState = state;
    }

    public synchronized String getIncomingCallFrom() {
        return incomingCallFrom;
    }

    public synchronized void setIncomingCallFrom(String username) {
        this.incomingCallFrom = username;
    }
    
    public synchronized String getCurrentChatContext() {
        return currentChatContext;
    }

    public synchronized void setCurrentChatContext(String context) {
        this.currentChatContext = context;
    }

    public String getUsername() {
        return username;
    }
}

