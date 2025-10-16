import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class AuthHandler {
    private final BufferedReader in;
    private final PrintWriter out;
    private final BufferedReader userInput;
    private final Gson gson = new Gson();
    private String username;

    public AuthHandler(Socket socket) throws IOException {
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out = new PrintWriter(socket.getOutputStream(), true);
        this.userInput = new BufferedReader(new InputStreamReader(System.in));
    }

    public String authenticate() throws IOException {
        String serverMessage = in.readLine();
        if (serverMessage == null) return null;
        System.out.println("<- " + serverMessage);

        while (true) {
            System.out.print("Elige (login/register): ");
            String command = userInput.readLine();
            if (command == null) return null;

            if (!"login".equalsIgnoreCase(command.trim()) && !"register".equalsIgnoreCase(command.trim())) {
                System.out.println("Comando no válido.");
                continue;
            }

            System.out.print("Username: ");
            String user = userInput.readLine();
            System.out.print("Password: ");
            String pass = userInput.readLine();
            if (user == null || pass == null || user.trim().isEmpty() || pass.trim().isEmpty()) {
                System.out.println("Usuario y contraseña no pueden estar vacíos.");
                continue;
            }

            JsonObject request = new JsonObject();
            request.addProperty("command", command.trim());
            request.addProperty("username", user.trim());
            request.addProperty("password", pass.trim());
            out.println(gson.toJson(request));

            String serverResponse = in.readLine();
            if (serverResponse == null) return null;

            try {
                JsonObject responseJson = gson.fromJson(serverResponse, JsonObject.class);
                if (responseJson.has("status") && "ok".equals(responseJson.get("status").getAsString())) {
                    System.out.println("<- " + responseJson.get("message").getAsString());
                    this.username = user.trim();
                    return this.username; // Authentication successful
                } else {
                    System.out.println("<- Error: " + responseJson.get("message").getAsString());
                }
            } catch (JsonSyntaxException | NullPointerException e) {
                System.err.println("Respuesta inesperada del servidor: " + serverResponse);
            }
        }
    }
}

