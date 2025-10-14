import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class Client {

    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 12345;
    private static final Gson gson = new Gson();
    private static String currentChatContext = "General";

    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in))) {

            System.out.println("Conectado al servidor.");

            if (handleAuthentication(in, out, userInput)) {
                new Thread(new ServerListener(in)).start();
                handleUserInput(out, userInput);
            }

        } catch (IOException e) {
            System.err.println("No se pudo conectar al servidor: " + e.getMessage());
        }
    }

    private static boolean handleAuthentication(BufferedReader in, PrintWriter out, BufferedReader userInput) throws IOException {
        String serverMessage = in.readLine();
        System.out.println("<- " + serverMessage);

        while (true) {
            System.out.print("Elige (login/register): ");
            String command = userInput.readLine();

            if (!"login".equalsIgnoreCase(command) && !"register".equalsIgnoreCase(command)) {
                System.out.println("Comando no válido.");
                continue;
            }

            System.out.print("Username: ");
            String username = userInput.readLine();
            System.out.print("Password: ");
            String password = userInput.readLine();

            JsonObject request = new JsonObject();
            request.addProperty("command", command);
            request.addProperty("username", username);
            request.addProperty("password", password);
            out.println(gson.toJson(request));

            String serverResponse = in.readLine();
            if (serverResponse == null) return false;

            try {
                JsonObject responseJson = gson.fromJson(serverResponse, JsonObject.class);
                if (responseJson.has("status") && "ok".equals(responseJson.get("status").getAsString())) {
                    System.out.println("<- " + responseJson.get("message").getAsString());
                    return true;
                } else {
                    System.out.println("<- Error: " + responseJson.get("message").getAsString());
                }
            } catch (JsonSyntaxException | NullPointerException e) {
                System.err.println("Respuesta inesperada del servidor durante la autenticación: " + serverResponse);
            }
        }
    }

    private static void handleUserInput(PrintWriter out, BufferedReader userInput) throws IOException {
        String line;
        System.out.println("\n--- Comandos Disponibles ---");
        System.out.println("/crear <grupo>");
        System.out.println("/invitar <grupo> <usuario>");
        System.out.println("/chat <grupo>  - Cambia al chat del grupo");
        System.out.println("/general     - Vuelve al chat general");
        System.out.println("/historial <usuario> - Muestra chat privado");
        System.out.println("/msg <usuario> <mensaje>");
        System.out.println("/exit");
        System.out.println("--------------------------");

        while (true) {
            System.out.print(String.format("[%s]> ", currentChatContext));
            line = userInput.readLine();
            if (line == null || "/exit".equalsIgnoreCase(line)) {
                break;
            }

            JsonObject request = new JsonObject();
            if (line.startsWith("/")) {
                String[] parts = line.split(" ", 3);
                String command = parts[0];
                switch (command) {
                    case "/crear":
                        request.addProperty("command", "create_group");
                        request.addProperty("group_name", parts[1]);
                        break;
                    case "/invitar":
                        request.addProperty("command", "invite_to_group");
                        request.addProperty("group_name", parts[1]);
                        request.addProperty("user_to_invite", parts[2]);
                        break;
                    case "/chat":
                        currentChatContext = parts[1];
                        request.addProperty("command", "get_group_history");
                        request.addProperty("group_name", parts[1]);
                        break;
                    case "/general":
                        currentChatContext = "General";
                        System.out.println("Cambiado al chat General.");
                        continue;
                    case "/msg":
                        request.addProperty("command", "private_message");
                        request.addProperty("recipient", parts[1]);
                        request.addProperty("text", parts[2]);
                        break;
                    case "/historial":
                        request.addProperty("command", "get_private_history");
                        request.addProperty("with_user", parts[1]);
                        break;
                    default:
                        System.out.println("Comando desconocido.");
                        continue;
                }
            } else {
                if ("General".equals(currentChatContext)) {
                    request.addProperty("command", "public_message");
                } else {
                    request.addProperty("command", "group_message");
                    request.addProperty("group_name", currentChatContext);
                }
                request.addProperty("text", line);
            }
            out.println(gson.toJson(request));
        }
    }

    private static class ServerListener implements Runnable {
        private final BufferedReader in;
        public ServerListener(BufferedReader in) { this.in = in; }

        @Override
        public void run() {
            try {
                String serverMessage;
                while ((serverMessage = in.readLine()) != null) {
                    try {
                        JsonObject json = gson.fromJson(serverMessage, JsonObject.class);
                        String type = json.has("type") ? json.get("type").getAsString() : "unknown";
                        
                        System.out.print("\r" + " ".repeat(String.format("[%s]> ", currentChatContext).length() + 50) + "\r");

                        if("chat".equals(type)) {
                            // CAMBIO: Lógica de formato movida aquí.
                            String subType = json.get("sub_type").getAsString();
                            String text = json.get("text").getAsString();
                            String formattedMessage;

                            switch (subType) {
                                case "public":
                                    formattedMessage = String.format("[General] %s: %s", json.get("party").getAsString(), text);
                                    break;
                                case "group":
                                    formattedMessage = String.format("[%s] %s: %s", json.get("group").getAsString(), json.get("sender").getAsString(), text);
                                    break;
                                case "private_from":
                                    formattedMessage = String.format("[Privado de %s]: %s", json.get("party").getAsString(), text);
                                    break;
                                case "private_to":
                                    formattedMessage = String.format("[Privado para %s]: %s", json.get("party").getAsString(), text);
                                    break;
                                case "history":
                                    formattedMessage = text; // El historial ya viene pre-formateado
                                    break;
                                default:
                                    formattedMessage = text;
                                    break;
                            }
                            System.out.println(formattedMessage);
                        } else if ("notification".equals(type)){
                            System.out.println(">> " + json.get("message").getAsString());
                        } else {
                            System.out.println("<- " + serverMessage);
                        }
                        
                        System.out.print(String.format("[%s]> ", currentChatContext));

                    } catch (JsonSyntaxException e){
                        System.out.println("<- " + serverMessage);
                    }
                }
            } catch (IOException e) {
                System.out.println("\nDesconectado del servidor.");
            }
        }
    }
}

