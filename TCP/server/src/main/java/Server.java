import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private static final int TCP_PORT = 12345;
    private static final int THREAD_POOL_SIZE = 20;

    public static void main(String[] args) {
        ExecutorService tcpPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        // Se crea una única instancia del CallManager para compartirla.
        CallManager callManager = new CallManager();

        // Se inicia el servidor UDP en un hilo separado, pasándole el CallManager.
        new Thread(new UdpServer(callManager)).start();

        try (ServerSocket serverSocket = new ServerSocket(TCP_PORT)) {
            System.out.println("Servidor de Chat (TCP) iniciado en el puerto " + TCP_PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                // Cada nuevo cliente recibe la referencia al CallManager.
                tcpPool.submit(new ClientHandler(clientSocket, callManager));
            }
        } catch (IOException e) {
            System.err.println("Error crítico en el servidor TCP: " + e.getMessage());
        } finally {
            tcpPool.shutdown();
        }
    }
}

