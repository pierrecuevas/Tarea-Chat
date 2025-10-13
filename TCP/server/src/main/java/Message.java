import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Representa un solo mensaje en el chat.
 * Esta clase se convertirá a JSON para ser almacenada.
 */
public class Message {
    private String type; // "private_message", "group_message", "audio_note"
    private String sender;
    private String recipient; // Puede ser un username o un groupname
    private String content; // El texto del mensaje o la ruta al archivo de audio
    private String timestamp;

    public Message(String type, String sender, String recipient, String content) {
        this.type = type;
        this.sender = sender;
        this.recipient = recipient;
        this.content = content;
        // Guarda la fecha y hora actual en un formato legible
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    // Getters son necesarios para que GSON pueda serializar el objeto
    public String getSender() {
        return sender;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getContent() {
        return content;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getType() {
        return type;
    }
}
