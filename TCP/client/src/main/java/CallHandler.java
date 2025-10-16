import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class CallHandler {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_UDP_PORT = 12346;
    
    private final Client chatClient;
    private final AudioService audioService;
    private volatile boolean isInCall = false;
    private DatagramSocket socket;

    public CallHandler(Client chatClient, AudioService audioService) {
        this.chatClient = chatClient;
        this.audioService = audioService;
    }

    public void startCall() {
        if (isInCall) return;
        isInCall = true;

        try {
            socket = new DatagramSocket(); 
            
            new Thread(() -> {
                try {
                    byte[] helloData = ("hello:" + chatClient.getUsername()).getBytes();
                    DatagramPacket helloPacket = new DatagramPacket(helloData, helloData.length, InetAddress.getByName(SERVER_ADDRESS), SERVER_UDP_PORT);
                    socket.send(helloPacket);

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

            new Thread(() -> {
                byte[] buffer = new byte[1024];
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
            
        } catch (Exception e) {
            System.err.println("Error al iniciar la llamada: " + e.getMessage());
            isInCall = false;
        }
    }

    public void stopCall() {
        if (!isInCall) return;
        isInCall = false;
        if (socket != null) {
            socket.close();
        }
        audioService.stopCallAudio();
        System.out.println("\n>> Llamada finalizada.");
    }

    public boolean isInCall() {
        return isInCall;
    }
}

