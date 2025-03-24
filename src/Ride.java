import java.util.HashMap;
import java.util.Map;

public class Ride {
    private static int idCounter = 1;

    private int rideId;
    private String pickup;
    private String destination;
    private ClientHandler customerHandler;
    private boolean assigned = false;
    private ClientHandler assignedDriver;
    private String status = "REQUESTED"; // Possible statuses: REQUESTED, ASSIGNED, START, END
    private boolean rated = false; // Flag to track if this ride has been rated.
    // Offers from drivers: key = driver username, value = fare offer
    private Map<String, Double> offers = new HashMap<>();

    public Ride(String pickup, String destination, ClientHandler customerHandler) {
        this.rideId = idCounter++;
        this.pickup = pickup;
        this.destination = destination;
        this.customerHandler = customerHandler;
    }

    public int getRideId() {
        return rideId;
    }

    public String getPickup() {
        return pickup;
    }

    public String getDestination() {
        return destination;
    }

    public ClientHandler getCustomerHandler() {
        return customerHandler;
    }

    public boolean isAssigned() {
        return assigned;
    }

    public void setAssigned(boolean assigned) {
        this.assigned = assigned;
    }

    public ClientHandler getAssignedDriver() {
        return assignedDriver;
    }

    public String getStatus() {
        return status;
    }

    public void updateStatus(String newStatus) {
        this.status = newStatus.toUpperCase();
    }

    public boolean isRated() {
        return rated;
    }

    public void setRated(boolean rated) {
        this.rated = rated;
    }

    // Add an offer from a driver.
    public void addOffer(ClientHandler driverHandler, double price) {
        offers.put(driverHandler.getUser().getUsername(), price);
    }

    // Notify the customer about the offers received.
    public void notifyCustomerOffers() {
        StringBuilder sb = new StringBuilder("OFFERS:" + rideId);
        for (Map.Entry<String, Double> entry : offers.entrySet()) {
            sb.append(":" + entry.getKey() + "=" + entry.getValue());
        }
        customerHandler.sendMessage(sb.toString());
    }

    // Assign a driver based on the provided username.
    public ClientHandler assignDriver(String driverUsername) {
        if (offers.containsKey(driverUsername)) {
            // Find the driver in the list of available drivers.
            for (ClientHandler driverHandler : Server.drivers) { // assuming Server.drives is defined similarly to Server.drivers
                if (driverHandler.getUser().getUsername().equalsIgnoreCase(driverUsername)) {
                    this.assignedDriver = driverHandler;
                    this.status = "ASSIGNED";
                    return driverHandler;
                }
            }
        }
        return null;
    }

    // Helper method to retrieve a ride by its ID.
    public static Ride getRideById(int id) {
        for (Ride ride : Server.rides) {
            if (ride.getRideId() == id) {
                return ride;
            }
        }
        return null;
    }
}
