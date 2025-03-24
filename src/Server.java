import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Server {

    // Shared lists for registered users and ongoing rides
    public static List<User> users = Collections.synchronizedList(new ArrayList<>());
    public static List<ClientHandler> drivers = Collections.synchronizedList(new ArrayList<>());
    public static List<ClientHandler> customers = Collections.synchronizedList(new ArrayList<>());
    public static List<Ride> rides = Collections.synchronizedList(new ArrayList<>());

    // Pre-defined admin user is created here.
    static {
        // Admin credentials: username "admin", password "admin123", type "admin"
        users.add(new User("admin", "admin123", "admin"));
    }

    public static void main(String[] args) {
        int port = 12345;
        System.out.println("Server starting on port " + port);
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New connection from " + clientSocket.getInetAddress());
                ClientHandler handler = new ClientHandler(clientSocket);
                Thread t = new Thread(handler);
                t.start();
            }
        } catch (IOException e) {
            System.err.println("Server exception: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
