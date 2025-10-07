import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * CrimeTrackingSystem.java
 *
 * Simple console-based crime tracking system for a police station.
 * - Officer login (pre-created admin account)
 * - Record crimes (offender name, crime type, description, police-in-charge, punishment)
 * - View all records
 * - Search records by offender
 * - Persistence to disk via serialization
 *
 * Usage:
 *   javac CrimeTrackingSystem.java
 *   java CrimeTrackingSystem
 */
public class CrimeTrackingSystem {

    // ---------- nested data classes ----------
    static class Officer implements Serializable {
        private static final long serialVersionUID = 1L;
        String username;
        String passwordHash; // SHA-256 hash

        Officer(String username, String passwordHash) {
            this.username = username;
            this.passwordHash = passwordHash;
        }

        @Override
        public String toString() {
            return "Officer{" + "username='" + username + '\'' + '}';
        }
    }

    static class CrimeRecord implements Serializable {
        private static final long serialVersionUID = 1L;
        UUID id;
        String offenderName;
        String crimeType;
        String description;
        String policeInCharge;
        String punishment;
        LocalDateTime reportedAt;

        CrimeRecord(String offenderName, String crimeType, String description, String policeInCharge, String punishment) {
            this.id = UUID.randomUUID();
            this.offenderName = offenderName;
            this.crimeType = crimeType;
            this.description = description;
            this.policeInCharge = policeInCharge;
            this.punishment = punishment;
            this.reportedAt = LocalDateTime.now();
        }

        @Override
        public String toString() {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            return "ID: " + id
                    + "\nOffender: " + offenderName
                    + "\nCrime Type: " + crimeType
                    + "\nDescription: " + description
                    + "\nPolice in charge: " + policeInCharge
                    + "\nPunishment: " + punishment
                    + "\nReported At: " + reportedAt.format(fmt)
                    + "\n---------------------------";
        }
    }

    // ---------- simple database (file-based) ----------
    static class Database implements Serializable {
        private static final long serialVersionUID = 1L;
        List<Officer> officers = new ArrayList<>();
        List<CrimeRecord> crimes = new ArrayList<>();
    }

    // file to persist data
    private static final String DB_FILE = "crimedb.ser";

    private Database db;
    private Officer currentOfficer;
    private final Scanner scanner = new Scanner(System.in);

    // ---------- constructor ----------
    public CrimeTrackingSystem() {
        loadDatabase();
        ensureDefaultAdmin();
    }

    // ---------- main loop ----------
    public void run() {
        println("=== Crime Tracking Information System ===");

        while (true) {
            if (currentOfficer == null) {
                showLoginMenu();
            } else {
                showOfficerMenu();
            }
        }
    }

    // ---------- UI menus ----------
    private void showLoginMenu() {
        println("\n1) Login\n2) Register new officer\n3) Exit");
        System.out.print("Choose: ");
        String choice = scanner.nextLine().trim();
        switch (choice) {
            case "1" -> login();
            case "2" -> registerOfficer();
            case "3" -> {
                println("Exiting... Goodbye.");
                saveDatabase();
                System.exit(0);
            }
            default -> println("Invalid choice.");
        }
    }

    private void showOfficerMenu() {
        println("\nLogged in as: " + currentOfficer.username);
        println("1) Record a new crime");
        println("2) View all crime records");
        println("3) Search crimes by offender name");
        println("4) View punishments list");
        println("5) Logout");
        println("6) Exit");
        System.out.print("Choose: ");
        String choice = scanner.nextLine().trim();
        switch (choice) {
            case "1" -> addCrime();
            case "2" -> viewAllCrimes();
            case "3" -> searchByOffender();
            case "4" -> viewPunishments();
            case "5" -> {
                currentOfficer = null;
                println("Logged out.");
            }
            case "6" -> {
                println("Exiting... Saving database.");
                saveDatabase();
                System.exit(0);
            }
            default -> println("Invalid choice.");
        }
    }

    // ---------- functionality ----------
    private void login() {
        System.out.print("Username: ");
        String username = scanner.nextLine().trim();
        System.out.print("Password: ");
        String password = readPassword();

        Optional<Officer> opt = db.officers.stream()
                .filter(o -> o.username.equalsIgnoreCase(username))
                .findFirst();

        if (opt.isEmpty()) {
            println("No such officer.");
            return;
        }

        Officer officer = opt.get();
        String providedHash = sha256(password);
        if (providedHash.equals(officer.passwordHash)) {
            currentOfficer = officer;
            println("Login successful. Welcome, " + officer.username + "!");
        } else {
            println("Wrong password.");
        }
    }

