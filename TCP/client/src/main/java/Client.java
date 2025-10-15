import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * The main entry point for the client application.
 * Establishes the connection and coordinates all other client-side components.
 */
public class Client {

    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 12345;
    
    private String currentChatContext = "General";
    private String username;

    public static void main(String[] args) {
        new Client().start();
    }

    public void start() {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
            System.out.println("Conectado al servidor.");

            AuthHandler authHandler = new AuthHandler(socket);
            this.username = authHandler.authenticate(); // Handles login/register flow

            if (this.username != null) {
                // If authentication is successful, start the other components
                AudioService audioService = new AudioService();
                
                // Start thread to listen for server messages
                ServerListener serverListener = new ServerListener(this, socket, audioService);
                new Thread(serverListener).start();

                // Use the main thread to handle user input
                new UserInputHandler(this, socket, audioService).run();
            }

        } catch (UnknownHostException e) {
            System.err.println("Host desconocido: " + SERVER_ADDRESS);
        } catch (IOException e) {
            System.err.println("No se pudo conectar al servidor: " + e.getMessage());
        } finally {
            System.out.println("\nCliente cerrado.");
        }
    }

    // --- Getters and Setters for Shared State ---

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

