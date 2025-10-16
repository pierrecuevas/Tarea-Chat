import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

public class CallHandler {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_UDP_PORT = 12346;
    
    private final Client chatClient;
    private final AudioService audioService;
    private volatile boolean isInCall = false;
    private final DatagramSocket socket;

    public CallHandler(Client chatClient, AudioService audioService) {
        this.chatClient = chatClient;
        this.audioService = audioService;
        DatagramSocket tempSocket = null;
        try {
            // Se crea el socket una sola vez al instanciar la clase
            tempSocket = new DatagramSocket(); 
        } catch (SocketException e) {
            System.err.println("Error fatal: No se pudo crear el socket UDP. Las llamadas no funcionarán.");
        }
        this.socket = tempSocket;
    }

    // Nuevo método para ser llamado después del login
    public void registerWithServer() {
        if (socket == null) return;
        new Thread(() -> {
            try {
                byte[] helloData = ("hello:" + chatClient.getUsername()).getBytes();
                DatagramPacket helloPacket = new DatagramPacket(helloData, helloData.length, InetAddress.getByName(SERVER_ADDRESS), SERVER_UDP_PORT);
                socket.send(helloPacket);
            } catch (Exception e) {
                System.err.println("Error al registrar la dirección UDP: " + e.getMessage());
            }
        }).start();
    }

    public void startCall() {
        if (isInCall || socket == null) return;
        isInCall = true;

        // Hilo para enviar audio
        new Thread(() -> {
            try {
                while (isInCall) {
                    byte[] audioData = audioService.captureAudioForCall();
                    if (audioData != null) {
                        DatagramPacket packet = new DatagramPacket(audioData, audioData.length, InetAddress.getByName(SERVER_ADDRESS), SERVER_UDP_PORT);
                        socket.send(packet);
                    }
                }
            } catch (Exception e) {
                if(isInCall) System.err.println("Error en el hilo de envío de llamada: " + e.getMessage());
            }
        }).start();

        // Hilo para recibir audio
        new Thread(() -> {
            byte[] buffer = new byte[AudioService.CALL_BUFFER_SIZE];
            while (isInCall) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    audioService.playAudioFromCall(packet.getData(), packet.getLength());
                } catch (Exception e) {
                    if(isInCall) System.err.println("Error en el hilo de recepción de llamada: " + e.getMessage());
                }
            }
        }).start();
    }

    public void stopCall() {
        if (!isInCall) return;
        isInCall = false;
        audioService.stopCallAudio();
        System.out.println("\n>> Llamada finalizada.");
    }
    
    // Método para cerrar el socket cuando el cliente se desconecta
    public void closeSocket() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    public boolean isInCall() {
        return isInCall;
    }
}
