public class User {
    private String username;
    private String password;
    private String type; // "customer", "driver", or "admin"
    private double rating = 0;
    private int ratingCount = 0;



    public User(String username, String password, String type) {
        this.username = username;
        this.password = password;
        this.type = type.toLowerCase();
    }

    public String getUsername() {
        return username;
    }

    public String getType() {
        return type;
    }

    public boolean checkPassword(String pwd) {
        return this.password.equals(pwd);
    }

    // For driver rating: update the accumulated rating.
    public void addRating(int newRating) {
        rating = ((rating * ratingCount) + newRating) / (++ratingCount);
    }

    public double getRating() {
        return rating;
    }

    // Check if a username already exists in the system.
    public static boolean userExists(String username) {
        return Server.users.stream().anyMatch(u -> u.getUsername().equalsIgnoreCase(username));
    }

    // Authenticate user credentials based on username, password, and type.
    public static User authenticate(String username, String password) {
        return Server.users.stream()
                .filter(u -> u.getUsername().equalsIgnoreCase(username) &&
                        u.checkPassword(password))
                .findFirst().orElse(null);
    }
}