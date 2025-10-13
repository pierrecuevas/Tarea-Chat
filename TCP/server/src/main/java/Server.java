import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {

    private static final int PORT = 12345;
    private static final int THREAD_POOL_SIZE = 10;
    
    public static void main(String[] args) {
        ExecutorService pool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        // Inicializa el controlador (Singleton)
        ChatController.getInstance();
        
        try (ServerSocket server = new ServerSocket(PORT)) {
            System.out.println("Servidor de Chat iniciado en el puerto " + PORT);

            while (true) {
                Socket clientSocket = server.accept();
                // Simplemente crea un handler y lo pone a trabajar.
                pool.execute(new ClientHandler(clientSocket));
            }
        } catch (IOException e) {
            System.err.println("Error en el servidor: " + e.getMessage());
        } finally {
            pool.shutdown();
        }
    }
}

