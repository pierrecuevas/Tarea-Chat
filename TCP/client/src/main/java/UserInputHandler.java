import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.InputStreamReader;
import java.net.Socket;

public class UserInputHandler implements Runnable {

    private final Client chatClient;
    private final PrintWriter out;
    private final AudioService audioService;
    private final OutputStream socketOutStream;
    private final BufferedReader userInput;
    private String lastRecordedAudioPath = null;
    private final Gson gson = new Gson();

    public UserInputHandler(Client chatClient, Socket socket, AudioService audioService) throws IOException {
        this.chatClient = chatClient;
        this.out = new PrintWriter(socket.getOutputStream(), true);
        this.socketOutStream = socket.getOutputStream();
        this.userInput = new BufferedReader(new InputStreamReader(System.in));
        this.audioService = audioService;
    }

    @Override
    public void run() {
        try {
            printWelcomeMessage();
            String line;
            while (true) {
                System.out.print(String.format("[%s]> ", chatClient.getCurrentChatContext()));
                line = userInput.readLine();
                if (line == null || "/exit".equalsIgnoreCase(line.trim())) {
                    break;
                }
                if (!line.trim().isEmpty()) {
                    handleLine(line.trim());
                }
            }
        } catch (IOException e) {
            System.out.println("Error al leer la entrada del usuario.");
        }
    }
    
    private void printWelcomeMessage() {
        System.out.println("\n--- Comandos Disponibles ---");
        System.out.println("/crear <grupo>");
        System.out.println("/invitar <grupo> <usuario>");
        System.out.println("/salir <grupo>");
        System.out.println("/chat <grupo> | /general");
        System.out.println("/historial <usuario>");
        System.out.println("/msg <usuario> <mensaje>");
        System.out.println("/grabar | /detener | /enviar_audio <dest>");
        System.out.println("/reproducir <archivo.wav>");
        System.out.println("/exit");
        System.out.println("--------------------------");
    }

    private void handleLine(String line) throws IOException {
        JsonObject request = new JsonObject();
        
        if (line.startsWith("/")) {
            String[] parts = line.split("\\s+", 2);
            String command = parts[0];
            String args = parts.length > 1 ? parts[1].trim() : "";

            switch (command) {
                case "/reproducir":
                     if (args.isEmpty()) { System.out.println("Uso: /reproducir <nombre_archivo>"); return; }
                     // If the file is already downloaded, play it. Otherwise, request it.
                     if (audioService.isAudioDownloaded(args)) {
                         audioService.playAudio(args);
                     } else {
                         System.out.println(">> El archivo no está local. Solicitando al servidor...");
                         request.addProperty("command", "request_audio");
                         request.addProperty("file_name", args);
                         out.println(gson.toJson(request));
                     }
                    return; // Return because we either played it or sent a request.
                
                // --- Other commands ---
                case "/crear":
                    if (args.isEmpty()) { System.out.println("Uso: /crear <nombre_grupo>"); return; }
                    request.addProperty("command", "create_group");
                    request.addProperty("group_name", args);
                    break;
                case "/invitar":
                    String[] inviteArgs = args.split("\\s+", 2);
                    if (inviteArgs.length < 2) { System.out.println("Uso: /invitar <grupo> <usuario>"); return; }
                    request.addProperty("command", "invite_to_group");
                    request.addProperty("group_name", inviteArgs[0]);
                    request.addProperty("user_to_invite", inviteArgs[1]);
                    break;
                case "/salir":
                    if (args.isEmpty()) { System.out.println("Uso: /salir <grupo>"); return; }
                    request.addProperty("command", "leave_group");
                    request.addProperty("group_name", args);
                    break;
                case "/chat":
                    if (args.isEmpty()) { System.out.println("Uso: /chat <nombre_grupo>"); return; }
                    chatClient.setCurrentChatContext(args);
                    request.addProperty("command", "get_group_history");
                    request.addProperty("group_name", args);
                    break;
                case "/general":
                    chatClient.setCurrentChatContext("General");
                    System.out.println("Cambiado al chat General.");
                    return;
                case "/msg":
                    String[] msgArgs = args.split("\\s+", 2);
                    if (msgArgs.length < 2) { System.out.println("Uso: /msg <usuario> <mensaje>"); return; }
                    request.addProperty("command", "private_message");
                    request.addProperty("recipient", msgArgs[0]);
                    request.addProperty("text", msgArgs[1]);
                    break;
                case "/historial":
                    if (args.isEmpty()) { System.out.println("Uso: /historial <usuario>"); return; }
                    request.addProperty("command", "get_private_history");
                    request.addProperty("with_user", args);
                    break;
                case "/grabar":
                    lastRecordedAudioPath = audioService.startRecording(chatClient.getUsername());
                    return;
                case "/detener":
                    audioService.stopRecording();
                    return;
                case "/enviar_audio":
                    if (args.isEmpty()) { System.out.println("Uso: /enviar_audio <destinatario>"); return; }
                    if (lastRecordedAudioPath != null) {
                        sendAudioFile(args, lastRecordedAudioPath);
                    } else {
                        System.out.println(">> Primero debes grabar un audio con /grabar.");
                    }
                    return;
                default:
                    System.out.println(">> Comando desconocido: " + command);
                    return;
            }
        } else {
            if ("General".equals(chatClient.getCurrentChatContext())) {
                request.addProperty("command", "public_message");
            } else {
                request.addProperty("command", "group_message");
                request.addProperty("group_name", chatClient.getCurrentChatContext());
            }
            request.addProperty("text", line);
        }
        out.println(gson.toJson(request));
    }

    private void sendAudioFile(String recipient, String filePath) throws IOException {
        File audioFile = new File(filePath);
        if (!audioFile.exists()) {
            System.out.println(">> El archivo de audio no existe: " + filePath);
            return;
        }

        JsonObject request = new JsonObject();
        request.addProperty("command", "send_audio");
        request.addProperty("recipient", recipient);
        request.addProperty("file_name", audioFile.getName());
        request.addProperty("file_size", audioFile.length());
        
        out.println(gson.toJson(request));

        try (FileInputStream fis = new FileInputStream(audioFile)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                socketOutStream.write(buffer, 0, bytesRead);
            }
            socketOutStream.flush();
        }
        System.out.println(">> Archivo de audio enviado.");
    }
}

