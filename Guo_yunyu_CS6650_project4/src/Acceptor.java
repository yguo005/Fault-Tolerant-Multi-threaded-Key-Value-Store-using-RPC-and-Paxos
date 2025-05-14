import java.util.Random;

public class Acceptor extends Thread {
  private Server server;
  private long highestPrepare = -1;
  private long acceptedProposal = -1;
  private String acceptedValue = null;
  private Random random = new Random();
  private volatile boolean running = true;

  public Acceptor(Server server) {
    this.server = server;
  }

  @Override
  public void run() {
    while (running) {
      // Simulate random failures
      if (random.nextDouble() < 0.01) { // 1% chance of failure
        running = false; // Simulate thread termination
        try {
          Thread.sleep(5000); // Sleep for a while before restarting
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        restart(); // Restart the acceptor thread
      }
      try {
        Thread.sleep(100); // Small delay to prevent busy waiting
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private void restart() {
    Acceptor newAcceptor = new Acceptor(server);
    newAcceptor.start(); // Start a new thread as a replacement
  }

  public synchronized String prepare(long proposalNumber) {
    if (proposalNumber > highestPrepare) {
      highestPrepare = proposalNumber;
      if (acceptedProposal != -1) {
        return "PROMISE," + acceptedProposal + "," + acceptedValue;
      } else {
        return "PROMISE";
      }
    }
    return "REJECT";
  }

  public synchronized String accept(long proposalNumber, String value) {
    if (proposalNumber >= highestPrepare) {
      highestPrepare = proposalNumber;
      acceptedProposal = proposalNumber;
      acceptedValue = value;
      return "ACCEPTED";
    }
    return "REJECT";
  }

  public void shutdown() {
    running = false;
  }
}
