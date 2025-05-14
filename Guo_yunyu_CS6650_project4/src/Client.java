import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

public class Client {

  private KeyValueRPC[] servers;
  private Random random = new Random();
  private BufferedReader input;
  private static final int MAX_RETRIES = 3;
  private static final int RETRY_DELAY = 1000; // 1 sec

  public Client(String[] addresses, int[] ports) {
    if (addresses.length != 5 || ports.length != 5) {
      throw new IllegalArgumentException("Exactly 5 server addresses and ports must be provided");
    }

    servers = new KeyValueRPC[5];
    for (int i = 0; i < 5; i++) {
      try {
        Registry registry = LocateRegistry.getRegistry(addresses[i], ports[i]);
        servers[i] = (KeyValueRPC) registry.lookup("KeyValueRPC");
        log("Connected to server at " + addresses[i] + ":" + ports[i]);
      } catch (Exception e) {
        log("Client exception when connecting to server: " + addresses[i] + ":" + ports[i]);
        e.printStackTrace();
        System.exit(1);
      }
    }

    input = new BufferedReader(new InputStreamReader(System.in));
    // Add a delay to allow servers to fully set up
    System.out.println("Waiting for servers to set up...");
    try {
      Thread.sleep(10000); // Wait for 10 seconds
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    // Pre-populate key-value store with retries
    System.out.println("Pre-populating key-value store...");
    for (int i = 1; i <= 5; i++) {
      boolean success = false;
      int retries = 0;
      while (!success && retries < 3) {
        try {
          sendCommandWithRetry("PUT key" + i + " value" + i);
          success = true;
        } catch (Exception e) {
          retries++;
          log("Failed to pre-populate key" + i + ". Retrying... (Attempt " + retries + ")");
          try {
            Thread.sleep(1000 * retries);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
          }
        }
      }
      if (!success) {
        log("Failed to pre-populate key" + i + " after 3 attempts.");
      }
    }

    // Verify pre-population
    System.out.println("Verifying pre-populated data...");
    for (int i = 1; i <= 5; i++) {
      sendCommandWithRetry("GET key" + i);
    }

    // Start reading interactive command loop
    String line = "";
    while (!line.equalsIgnoreCase("QUIT")) {
      try {
        System.out.println(
            "Enter a command: PUT key value, GET key, DELETE key, GETALL or 'QUIT' to quit: ");
        line = input.readLine();
        if (!line.equalsIgnoreCase("QUIT")) {
          sendCommandWithRetry(line);
        }
      } catch (Exception e) {
        log("Error reading command: " + e.getMessage());
      }
    }

    System.out.println("Client shutting down...");
  }

  private void sendCommandWithRetry(String command) {
    int retries = 0;
    while (retries < MAX_RETRIES) {
      try {
        sendCommand(command); // Success, exit the retry loop
        return;
      } catch (Exception e) {
        log("Error executing command (attempt " + (retries + 1) + "): " + e.getMessage());
        retries++;
        if (retries < MAX_RETRIES) {
          try {
            Thread.sleep(RETRY_DELAY);
          } catch (InterruptedException e1) {
            Thread.currentThread().interrupt();
          }
        }
      }
    }
    log("Failed to execute command after " + MAX_RETRIES + " attempts: " + command);
  }

  private void sendCommand(String command) throws RemoteException {
    String[] parts = command.split(" ");
    if (parts.length == 0) {
      log("Invalid command format");
      return;
    }

    KeyValueRPC server = servers[random.nextInt(5)];
    String response = "";

    switch (parts[0].toUpperCase()) {
      case "PUT":
        if (parts.length != 3) {
          log("Invalid PUT command. Usage: PUT key value");
          return;
        }
        response = server.put(parts[1], parts[2]);
        break;
      case "GET":
        if (parts.length != 2) {
          log("Invalid GET command. Usage: GET key");
          return;
        }
        response = server.get(parts[1]);
        break;
      case "DELETE":
        if (parts.length != 2) {
          log("Invalid DELETE command. Usage: DELETE key");
          return;
        }
        response = server.delete(parts[1]);
        break;
      case "GETALL":
        response = server.getAll();
        break;
      default:
        log("Unknown command: " + parts[0]);
        return;
    }
    log("Received from server: " + response);

    // Add a small delay after each operation to allow for Paxos consensus
    try {
      Thread.sleep(500);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static void log(String message) {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    String timestamp = LocalDateTime.now().format(formatter);
    System.out.println(timestamp + ": " + message);
  }

  public static void main(String args[]) {
    if (args.length != 10) {
      log("Usage: java Client <host1> <port1> <host2> <port2> <host3> <port3> <host4> <port4> <host5> <port5>");
      System.exit(1);
    }

    String[] hosts = new String[5];
    int[] ports = new int[5];
    for (int i = 0; i < 5; i++) {
      hosts[i] = args[i * 2];
      try {
        ports[i] = Integer.parseInt(args[i * 2 + 1]);
      } catch (NumberFormatException e) {
        log("ERROR: port number must be integer");
        System.exit(1);
      }
    }
    new Client(hosts, ports);
  }
}