    private void registerOfficer() {
        System.out.print("Choose a username: ");
        String username = scanner.nextLine().trim();
        if (username.isEmpty()) {
            println("Username cannot be empty.");
            return;
        }
        boolean exists = db.officers.stream().anyMatch(o -> o.username.equalsIgnoreCase(username));
        if (exists) {
            println("Username already exists.");
            return;
        }
        System.out.print("Choose a password: ");
        String password = readPassword();
        if (password.length() < 4) {
            println("Password too short (min 4 chars).");
            return;
        }
        db.officers.add(new Officer(username, sha256(password)));
        saveDatabase();
        println("Officer registered. You can now login.");
    }

    private void addCrime() {
        println("\n--- Record New Crime ---");
        System.out.print("Offender name: ");
        String offender = scanner.nextLine().trim();
        if (offender.isEmpty()) offender = "Unknown";

        System.out.print("Crime type (e.g., Theft, Assault): ");
        String crimeType = scanner.nextLine().trim();
        if (crimeType.isEmpty()) crimeType = "Unspecified";

        System.out.print("Description: ");
        String description = scanner.nextLine().trim();

        System.out.print("Police in charge (press Enter to use your username): ");
        String policeInCharge = scanner.nextLine().trim();
        if (policeInCharge.isEmpty()) policeInCharge = currentOfficer.username;

        System.out.print("Punishment/Disposition (e.g., Arrest, Fine, Pending): ");
        String punishment = scanner.nextLine().trim();
        if (punishment.isEmpty()) punishment = "Pending";

        CrimeRecord rec = new CrimeRecord(offender, crimeType, description, policeInCharge, punishment);
        db.crimes.add(rec);
        saveDatabase();
        println("Crime recorded with ID: " + rec.id);
    }

    private void viewAllCrimes() {
        println("\n--- All Crime Records ---");
        if (db.crimes.isEmpty()) {
            println("No crime records found.");
            return;
        }
        db.crimes.stream()
                .sorted(Comparator.comparing(r -> r.reportedAt))
                .forEach(r -> println(r.toString()));
    }

    private void searchByOffender() {
        System.out.print("Enter offender name to search (partial allowed): ");
        String q = scanner.nextLine().trim().toLowerCase();
        List<CrimeRecord> found = new ArrayList<>();
        for (CrimeRecord r : db.crimes) {
            if (r.offenderName.toLowerCase().contains(q)) found.add(r);
        }
        if (found.isEmpty()) {
            println("No records found for: " + q);
        } else {
            println(found.size() + " record(s) found:");
            found.forEach(r -> println(r.toString()));
        }
    }

    private void viewPunishments() {
        println("\n--- Punishments / Dispositions ---");
        if (db.crimes.isEmpty()) {
            println("No crime records.");
            return;
        }
        for (CrimeRecord r : db.crimes) {
            println("ID: " + r.id + " | Offender: " + r.offenderName + " | Punishment: " + r.punishment);
        }
    }

    // ---------- persistence ----------
    private void loadDatabase() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(DB_FILE))) {
            db = (Database) ois.readObject();
            println("Database loaded (" + db.officers.size() + " officer(s), " + db.crimes.size() + " record(s)).");
        } catch (FileNotFoundException e) {
            db = new Database();
            println("No existing database found. A new database will be created.");
        } catch (Exception e) {
            println("Failed to load database. Starting fresh. Error: " + e.getMessage());
            db = new Database();
        }
    }

    private void saveDatabase() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(DB_FILE))) {
            oos.writeObject(db);
            //println("Database saved.");
        } catch (Exception e) {
            println("Failed to save database: " + e.getMessage());
        }
    }

    // ---------- helpers ----------
    private void ensureDefaultAdmin() {
        boolean hasAdmin = db.officers.stream().anyMatch(o -> o.username.equalsIgnoreCase("admin"));
        if (!hasAdmin) {
            db.officers.add(new Officer("admin", sha256("admin"))); // default: admin/admin
            saveDatabase();
            println("Default admin account created (username: admin, password: admin). Please change ASAP.");
        }
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] b = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte by : b) sb.append(String.format("%02x", by));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // basic password read that hides input when possible
    private String readPassword() {
        // try to use system console
        Console console = System.console();
        if (console != null) {
            char[] pwd = console.readPassword();
            return new String(pwd);
        } else {
            // fallback
            return scanner.nextLine();
        }
    }

    private void println(String s) {
        System.out.println(s);
    }

    // ---------- entry ----------
    public static void main(String[] args) {
        CrimeTrackingSystem app = new CrimeTrackingSystem();
        app.run();
    }
}