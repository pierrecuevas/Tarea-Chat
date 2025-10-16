import java.net.SocketAddress;
import java.util.concurrent.ConcurrentHashMap;

public class CallManager {
    private final ConcurrentHashMap<String, SocketAddress> userUdpAddresses = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> activeCalls = new ConcurrentHashMap<>();

    public void registerUdpAddress(String username, SocketAddress udpAddress) {
        userUdpAddresses.put(username, udpAddress);
        System.out.println("Dirección UDP registrada para " + username + ": " + udpAddress);
    }

    public boolean startCall(String userA, String userB) {
        if (userUdpAddresses.containsKey(userA) && userUdpAddresses.containsKey(userB)) {
            activeCalls.put(userA, userB);
            activeCalls.put(userB, userA);
            System.out.println("Llamada iniciada entre " + userA + " y " + userB);
            return true;
        }
        return false;
    }

    public void endCall(String username) {
        String partner = activeCalls.remove(username);
        if (partner != null) {
            activeCalls.remove(partner);
            System.out.println("Llamada finalizada para " + username + " y " + partner);
        }
    }

    public SocketAddress getCallPartnerAddress(SocketAddress callerAddress) {
        String callerUsername = findUsernameByUdpAddress(callerAddress);
        if (callerUsername != null) {
            String partnerUsername = activeCalls.get(callerUsername);
            if (partnerUsername != null) {
                return userUdpAddresses.get(partnerUsername);
            }
        }
        return null;
    }
    
    public String getCallPartner(String username) {
        return activeCalls.get(username);
    }
    
    private String findUsernameByUdpAddress(SocketAddress address) {
        return userUdpAddresses.entrySet().stream()
            .filter(entry -> entry.getValue().equals(address))
            .map(java.util.Map.Entry::getKey)
            .findFirst()
            .orElse(null);
    }

    public void userDisconnected(String username) {
        endCall(username);
        userUdpAddresses.remove(username);
    }
}

