import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Socket;

/**
 * Listens for incoming messages from the server in a dedicated thread.
 * It parses the JSON messages and formats them for display in the console.
 */
public class ServerListener implements Runnable {
    
    private final Client chatClient;
    private final InputStream inStream;
    private final BufferedReader inReader;
    private final Gson gson = new Gson();
    private final AudioService audioService; 

    public ServerListener(Client chatClient, Socket socket, AudioService audioService) throws IOException {
        this.chatClient = chatClient;
        this.inStream = socket.getInputStream();
        this.inReader = new BufferedReader(new InputStreamReader(this.inStream));
        this.audioService = audioService;
    }

    @Override
    public void run() {
        try {
            String serverJson;
            while ((serverJson = inReader.readLine()) != null) {
                handleServerMessage(serverJson);
            }
        } catch (IOException e) {
            // This is expected when the client disconnects.
        }
    }

    private void handleServerMessage(String serverJson) {
        try {
            // Erase the current line (prompt) before printing the new message
            System.out.print("\r" + " ".repeat(chatClient.getCurrentChatContext().length() + 50) + "\r");
            
            JsonObject json = gson.fromJson(serverJson, JsonObject.class);
            String type = json.has("type") ? json.get("type").getAsString() : "unknown";

            if ("audio_transfer".equals(type)) {
                long fileSize = json.get("file_size").getAsLong();
                String fileName = json.get("file_name").getAsString();
                audioService.saveDownloadedAudio(fileName, inStream, fileSize);
            } else if ("chat".equals(type)) {
                formatAndPrintChatMessage(json);
            } else if ("notification".equals(type)) {
                System.out.println(">> " + json.get("message").getAsString());
            } else {
                System.out.println("<- " + serverJson);
            }
            
            // Reprint the user's prompt
            System.out.print(String.format("[%s]> ", chatClient.getCurrentChatContext()));

        } catch (JsonSyntaxException e) {
            // Handle plain text messages from the server (like history headers)
            System.out.println("<- " + serverJson);
            System.out.print(String.format("[%s]> ", chatClient.getCurrentChatContext()));
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
                formattedMessage = String.format("[Privado de %s] Nota de voz: %s", json.get("sender").getAsString(), text);
                break;
            case "private_audio_to":
                formattedMessage = String.format("[Privado para %s] Nota de voz: %s", json.get("party").getAsString(), text);
                break;
            default:
                formattedMessage = text;
                break;
        }
        System.out.println(formattedMessage);
    }
}

