import com.zeroc.Ice.Current;

import demo.VoiceChat;
import demo.VoiceMessageInfo;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VoiceChatI implements VoiceChat {
    private static final String AUDIO_DIR = "server_audio_files";
    private final Map<String, String> activeCalls = new ConcurrentHashMap<>();

    public VoiceChatI() {
        // Create audio directory if it doesn't exist
        try {
            Files.createDirectories(Paths.get(AUDIO_DIR));
        } catch (IOException e) {
            System.err.println("Failed to create audio directory: " + e.getMessage());
        }
    }

    @Override
    public void sendAudio(byte[] data, Current current) {
        // For real-time call audio streaming
        String sender = current.id.name;
        String recipient = activeCalls.get(sender);

        if (recipient != null) {
            try {
                ChatController.getInstance().streamAudioToUser(recipient, data);
            } catch (Exception e) {
                System.err.println("Error relaying audio: " + e.getMessage());
            }
        }
    }

    @Override
    public String sendVoiceMessage(byte[] data, VoiceMessageInfo info, Current current) {
        try {
            // Generate unique filename
            String filename = String.format("%s_%s_%d.webm",
                    info.sender,
                    info.recipient.replace("/", "_"),
                    System.currentTimeMillis());

            Path filePath = Paths.get(AUDIO_DIR, filename);

            // Save audio data to file
            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                fos.write(data);
            }

            System.out.println("Saved voice message: " + filename +
                    " (" + data.length + " bytes, " + info.duration + "ms)");

            // Notify recipient and save metadata via ChatController
            try {
                ChatController.getInstance().processAudioMessage(info.sender, info.recipient, filePath.toString());
            } catch (Exception e) {
                System.err.println("Error notifying ChatController: " + e.getMessage());
            }

            return filename;

        } catch (IOException e) {
            System.err.println("Error saving voice message: " + e.getMessage());
            return null;
        }
    }

    @Override
    public void initiateCall(String recipient, Current current) {
        String caller = current.id.name; // Get caller from ICE context
        System.out.println("Call initiated: " + caller + " -> " + recipient);
        activeCalls.put(caller, recipient);

        try {
            ChatController.getInstance().requestCall(caller, recipient);
        } catch (Exception e) {
            System.err.println("Error initiating call via ChatController: " + e.getMessage());
        }
    }

    @Override
    public void acceptCall(String caller, Current current) {
        String recipient = current.id.name;
        System.out.println("Call accepted: " + caller + " <-> " + recipient);
        activeCalls.put(recipient, caller); // Add reverse mapping for bidirectional audio

        try {
            ChatController.getInstance().acceptCall(recipient, caller);
        } catch (Exception e) {
            System.err.println("Error accepting call via ChatController: " + e.getMessage());
        }
    }

    @Override
    public void rejectCall(String caller, Current current) {
        String recipient = current.id.name;
        System.out.println("Call rejected: " + caller + " X " + recipient);
        activeCalls.remove(caller);

        try {
            ChatController.getInstance().rejectCall(recipient, caller);
        } catch (Exception e) {
            System.err.println("Error rejecting call via ChatController: " + e.getMessage());
        }
    }

    @Override
    public void endCall(String participant, Current current) {
        System.out.println("Call ended with: " + participant);
        activeCalls.remove(participant);
        activeCalls.remove(current.id.name);

        try {
            ChatController.getInstance().endCall(current.id.name);
        } catch (Exception e) {
            System.err.println("Error ending call via ChatController: " + e.getMessage());
        }
    }

}