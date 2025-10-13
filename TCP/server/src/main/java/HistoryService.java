import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Gestiona la lectura y escritura del historial de chat en un archivo JSON.
 */
public class HistoryService {
    private static final String HISTORY_FILE = "chat_history.json";
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final Type messageListType = new TypeToken<ArrayList<Message>>() {}.getType();

    /**
     * Guarda un mensaje en el archivo JSON.
     * El método es 'synchronized' para evitar que múltiples hilos escriban en el archivo
     * al mismo tiempo y lo corrompan.
     * @param message El mensaje a guardar.
     */
    public static synchronized void saveMessage(Message message) {
        try {
            List<Message> messages = readMessages();
            messages.add(message);
            try (FileWriter writer = new FileWriter(HISTORY_FILE)) {
                gson.toJson(messages, writer);
            }
        } catch (IOException e) {
            System.err.println("Error al guardar el mensaje en el historial: " + e.getMessage());
        }
    }

    /**
     * Lee todos los mensajes del archivo JSON.
     * @return Una lista de mensajes.
     */
    private static List<Message> readMessages() {
        try (FileReader reader = new FileReader(HISTORY_FILE)) {
            List<Message> messages = gson.fromJson(reader, messageListType);
            return messages != null ? messages : new ArrayList<>();
        } catch (IOException e) {
            // Si el archivo no existe, simplemente devuelve una lista vacía.
            return new ArrayList<>();
        }
    }
}
