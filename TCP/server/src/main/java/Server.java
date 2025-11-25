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

        // Start ICE Server in a separate thread
        new Thread(() -> {
            try (com.zeroc.Ice.Communicator communicator = com.zeroc.Ice.Util.initialize(args)) {
                System.out.println("Iniciando servidor ICE...");
                com.zeroc.Ice.ObjectAdapter adapter = communicator.createObjectAdapterWithEndpoints("VoiceChatAdapter",
                        "ws -h 0.0.0.0 -p 10000");
                com.zeroc.Ice.Object object = new VoiceChatI();
                adapter.add(object, com.zeroc.Ice.Util.stringToIdentity("VoiceChat"));
                adapter.activate();
                System.out.println("Servidor ICE iniciado en el puerto 10000 (WebSocket)");
                communicator.waitForShutdown();
            } catch (Exception e) {
                System.err.println("Error en el servidor ICE: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();

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
