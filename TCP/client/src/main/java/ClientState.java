/**
 * Representa los diferentes estados en los que puede estar el cliente,
 * principalmente para gestionar la lógica de las llamadas.
 */
public enum ClientState {
    IDLE,           // Estado normal, chateando.
    OUTGOING_CALL,  // El usuario ha escrito /llamar y está esperando respuesta.
    INCOMING_CALL,  // El usuario está recibiendo una llamada de otro.
    IN_CALL         // La llamada ha sido aceptada y está activa.
}
