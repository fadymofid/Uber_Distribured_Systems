import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class Client {
    private static BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
    private static BufferedReader in;
    private static PrintWriter out;
    // Shared flag indicating that the server confirmed disconnection.
    private static volatile boolean shouldDisconnect = false;

    public static void main(String[] args) {
        try {
            System.out.print("Enter server IP (default localhost): ");
            String ip = consoleReader.readLine();
            if (ip.isEmpty()) {
                ip = "localhost";
            }

            Socket socket = new Socket(ip, 12345);
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            // Start thread to listen for messages from the server.
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

            // Login or register.
            System.out.println("Do you want to (1) Login or (2) Register?");
            String choice = consoleReader.readLine();
            System.out.print("Enter username: ");
            String username = consoleReader.readLine();
            System.out.print("Enter password: ");
            String password = consoleReader.readLine();
            System.out.print("Enter type (customer/driver/admin): ");
            String type = consoleReader.readLine();

            if (choice.equals("2")) {
                out.println("REGISTER:" + username + ":" + password + ":" + type);
            } else {
                out.println("LOGIN:" + username + ":" + password + ":" + type);
            }

            // Main menu loop.
            while (true) {
                if (type.equalsIgnoreCase("customer")) {
                    System.out.println("\nCustomer Menu:");
                    System.out.println("1. Request a Ride");
                    System.out.println("2. Assign a Ride (choose an offer)");
                    System.out.println("3. Rate a Driver");
                    System.out.println("4. Disconnect");
                    System.out.print("Choice: ");
                    String input = consoleReader.readLine();
                    if (input.equals("1")) {
                        System.out.print("Enter pickup location: ");
                        String pickup = consoleReader.readLine();
                        System.out.print("Enter destination: ");
                        String destination = consoleReader.readLine();
                        out.println("REQUEST:" + pickup + ":" + destination);
                    } else if (input.equals("2")) {
                        System.out.print("Enter Ride ID: ");
                        String rideId = consoleReader.readLine();
                        System.out.print("Enter chosen driver username: ");
                        String driverUser = consoleReader.readLine();
                        out.println("ASSIGN:" + rideId + ":" + driverUser);
                    } else if (input.equals("3")) {
                        System.out.print("Enter Ride ID to rate: ");
                        String rideId = consoleReader.readLine();
                        System.out.print("Enter behaviour rating (1-5): ");
                        String behaviourRating = consoleReader.readLine();
                        System.out.print("Enter car rating (1-5): ");
                        String carRating = consoleReader.readLine();
                        System.out.print("Enter ride rating (1-5): ");
                        String rideRating = consoleReader.readLine();
                        System.out.print("Enter comment: ");
                        String comment = consoleReader.readLine();
                        out.println("RATE:" + rideId + ":" + behaviourRating + ":" + carRating + ":" + rideRating + ":" + comment);
                    } else if (input.equals("4")) {
                        out.println("DISCONNECT");
                        // Wait until server confirms disconnect (or error occurs and flag is not set)
                        Thread.sleep(500); // short pause to let response come in
                        if (shouldDisconnect) {
                            break;
                        }
                    } else {
                        System.out.println("Invalid option.");
                    }
                } else if (type.equalsIgnoreCase("driver")) {
                    System.out.println("\nDriver Menu:");
                    System.out.println("1. Send Offer for a Ride");
                    System.out.println("2. Update Ride Status (START/END)");
                    System.out.println("3. Disconnect");
                    System.out.print("Choice: ");
                    String input = consoleReader.readLine();
                    if (input.equals("1")) {
                        System.out.print("Enter Ride ID to offer: ");
                        String rideId = consoleReader.readLine();
                        System.out.print("Enter your fare offer: ");
                        String price = consoleReader.readLine();
                        out.println("OFFER:" + rideId + ":" + price);
                    } else if (input.equals("2")) {
                        System.out.print("Enter Ride ID to update: ");
                        String rideId = consoleReader.readLine();
                        System.out.print("Enter status (START/END): ");
                        String status = consoleReader.readLine();
                        out.println("UPDATE:" + rideId + ":" + status);
                    } else if (input.equals("3")) {
                        out.println("DISCONNECT");
                        Thread.sleep(500);
                        if (shouldDisconnect) {
                            break;
                        }
                    } else {
                        System.out.println("Invalid option.");
                    }
                } else if (type.equalsIgnoreCase("admin")) {
                    System.out.println("\nAdmin Menu:");
                    System.out.println("1. View System Statistics");
                    System.out.println("2. Disconnect");
                    System.out.print("Choice: ");
                    String input = consoleReader.readLine();
                    if (input.equals("1")) {
                        out.println("STATS");
                    } else if (input.equals("2")) {
                        out.println("DISCONNECT");
                        Thread.sleep(500);
                        if (shouldDisconnect) {
                            break;
                        }
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