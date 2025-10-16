import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Socket;

public class ServerListener implements Runnable {
    
    private final Client chatClient;
    private final InputStream inStream;
    private final BufferedReader inReader;
    private final Gson gson = new Gson();
    private final AudioService audioService; 
    private final CallHandler callHandler;

    public ServerListener(Client chatClient, Socket socket, AudioService audioService, CallHandler callHandler) throws IOException {
        this.chatClient = chatClient;
        this.inStream = socket.getInputStream();
        this.inReader = new BufferedReader(new InputStreamReader(this.inStream));
        this.audioService = audioService;
        this.callHandler = callHandler;
    }

    @Override
    public void run() {
        try {
            String serverJson;
            while ((serverJson = inReader.readLine()) != null) {
                handleServerMessage(serverJson);
            }
        } catch (IOException e) {
            // Se espera cuando el cliente se desconecta
        }
    }

    private void handleServerMessage(String serverJson) {
        // Borra la línea actual del prompt del usuario antes de imprimir el nuevo mensaje
        System.out.print("\r" + " ".repeat(100) + "\r");
        
        try {
            JsonObject json = gson.fromJson(serverJson, JsonObject.class);
            String type = json.has("type") ? json.get("type").getAsString() : "unknown";

            switch (type) {
                case "audio_transfer":
                    handleAudioTransfer(json);
                    break;
                case "chat":
                    formatAndPrintChatMessage(json);
                    break;
                case "notification":
                    handleNotification(json);
                    break;
                case "call_request":
                    handleCallRequest(json);
                    break;
                case "call_accepted":
                    handleCallAccepted();
                    break;
                case "call_rejected":
                    handleCallRejected(json);
                    break;
                case "call_ended":
                    handleCallEnded();
                    break;
                default:
                    System.out.println("<- " + serverJson);
                    break;
            }
        } catch (JsonSyntaxException e) {
            System.out.println("<- " + serverJson);
        }
        
        // Reimprime el prompt del usuario, que ahora reflejará el estado actualizado
        System.out.print(getPrompt());
    }
    
    private void handleAudioTransfer(JsonObject json) {
        long fileSize = json.get("file_size").getAsLong();
        String fileName = json.get("file_name").getAsString();
        audioService.saveDownloadedAudio(fileName, inStream, fileSize);
    }
    
    private void handleNotification(JsonObject json) {
        String message = json.get("message").getAsString();
        System.out.println(">> " + message);
        if (message.startsWith("No se pudo establecer la llamada")) {
            chatClient.setCurrentState(ClientState.IDLE);
        }
    }

    private void handleCallRequest(JsonObject json) {
        String requester = json.get("from").getAsString();
        chatClient.setIncomingCallFrom(requester);
        chatClient.setCurrentState(ClientState.INCOMING_CALL);
        System.out.println("\n>> Llamada entrante de " + requester + ". Usa /aceptar o /rechazar.");
    }
    
    private void handleCallAccepted() {
        System.out.println(">> Llamada aceptada. ¡Conectando!");
        chatClient.setIncomingCallFrom(null);
        chatClient.setCurrentState(ClientState.IN_CALL);
        callHandler.startCall();
    }
    
    private void handleCallRejected(JsonObject json) {
        String user = json.get("user").getAsString();
        System.out.println(">> " + user + " ha rechazado la llamada.");
        chatClient.setCurrentState(ClientState.IDLE);
    }

    private void handleCallEnded() {
        // Solo si no estamos ya en IDLE, para evitar mensajes duplicados
        if(chatClient.getCurrentState() != ClientState.IDLE) {
            callHandler.stopCall();
            chatClient.setIncomingCallFrom(null);
            chatClient.setCurrentState(ClientState.IDLE);
            System.out.println("\n>> La llamada ha finalizado.");
        }
    }

    private String getPrompt() {
        ClientState state = chatClient.getCurrentState();
        switch (state) {
            case OUTGOING_CALL: return ">> Llamando... (escribe /colgar para cancelar)\n> ";
            case INCOMING_CALL: return ">> Llamada entrante de " + chatClient.getIncomingCallFrom() + ". (/aceptar o /rechazar)\n> ";
            case IN_CALL: return ">> Llamada en curso... (escribe /colgar para finalizar)\n> ";
            default: return String.format("[%s]> ", chatClient.getCurrentChatContext());
        }
    }

    private void formatAndPrintChatMessage(JsonObject json) {
        String subType = json.get("sub_type").getAsString();
        String text = json.get("text").getAsString();
        String formattedMessage;

        switch (subType) {
            case "public":
                formattedMessage = String.format("[General] %s: %s", json.get("sender").getAsString(), text);
                break;
            case "group":
                formattedMessage = String.format("[%s] %s: %s", json.get("group").getAsString(), json.get("sender").getAsString(), text);
                break;
            case "private_from":
                formattedMessage = String.format("[Privado de %s]: %s", json.get("sender").getAsString(), text);
                break;
            case "private_to":
                formattedMessage = String.format("[Privado para %s]: %s", json.get("party").getAsString(), text);
                break;
            case "public_audio":
                 formattedMessage = String.format("[General] %s envió una nota de voz: %s", json.get("sender").getAsString(), text);
                break;
            case "group_audio":
                 formattedMessage = String.format("[%s] %s envió una nota de voz: %s", json.get("group").getAsString(), json.get("sender").getAsString(), text);
                break;
            case "private_audio_from":
                // Mensaje para el receptor
                String sender = json.get("sender").getAsString();
                String fileName = text;
                formattedMessage = String.format(">> ¡Nota de voz de %s recibida! Archivo: %s (Usa /reproducir %s)", sender, fileName, fileName);
                break;
            case "private_audio_to":
                 // Mensaje de confirmación para el emisor 
                String recipient = json.get("party").getAsString();
                String sentFileName = text;
                formattedMessage = String.format(">> Nota de voz enviada a %s. Archivo: %s", recipient, sentFileName);
                break;
            default:
                formattedMessage = text;
                break;
        }
        System.out.println(formattedMessage);
    }
}

