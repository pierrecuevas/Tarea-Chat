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
    private final CallHandler callHandler;

    public UserInputHandler(Client chatClient, Socket socket, AudioService audioService, CallHandler callHandler) throws IOException {
        this.chatClient = chatClient;
        this.out = new PrintWriter(socket.getOutputStream(), true);
        this.socketOutStream = socket.getOutputStream();
        this.userInput = new BufferedReader(new InputStreamReader(System.in));
        this.audioService = audioService;
        this.callHandler = callHandler;
    }

    @Override
    public void run() {
        try {
            printWelcomeMessage();
            while (true) {
                // El estado actual determina el prompt que se muestra
                ClientState currentState = chatClient.getCurrentState();
                displayPrompt(currentState);
                
                String line = userInput.readLine();
                if (line == null) break; // Fin de la entrada (Ctrl+D)

                // El estado actual determina cómo se procesa la entrada
                processLineBasedOnState(line.trim(), currentState);
            }
        } catch (IOException e) {
            System.out.println("\nDesconectado del servidor.");
        }
    }

    private void displayPrompt(ClientState state) {
        switch (state) {
            case OUTGOING_CALL:
                System.out.print(">> Llamando... (escribe /colgar para cancelar)\n> ");
                break;
            case INCOMING_CALL:
                System.out.print(">> Llamada entrante de " + chatClient.getIncomingCallFrom() + ". (/aceptar o /rechazar)\n> ");
                break;
            case IN_CALL:
                System.out.print(">> Llamada en curso... (escribe /colgar para finalizar)\n> ");
                break;
            case IDLE:
            default:
                System.out.print(String.format("[%s]> ", chatClient.getCurrentChatContext()));
                break;
        }
    }
    
    private void processLineBasedOnState(String line, ClientState state) throws IOException {
        if (line.isEmpty()) return;

        switch (state) {
            case OUTGOING_CALL:
            case IN_CALL:
                if ("/colgar".equalsIgnoreCase(line)) {
                    sendHangupRequest();
                } else {
                    System.out.println(">> Comando no válido durante una llamada. Usa /colgar.");
                }
                break;
            case INCOMING_CALL:
                if ("/aceptar".equalsIgnoreCase(line)) {
                    sendAcceptRequest();
                } else if ("/rechazar".equalsIgnoreCase(line)) {
                    sendRejectRequest();
                } else {
                    System.out.println(">> Comando no válido. Debes /aceptar o /rechazar.");
                }
                break;
            case IDLE:
                handleIdleInput(line);
                break;
        }
    }

    private void handleIdleInput(String line) throws IOException {
        if ("/exit".equalsIgnoreCase(line)) {
            // Cierra el socket para terminar el bucle del ServerListener
            chatClient.closeConnection();
            return;
        }

        JsonObject request = new JsonObject();
        
        if (line.startsWith("/")) {
            String[] parts = line.split("\\s+", 2);
            String command = parts[0];
            String args = parts.length > 1 ? parts[1].trim() : "";

            switch (command) {
                case "/reproducir":
                     if (args.isEmpty()) { System.out.println("Uso: /reproducir <nombre_archivo>"); return; }
                     if (audioService.isAudioDownloaded(args)) {
                         audioService.playAudio(args);
                     } else {
                         System.out.println(">> El archivo no está local. Solicitando al servidor...");
                         request.addProperty("command", "request_audio");
                         request.addProperty("file_name", args);
                         out.println(gson.toJson(request));
                     }
                    return; 
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
                case "/llamar":
                    if (args.isEmpty()) { System.out.println("Uso: /llamar <usuario>"); return; }
                    // El único lugar donde UserInputHandler cambia el estado
                    chatClient.setCurrentState(ClientState.OUTGOING_CALL); 
                    request.addProperty("command", "call_request");
                    request.addProperty("callee", args);
                    break;
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
            fis.transferTo(socketOutStream);
            socketOutStream.flush();
        }
        System.out.println(">> Archivo de audio enviado.");
    }
    
    private void sendAcceptRequest() {
        String requester = chatClient.getIncomingCallFrom();
        if (requester != null) {
            JsonObject request = new JsonObject();
            request.addProperty("command", "call_accept");
            request.addProperty("requester", requester);
            out.println(gson.toJson(request));
        } else {
            System.out.println(">> No tienes ninguna llamada entrante para aceptar.");
        }
    }
    
    private void sendRejectRequest() {
        String requester = chatClient.getIncomingCallFrom();
        if (requester != null) {
            JsonObject request = new JsonObject();
            request.addProperty("command", "call_reject");
            request.addProperty("requester", requester);
            out.println(gson.toJson(request));
            // Volvemos a IDLE inmediatamente al rechazar, no necesitamos esperar al servidor
            chatClient.setCurrentState(ClientState.IDLE);
        } else {
            System.out.println(">> No tienes ninguna llamada entrante para rechazar.");
        }
    }

    private void sendHangupRequest() {
        callHandler.stopCall();
        JsonObject request = new JsonObject();
        request.addProperty("command", "call_hangup");
        out.println(gson.toJson(request));
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
        System.out.println("/llamar <usuario>");
        System.out.println("/exit");
        System.out.println("--------------------------");
    }
}

