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

        // Inicializar ChatController explícitamente
        ChatController.getInstance(callManager);

        // Se inicia el servidor UDP en un hilo separado, pasándole el CallManager.
        new Thread(new UdpServer(callManager)).start();

        // Start HTTP Server for audio files
        new Thread(() -> {
            try {
                com.sun.net.httpserver.HttpServer httpServer = com.sun.net.httpserver.HttpServer
                        .create(new java.net.InetSocketAddress(3001), 0);
                httpServer.createContext("/audio", exchange -> {
                    // Add CORS headers
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
                    exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type,Authorization");

                    if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                        exchange.sendResponseHeaders(204, -1);
                        return;
                    }

                    String path = exchange.getRequestURI().getPath();
                    String fileName = path.substring(path.lastIndexOf('/') + 1);
                    java.io.File file = new java.io.File("server_audio_files/" + fileName);

                    if (file.exists() && !file.isDirectory()) {
                        exchange.getResponseHeaders().add("Content-Type", "audio/webm");
                        exchange.getResponseHeaders().add("Accept-Ranges", "bytes");
                        exchange.getResponseHeaders().add("Cache-Control", "no-cache");
                        exchange.sendResponseHeaders(200, file.length());
                        try (java.io.OutputStream os = exchange.getResponseBody();
                                java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                            byte[] buffer = new byte[4096];
                            int count;
                            while ((count = fis.read(buffer)) != -1) {
                                os.write(buffer, 0, count);
                            }
                        }
                    } else {
                        String response = "File not found";
                        exchange.sendResponseHeaders(404, response.length());
                        try (java.io.OutputStream os = exchange.getResponseBody()) {
                            os.write(response.getBytes());
                        }
                    }
                });
                httpServer.setExecutor(null);
                httpServer.start();
                System.out.println("Servidor HTTP de audio iniciado en el puerto 3001");
            } catch (IOException e) {
            }
        }).start();

        // Start ICE Server in a separate thread
        new Thread(() -> {
            com.zeroc.Ice.Properties properties = com.zeroc.Ice.Util.createProperties();
            properties.setProperty("Ice.MessageSizeMax", "10240"); // 10MB limit
            com.zeroc.Ice.InitializationData initData = new com.zeroc.Ice.InitializationData();
            initData.properties = properties;

            try (com.zeroc.Ice.Communicator communicator = com.zeroc.Ice.Util.initialize(args, initData)) {
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
