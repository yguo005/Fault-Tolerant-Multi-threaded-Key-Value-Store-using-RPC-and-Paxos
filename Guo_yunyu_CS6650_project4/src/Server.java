import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Server extends UnicastRemoteObject implements KeyValueRPC {
  private static final Logger logger = Logger.getLogger(Server.class.getName());
  private Map<String, String> store = new ConcurrentHashMap<>();
  private List<KeyValueRPC> otherServers = new ArrayList<>();
  private String[] otherServerAddresses;
  private int[] otherServerPorts;
  private int serverId;
  private int port;

  private Acceptor acceptor;
  private Proposer proposer;
  private Learner learner;

  private static final int PREPARE_TIMEOUT_MS = 5000; // 5 seconds

  public Server(int port, String[] otherServerAddresses, int[] otherServerPorts, int serverId) throws RemoteException {
    super(port);
    this.port = port;
    this.serverId = serverId;
    this.otherServerAddresses = otherServerAddresses;
    this.otherServerPorts = otherServerPorts;

    this.acceptor = new Acceptor(this);
    this.proposer = new Proposer(this, serverId, otherServers);
    this.learner = new Learner(this, otherServers.size()/2+1);
  }

  public void start() {
    connectToOtherServers();
    acceptor.start();
    proposer.start();
    learner.start();
    checkConnectivity();
    logger.info("Server fully initialized and ready for operations.");
  }

  private void connectToOtherServers() {
    for (int i = 0; i < otherServerAddresses.length; i++) {
      try {
        Registry registry = LocateRegistry.getRegistry(otherServerAddresses[i], otherServerPorts[i]);
        KeyValueRPC otherServer = (KeyValueRPC) registry.lookup("KeyValueRPC");
        otherServers.add(otherServer);
        logger.info("Connected to server at " + otherServerAddresses[i] + ":" + otherServerPorts[i]);
      } catch (Exception e) {
        logger.log(Level.WARNING, "Failed to connect to server at " + otherServerAddresses[i] + ":" + otherServerPorts[i], e);
      }
    }
  }

  public void checkConnectivity() {
    logger.info("Checking connectivity with other servers...");
    for (KeyValueRPC otherServer : otherServers) {
      try {
        String response = otherServer.ping();
        logger.info("Successfully pinged server: " + response);
      } catch (RemoteException e) {
        logger.warning("Failed to ping server: " + e.getMessage());
      }
    }
  }

  public String ping() throws RemoteException {
    return "pong from " + serverId;
  }

  public boolean isReady() {
    return acceptor != null && proposer != null && learner != null;
  }

  public void healthCheck() {
    logger.info("Performing health check...");
    checkConnectivity();
    // Add any other health check logic here
  }

  @Override
  public String put(String key, String value) throws RemoteException {
    String command = "PUT " + key + " " + value;
    return runPaxos(command);
  }

  @Override
  public String get(String key) throws RemoteException {
    return store.getOrDefault(key, "ERROR: key not found");
  }

  @Override
  public String delete(String key) throws RemoteException {
    String command = "DELETE " + key;
    return runPaxos(command);
  }

  @Override
  public String getAll() throws RemoteException {
    StringBuilder result = new StringBuilder();
    for (Map.Entry<String, String> entry : store.entrySet()) {
      result.append(entry.getKey()).append(" ").append(entry.getValue()).append("\n");
    }
    return result.toString();
  }

  private String runPaxos(String command) {
    try {
      ProposalId proposalId = proposer.propose(command);
      if (learner.waitForConsensus(proposalId)) {
        executeCommand(command);
        return "SUCCESS";
      }
      return "FAILURE: Consensus not reached";
    } catch (Exception e) {
      logger.log(Level.SEVERE, "Error in Paxos execution", e);
      return "ERROR: " + e.getMessage();
    }
  }

  private void executeCommand(String command) {
    String[] parts = command.split(" ");
    if (parts[0].equals("PUT")) {
      store.put(parts[1], parts[2]);
    } else if (parts[0].equals("DELETE")) {
      store.remove(parts[1]);
    }
  }

  @Override
  public String prepare(long proposalNumber) throws RemoteException {
    CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
      return acceptor.prepare(proposalNumber);
    });

    try {
      return future.get(PREPARE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      logger.warning("Prepare request timed out for proposal: " + proposalNumber);
      return "TIMEOUT";
    } catch (Exception e) {
      logger.severe("Error in prepare request: " + e.getMessage());
      throw new RemoteException("Error in prepare request", e);
    }
  }

  @Override
  public String accept(long proposalNumber, String value) throws RemoteException {
    return acceptor.accept(proposalNumber, value);
  }

  @Override
  public void learn(long proposalNumber, String value) throws RemoteException {
    learner.learn(proposalNumber, value);
  }

  public static void main(String args[]) {
    if (args.length != 6) {
      System.out.println("Usage: java Server <port> <otherPort1> <otherPort2> <otherPort3> <otherPort4> <serverId>");
      System.exit(1);
    }

    int port = Integer.parseInt(args[0]);
    String[] otherServerAddresses = new String[4];
    int[] otherServerPorts = new int[4];
    for (int i = 0; i < 4; i++) {
      otherServerAddresses[i] = "localhost";
      otherServerPorts[i] = Integer.parseInt(args[i+1]);
    }
    int serverId = Integer.parseInt(args[5]);

    try {
      Thread.sleep(1000);
      Server server = new Server(port, otherServerAddresses, otherServerPorts, serverId);
      Registry registry = LocateRegistry.createRegistry(port);
      registry.bind("KeyValueRPC", server);
      logger.info("Server bound to registry on port " + port);

      server.start();
      logger.info("Server fully started and connected on port " + port);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      logger.log(Level.SEVERE, "Interrupted while waiting to start server", e);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}