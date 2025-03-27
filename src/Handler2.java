import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private User user; // The logged-in user
    private boolean running = true;

    // For driver clients: indicates if they are busy with a ride.
    private boolean busy = false;

    // For drivers: track the ride ID for which an offer has been sent.
    // -1 means no current pending offer.
    private int currentOfferRideId = -1;

    public ClientHandler(Socket socket) {
        this.socket = socket;
        try {
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
        } catch(IOException e) {
            System.err.println("ClientHandler error: " + e.getMessage());
        }
    }

    public String getUserName() {
        return user != null ? user.getUsername() : "unknown";
    }

    public boolean isBusy() {
        return busy;
    }

    public void setBusy(boolean busy) {
        this.busy = busy;
    }

    public int getCurrentOfferRideId() {
        return currentOfferRideId;
    }

    public void clearCurrentOffer() {
        this.currentOfferRideId = -1;
    }

    public void setCurrentOfferRideId(int rideId) {
        this.currentOfferRideId = rideId;
    }

    public void sendMessage(String msg) {
        out.println(msg);
    }

    @Override
    public void run() {
        try {
            // Authentication loop: remains until a successful login.
            while (user == null && running) {
                String authMsg = in.readLine();
                if (authMsg == null) break;
                // Expected formats:
                // Registration: REGISTER:username:password:type
                // Login: LOGIN:username:password
                String[] tokens = authMsg.split(":", -1);
                if (tokens.length < 3) {
                    out.println("ERROR: Invalid authentication format. Please try again.");
                    continue;
                }
                String command = tokens[0];
                String username = tokens[1];
                String password = tokens[2];

                if (command.equalsIgnoreCase("REGISTER")) {
                    if (tokens.length < 4) {
                        out.println("ERROR: Registration requires type. Please try again.");
                        continue;
                    }
                    String type = tokens[3];
                    if (type.equalsIgnoreCase("admin")) {
                        out.println("ERROR: Cannot register as admin.");
                        continue;
                    }
                    // Enforce strict type: only "driver" is accepted; anything else defaults to "customer".
                    if (!type.equalsIgnoreCase("driver")) {
                        type = "customer";
                    }
                    if (User.userExists(username)) {
                        out.println("ERROR: Username already exists.");
                        continue;
                    }
                    // Accept registration.
                    user = new User(username, password, type);
                    Server.users.add(user);
                    System.out.println("REGISTERED:" + username);
                    addToRoleList();
                    out.println("REGISTERED:" + username);
                    out.println("INFO: Registration successful. Please log in.");
                    // Reset user to force login.
                    user = null;
                    continue;
                } else if (command.equalsIgnoreCase("LOGIN")) {
                    User found = User.authenticate(username, password);
                    if (found == null) {
                        out.println("ERROR: Invalid credentials.");
                        continue;
                    } else {
                        user = found;
                        System.out.println("LOGGEDIN:" + username + ":" + user.getType());
                        addToRoleList();
                        out.println("LOGGEDIN:" + username + ":" + user.getType());
                    }
                } else {
                    out.println("ERROR: Unknown authentication command.");
                    continue;
                }
            }

            // Main loop for handling commands after authentication.
            String line;
            while (running && (line = in.readLine()) != null) {
                System.out.println("From " + getUserName() + ": " + line);
                String[] tokens = line.split(":");
                if (tokens.length == 0) continue;
                String command = tokens[0];
                switch (command.toUpperCase()) {
                    case "REQUEST":
                        // Format: REQUEST:pickup:destination
                        if (user.getType().equalsIgnoreCase("customer")) {
                            // Check if customer already has an ongoing ride (status START)
                            boolean ongoingRide = Server.rides.stream()
                                    .anyMatch(r -> r.getCustomerHandler() == this && r.getStatus().equals("START"));
                            if (ongoingRide) {
                                out.println("ERROR: You are already in an ongoing ride. Cannot request a new ride.");
                                break;
                            }
                            if (tokens.length >= 3) {
                                String pickup = tokens[1];
                                String destination = tokens[2];
                                Ride ride = new Ride(pickup, destination, this);
                                Server.rides.add(ride);
                                out.println("REQUEST_RECEIVED:" + ride.getRideId());
                                // Broadcast to available drivers.
                                broadcastRideRequest(ride);
                            } else {
                                out.println("ERROR: Invalid REQUEST format. Provide pickup and destination.");
                            }
                        } else {
                            out.println("ERROR: Only customers can request rides.");
                        }
                        break;

                    case "VIEW":
                        // New command: VIEW - allows a customer to view their current ride status.
                        if (user.getType().equalsIgnoreCase("customer")) {
                            // Find the ride requested by this customer (if any).
                            Ride currentRide = Server.rides.stream()
                                    .filter(r -> r.getCustomerHandler() == this)
                                    .findFirst().orElse(null);
                            if (currentRide != null) {
                                out.println("STATUS:" + currentRide.getRideId() + ":" + currentRide.getStatus());
                            } else {
                                out.println("INFO: No current ride.");
                            }
                        } else {
                            out.println("ERROR: Only customers can view ride status.");
                        }
                        break;

                    case "OFFER":
                        // Format: OFFER:rideId:price
                        if (user.getType().equalsIgnoreCase("driver")) {
                            if (currentOfferRideId != -1) {
                                out.println("ERROR: You have already sent an offer for ride " + currentOfferRideId + ". Cannot send another offer.");
                                break;
                            }
                            if (tokens.length >= 3) {
                                int rideId;
                                double price;
                                try {
                                    rideId = Integer.parseInt(tokens[1]);
                                    price = Double.parseDouble(tokens[2]);
                                } catch (NumberFormatException nfe) {
                                    out.println("ERROR: Invalid rideId or price.");
                                    break;
                                }
                                Ride ride = Ride.getRideById(rideId);
                                if (ride != null && !ride.isAssigned()) {
                                    ride.addOffer(this, price);
                                    setCurrentOfferRideId(rideId);
                                    out.println("OFFER_SENT for ride " + rideId);
                                    ride.notifyCustomerOffers();
                                } else {
                                    out.println("ERROR: Ride not found or already assigned.");
                                }
                            } else {
                                out.println("ERROR: Invalid OFFER format. Provide rideId and price.");
                            }
                        } else {
                            out.println("ERROR: Only drivers can offer rides.");
                        }
                        break;

                    case "ASSIGN":
                        // Format: ASSIGN:rideId:driverUsername
                        if (user.getType().equalsIgnoreCase("customer")) {
                            if (tokens.length >= 3) {
                                int rideId;
                                try {
                                    rideId = Integer.parseInt(tokens[1]);
                                } catch (NumberFormatException nfe) {
                                    out.println("ERROR: Invalid rideId.");
                                    break;
                                }
                                // Check if this ride was actually requested by this customer.
                                Ride ride = Ride.getRideById(rideId);
                                if (ride == null || ride.getCustomerHandler() != this) {
                                    out.println("ERROR: You are not authorized to assign ride " + rideId + ".");
                                    break;
                                }
                                String driverUsername = tokens[2];
                                if (!ride.isAssigned()) {
                                    ClientHandler chosenDriver = ride.assignDriver(driverUsername);
                                    if (chosenDriver != null) {
                                        ride.setAssigned(true);
                                        out.println("RIDE_ASSIGNED:Driver " + driverUsername);
                                        chosenDriver.sendMessage("ASSIGNED:" + rideId + ":You have been assigned a ride.");
                                        chosenDriver.setBusy(true);
                                        // Clear pending offers for this ride.
                                        for (ClientHandler driverHandler : Server.drivers) {
                                            if (driverHandler.getCurrentOfferRideId() == rideId) {
                                                driverHandler.clearCurrentOffer();
                                            }
                                        }
                                    } else {
                                        out.println("ERROR: Driver not found in offers.");
                                    }
                                } else {
                                    out.println("ERROR: Ride already assigned.");
                                }
                            } else {
                                out.println("ERROR: Invalid ASSIGN format. Provide rideId and driver username.");
                            }
                        } else {
                            out.println("ERROR: Only customers can assign rides.");
                        }
                        break;

                    case "UPDATE":
                        // Format: UPDATE:rideId:status (status can be START or END)
                        if (user.getType().equalsIgnoreCase("driver")) {
                            if (tokens.length >= 3) {
                                int rideId;
                                try {
                                    rideId = Integer.parseInt(tokens[1]);
                                } catch (NumberFormatException nfe) {
                                    out.println("ERROR: Invalid rideId.");
                                    break;
                                }
                                String status = tokens[2];
                                Ride ride = Ride.getRideById(rideId);
                                if (ride != null) {
                                    // Only allow update from the driver assigned to this ride.
                                    if (ride.getAssignedDriver() == null ||
                                            !ride.getAssignedDriver().getUser().getUsername().equalsIgnoreCase(getUserName())) {
                                        out.println("ERROR: You are not assigned to ride " + rideId + ". Cannot update its status.");
                                        break;
                                    }
                                    ride.updateStatus(status);
                                    ride.getCustomerHandler().sendMessage("UPDATE:" + rideId + ":" + status);
                                    if (status.equalsIgnoreCase("END")) {
                                        setBusy(false);
                                    }
                                    out.println("STATUS_UPDATED:" + rideId + ":" + status);
                                } else {
                                    out.println("ERROR: Ride not found.");
                                }
                            } else {
                                out.println("ERROR: Invalid UPDATE format. Provide rideId and status.");
                            }
                        } else {
                            out.println("ERROR: Only drivers can update ride status.");
                        }
                        break;

                    case "RATE":
                        // Format: RATE:rideId:behaviourRating:carRating:rideRating:comment
                        if (user.getType().equalsIgnoreCase("customer")) {
                            if (tokens.length >= 6) {
                                int rideId, behaviourRating, carRating, rideRating;
                                try {
                                    rideId = Integer.parseInt(tokens[1]);
                                    behaviourRating = Integer.parseInt(tokens[2]);
                                    carRating = Integer.parseInt(tokens[3]);
                                    rideRating = Integer.parseInt(tokens[4]);
                                } catch (NumberFormatException nfe) {
                                    out.println("ERROR: Invalid rideId or rating values.");
                                    break;
                                }
                                String comment = tokens[5];
                                Ride ride = Ride.getRideById(rideId);
                                if (ride != null && ride.isAssigned()) {
                                    ClientHandler driverHandler = ride.getAssignedDriver();
                                    // Calculate overall rating as average.
                                    double overallRating = (behaviourRating + carRating + rideRating) / 3.0;
                                    driverHandler.getUser().addRating((int) overallRating);
                                    String ratingMessage = "RATED: Ride " + rideId +
                                            " rated with Behaviour: " + behaviourRating +
                                            ", Car: " + carRating +
                                            ", Ride: " + rideRating +
                                            ". Comment: " + comment +
                                            ". Overall new rating: " + driverHandler.getUser().getRating();
                                    out.println(ratingMessage);
                                    driverHandler.sendMessage(ratingMessage);
                                } else {
                                    out.println("ERROR: Ride not found or not assigned.");
                                }
                            } else {
                                out.println("ERROR: Invalid RATE format. Provide rideId, behaviourRating, carRating, rideRating, and comment.");
                            }
                        } else {
                            out.println("ERROR: Only customers can rate drivers.");
                        }
                        break;

                    case "STATS":
                        // Only admin can request statistics.
                        if (user.getType().equalsIgnoreCase("admin")) {
                            StringBuilder stats = new StringBuilder("STATS:");
                            stats.append("Total Users: ").append(Server.users.size()).append(" | ");
                            stats.append("Total Customers: ").append(Server.customers.size()).append(" | ");
                            stats.append("Total Drivers: ").append(Server.drivers.size()).append(" | ");
                            stats.append("Total Rides: ").append(Server.rides.size()).append(" | ");
                            long requested = Server.rides.stream().filter(r -> r.getStatus().equals("REQUESTED")).count();
                            long assigned = Server.rides.stream().filter(r -> r.getStatus().equals("ASSIGNED")).count();
                            long started = Server.rides.stream().filter(r -> r.getStatus().equals("START")).count();
                            long ended = Server.rides.stream().filter(r -> r.getStatus().equals("END")).count();
                            stats.append("Ride Statuses [REQUESTED:").append(requested)
                                    .append(", ASSIGNED:").append(assigned)
                                    .append(", START:").append(started)
                                    .append(", END:").append(ended).append("]");
                            out.println(stats.toString());
                        } else {
                            out.println("ERROR: Only admin can view statistics.");
                        }
                        break;

                    case "DISCONNECT":
                        // Prevent disconnect if in an ongoing ride.
                        if (isInOngoingRide()) {
                            out.println("ERROR: You are in an ongoing ride, cannot disconnect.");
                        } else {
                            out.println("DISCONNECTING");
                            running = false;
                        }
                        break;

                    default:
                        out.println("ERROR: Unknown command.");
                }
            }
        } catch (IOException e) {
            System.err.println("IOException in ClientHandler (" + getUserName() + "): " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch(IOException e) { }
            removeFromRoleList();
            System.out.println("Connection closed for user: " + getUserName());
        }
    }

    // Check if this client is in an ongoing ride (status START).
    private boolean isInOngoingRide() {
        return Server.rides.stream().anyMatch(r ->
                ((r.getCustomerHandler() == this || (r.getAssignedDriver() != null && r.getAssignedDriver() == this))
                        && r.getStatus().equals("START")));
    }

    private void addToRoleList() {
        if (user.getType().equalsIgnoreCase("driver")) {
            Server.drivers.add(this);
        } else if (user.getType().equalsIgnoreCase("customer")) {
            Server.customers.add(this);
        }
    }

    private void removeFromRoleList() {
        if (user != null) {
            if (user.getType().equalsIgnoreCase("driver")) {
                Server.drivers.remove(this);
            } else if (user.getType().equalsIgnoreCase("customer")) {
                Server.customers.remove(this);
            }
        }
    }

    private void broadcastRideRequest(Ride ride) {
        // Send ride request to all free drivers.
        boolean anyDriverAvailable = false;
        for (ClientHandler driverHandler : Server.drivers) {
            if (!driverHandler.isBusy()) {
                driverHandler.sendMessage("NEW_RIDE:" + ride.getRideId() + ":" + ride.getPickup() + ":" + ride.getDestination());
                anyDriverAvailable = true;
            }
        }
        if (!anyDriverAvailable) {
            ride.getCustomerHandler().sendMessage("INFO: No drivers are currently available.");
        }
    }

    public User getUser() {
        return user;
    }
}
