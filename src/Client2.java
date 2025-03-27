import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class Client {
    private static BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
    private static BufferedReader in;
    private static PrintWriter out;
    private static volatile boolean shouldDisconnect = false;

    public static void main(String[] args) {
        try {
            System.out.print("Enter server IP (default localhost): ");
            String ip = consoleReader.readLine().trim();
            if (ip.isEmpty()) {
                ip = "localhost";
            }
            Socket socket = new Socket(ip, 12345);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            String userType = null; // will be assigned after successful login
            boolean authenticated = false;

            // Authentication loop.
            while (!authenticated) {
                System.out.println("\nDo you want to (1) Login or (2) Register?");
                String choice = consoleReader.readLine().trim();
                System.out.print("Enter username: ");
                String username = consoleReader.readLine().trim();
                System.out.print("Enter password: ");
                String password = consoleReader.readLine().trim();

                if (choice.equals("2")) {
                    System.out.print("Enter type (customer/driver): ");
                    String type = consoleReader.readLine().trim();
                    out.println("REGISTER:" + username + ":" + password + ":" + type);
                } else {
                    out.println("LOGIN:" + username + ":" + password);
                }

                // Read responses until we get a meaningful login response.
                String authResponse = null;
                while ((authResponse = in.readLine()) != null) {
                    // Skip over registration extra messages.
                    if (authResponse.startsWith("REGISTERED:") || authResponse.startsWith("INFO:")) {
                        System.out.println("SERVER: " + authResponse);
                        // If this was a registration, prompt user to log in.
                        if (authResponse.startsWith("REGISTERED:")) {
                            System.out.println("Registration successful. Please log in.\n");
                        }
                        // Continue reading for a proper login response.
                        continue;
                    } else if (authResponse.startsWith("LOGGEDIN:")) {
                        // Expected format: LOGGEDIN:username:type
                        String[] parts = authResponse.split(":");
                        if (parts.length >= 3) {
                            userType = parts[2];
                        } else {
                            userType = "customer"; // fallback
                        }
                        System.out.println("SERVER: " + authResponse);
                        System.out.println("Login successful as " + userType.toUpperCase() + ".\n");
                        authenticated = true;
                        break;
                    } else {
                        // If an error message is received, print it and break out to try again.
                        System.out.println("SERVER: " + authResponse);
                        break;
                    }
                }
                if (!authenticated) {
                    System.out.println("Authentication failed. Please try again.\n");
                }
            }

            // Start listener thread after authentication.
            Thread listenerThread = new Thread(() -> {
                try {
                    String response;
                    while ((response = in.readLine()) != null) {
                        System.out.println("SERVER: " + response);
                        if(response.startsWith("DISCONNECTING")) {
                            shouldDisconnect = true;
                        }
                    }
                } catch(IOException e) {
                    System.out.println("Disconnected from server.");
                }
            });
            listenerThread.start();

            // Display the main menu.
            System.out.println("=== Main Menu ===");

            // Main menu loop based on user type.
            while (true) {
                if (userType.equalsIgnoreCase("customer")) {
                    System.out.println("\n--- Customer Menu ---");
                    System.out.println("1. Request a Ride");
                    System.out.println("2. View Ride Status");
                    System.out.println("3. Assign a Ride (choose an offer)");
                    System.out.println("4. Rate a Driver");
                    System.out.println("5. Disconnect");
                    System.out.print("Choice: ");
                    String input = consoleReader.readLine().trim();
                    if (input.equals("1")) {
                        System.out.print("Enter pickup location: ");
                        String pickup = consoleReader.readLine().trim();
                        System.out.print("Enter destination: ");
                        String destination = consoleReader.readLine().trim();
                        out.println("REQUEST:" + pickup + ":" + destination);
                    } else if (input.equals("2")) {
                        out.println("VIEW");
                    } else if (input.equals("3")) {
                        System.out.print("Enter Ride ID: ");
                        String rideId = consoleReader.readLine().trim();
                        System.out.print("Enter chosen driver username: ");
                        String driverUser = consoleReader.readLine().trim();
                        out.println("ASSIGN:" + rideId + ":" + driverUser);
                    } else if (input.equals("4")) {
                        System.out.print("Enter Ride ID to rate: ");
                        String rideId = consoleReader.readLine().trim();
                        System.out.print("Enter behaviour rating (1-5): ");
                        String behaviourRating = consoleReader.readLine().trim();
                        System.out.print("Enter car rating (1-5): ");
                        String carRating = consoleReader.readLine().trim();
                        System.out.print("Enter ride rating (1-5): ");
                        String rideRating = consoleReader.readLine().trim();
                        System.out.print("Enter comment: ");
                        String comment = consoleReader.readLine().trim();
                        out.println("RATE:" + rideId + ":" + behaviourRating + ":" + carRating + ":" + rideRating + ":" + comment);
                    } else if (input.equals("5")) {
                        out.println("DISCONNECT");
                        Thread.sleep(500);
                        if (shouldDisconnect) break;
                    } else {
                        System.out.println("Invalid option.");
                    }
                } else if (userType.equalsIgnoreCase("driver")) {
                    System.out.println("\n--- Driver Menu ---");
                    System.out.println("1. Send Offer for a Ride");
                    System.out.println("2. Update Ride Status (START/END)");
                    System.out.println("3. Disconnect");
                    System.out.print("Choice: ");
                    String input = consoleReader.readLine().trim();
                    if (input.equals("1")) {
                        System.out.print("Enter Ride ID to offer: ");
                        String rideId = consoleReader.readLine().trim();
                        System.out.print("Enter your fare offer: ");
                        String price = consoleReader.readLine().trim();
                        out.println("OFFER:" + rideId + ":" + price);
                    } else if (input.equals("2")) {
                        System.out.print("Enter Ride ID to update: ");
                        String rideId = consoleReader.readLine().trim();
                        System.out.print("Enter status (START/END): ");
                        String status = consoleReader.readLine().trim();
                        out.println("UPDATE:" + rideId + ":" + status);
                    } else if (input.equals("3")) {
                        out.println("DISCONNECT");
                        Thread.sleep(500);
                        if (shouldDisconnect) break;
                    } else {
                        System.out.println("Invalid option.");
                    }
                } else if (userType.equalsIgnoreCase("admin")) {
                    System.out.println("\n--- Admin Menu ---");
                    System.out.println("1. View System Statistics");
                    System.out.println("2. Disconnect");
                    System.out.print("Choice: ");
                    String input = consoleReader.readLine().trim();
                    if (input.equals("1")) {
                        out.println("STATS");
                    } else if (input.equals("2")) {
                        out.println("DISCONNECT");
                        Thread.sleep(500);
                        if (shouldDisconnect) break;
                    } else {
                        System.out.println("Invalid option.");
                    }
                }
            }

            System.out.println("Disconnecting...");
            socket.close();
        } catch (IOException | InterruptedException e) {
            System.err.println("Client error: " + e.getMessage());
        }
    }
}
