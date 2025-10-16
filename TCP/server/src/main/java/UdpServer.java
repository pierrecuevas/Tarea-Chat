import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.InetAddress;

public class UdpServer implements Runnable {
    private static final int UDP_PORT = 12346;
    private final CallManager callManager;

    public UdpServer(CallManager callManager) {
        this.callManager = callManager;
    }

    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket(UDP_PORT, InetAddress.getByName("0.0.0.0"))) {
            System.out.println("Servidor UDP escuchando en el puerto " + UDP_PORT);
            byte[] buffer = new byte[1024];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String message = new String(packet.getData(), 0, packet.getLength());

                // Si es un paquete "hola", registra la dirección UDP del cliente.
                if (message.startsWith("hello:")) {
                    String username = message.substring(6);
                    callManager.registerUdpAddress(username, packet.getSocketAddress());
                } else {
                    // Si no, es un paquete de audio que debe ser reenviado.
                    SocketAddress destinationAddress = callManager.getCallPartnerAddress(packet.getSocketAddress());
                    if (destinationAddress != null) {
                        DatagramPacket forwardPacket = new DatagramPacket(
                            packet.getData(), packet.getLength(), destinationAddress
                        );
                        socket.send(forwardPacket);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error crítico en el servidor UDP: " + e.getMessage());
        }
    }
}

