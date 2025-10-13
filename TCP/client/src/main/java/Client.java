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

    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in))) {
            
            System.out.println("Conectado al servidor.");

            // Hilo para escuchar al servidor
            Thread serverListener = new Thread(() -> {
                try {
                    String serverMessage;
                    while ((serverMessage = in.readLine()) != null) {
                        System.out.println("<- " + serverMessage);
                    }
                } catch (IOException e) {
                    System.out.println("Se ha perdido la conexión con el servidor.");
                }
            });
            serverListener.start();

            // Flujo de autenticación
            boolean loggedIn = false;
            while (!loggedIn) {
                String action = consoleReader.readLine(); // Espera la acción del usuario
                if (action == null) break;

                System.out.print("Username: ");
                String username = consoleReader.readLine();
                System.out.print("Password: ");
                String password = consoleReader.readLine();

                JsonObject authRequest = new JsonObject();
                authRequest.addProperty("command", action.trim().toLowerCase());
                authRequest.addProperty("username", username);
                authRequest.addProperty("password", password);
                out.println(gson.toJson(authRequest));
                
                // Esperar una respuesta simple del servidor
                String serverResponseJson = in.readLine();
                if (serverResponseJson == null) {
                    System.out.println("El servidor cerró la conexión.");
                    break;
                }
                
                try {
                    JsonObject response = gson.fromJson(serverResponseJson, JsonObject.class);
                    System.out.println("SERVER: " + response.get("message").getAsString());
                    if ("ok".equals(response.get("status").getAsString()) && "login".equals(action)) {
                        loggedIn = true;
                    }
                } catch(JsonSyntaxException e) {
                     System.out.println("Respuesta inesperada del servidor: " + serverResponseJson);
                }
            }

            // Bucle del chat principal
            System.out.println("--- Estás en el chat general. Escribe /msg <user> <text> para mensajes privados ---");
            String userInput;
            while (serverListener.isAlive() && (userInput = consoleReader.readLine()) != null) {
                if (userInput.equalsIgnoreCase("/salir")) {
                    break;
                }
                processChatInput(userInput, out);
            }

        } catch (IOException e) {
            System.err.println("Error de cliente: " + e.getMessage());
        } finally {
            System.out.println("Cliente cerrado.");
        }
    }

    private static void processChatInput(String input, PrintWriter out) {
        JsonObject jsonRequest = new JsonObject();
        if (input.startsWith("/msg")) {
            String[] parts = input.split(" ", 3);
            if (parts.length < 3) {
                System.out.println("Uso: /msg <destinatario> <mensaje>");
                return;
            }
            jsonRequest.addProperty("command", "private_msg");
            jsonRequest.addProperty("recipient", parts[1]);
            jsonRequest.addProperty("text", parts[2]);
        } else {
            // Cualquier cosa que no sea un comando, es un mensaje público
            jsonRequest.addProperty("command", "public_msg");
            jsonRequest.addProperty("text", input);
        }
        out.println(gson.toJson(jsonRequest));
    }
}

