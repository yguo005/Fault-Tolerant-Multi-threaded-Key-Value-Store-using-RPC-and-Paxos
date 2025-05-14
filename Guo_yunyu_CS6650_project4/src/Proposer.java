import java.rmi.RemoteException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Proposer extends Thread {
  private static final Logger logger = Logger.getLogger(Proposer.class.getName());
  private Server server;
  private long nextProposalNumber;
  private List<KeyValueRPC> otherServers;
  private static final int MAX_RETRIES = 3;

  public Proposer(Server server, int serverId, List<KeyValueRPC> otherServers) {
    this.server = server;
    this.nextProposalNumber = serverId;
    this.otherServers = otherServers;
  }

  @Override
  public void run() {
    // The proposer doesn't need to continuously run
    // It will be triggered by client requests
  }

  public ProposalId propose(String value) throws RemoteException {
    for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
      try {
        return doProposeAttempt(value);
      } catch (RemoteException e) {
        logger.warning("Proposal attempt " + (attempt + 1) + " failed: " + e.getMessage());
        if (attempt == MAX_RETRIES - 1) {
          throw e;
        }
        try {
          Thread.sleep(1000 * (attempt + 1)); // Exponential backoff
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
        }
      }
    }
    throw new RemoteException("Failed to propose after " + MAX_RETRIES + " attempts");
  }

  private ProposalId doProposeAttempt(String value) throws RemoteException {
    long proposalNumber = getNextProposalNumber();
    int quorum = (otherServers.size() + 1) / 2 + 1; // Include this server in the count
    logger.info("Starting proposal with number: " + proposalNumber + ", quorum needed: " + quorum);

    // 1: prepare
    int promises = 0;
    String highestAcceptedValue = null;
    long highestAcceptedProposal = -1;

    logger.info("Sending prepare requests to " + otherServers.size() + " servers.");
    for (KeyValueRPC otherServer : otherServers) {
      try {
        String response = otherServer.prepare(proposalNumber);
        logger.info("Received prepare response from server: " + response);
        if (response.startsWith("PROMISE")) {
          promises++;
          String[] parts = response.split(",");
          if (parts.length == 3) {
            long acceptedProposal = Long.parseLong(parts[1]);
            if (acceptedProposal > highestAcceptedProposal) {
              highestAcceptedProposal = acceptedProposal;
              highestAcceptedValue = parts[2];
            }
          }
        }
      } catch (RemoteException e) {
        logger.warning("Failed to send prepare request to server: " + e.getMessage());
      }
    }

    // Include this server's promise
    promises++;

    logger.info("Prepare phase complete. Promises received: " + promises + " out of " + (otherServers.size() + 1));

    if (promises < quorum) {
      throw new RemoteException("Failed to get quorum for prepare phase. Received " + promises + " promises, needed " + quorum);
    }

    // 2: accept
    String valueToPropose = highestAcceptedValue != null ? highestAcceptedValue : value;
    int accepts = 0;

    for (KeyValueRPC otherServer : otherServers) {
      try {
        String response = otherServer.accept(proposalNumber, valueToPropose);
        logger.info("Received accept response from server: " + response);
        if (response.equals("ACCEPTED")) {
          accepts++;
        }
      } catch (RemoteException e) {
        logger.warning("Failed to send accept request to server: " + e.getMessage());
      }
    }

    // Include this server's accept
    accepts++;

    logger.info("Accept phase complete. Accepts received: " + accepts + " out of " + (otherServers.size() + 1));

    if (accepts < quorum) {
      throw new RemoteException("Failed to get quorum for accept phase. Received " + accepts + " accepts, needed " + quorum);
    }

    // Proposal accepted, notify learners
    for (KeyValueRPC otherServer : otherServers) {
      try {
        otherServer.learn(proposalNumber, valueToPropose);
      } catch (RemoteException e) {
        logger.warning("Failed to notify learner: " + e.getMessage());
      }
    }

    // Notify this server's learner
    try {
      server.learn(proposalNumber, valueToPropose);
    } catch (RemoteException e) {
      logger.warning("Failed to notify local learner: " + e.getMessage());
    }

    return new ProposalId(proposalNumber, valueToPropose);
  }

  private synchronized long getNextProposalNumber() {
    return nextProposalNumber += otherServers.size() + 1;
  }
}

class ProposalId {
  long number;
  String value;

  public ProposalId(long number, String value) {
    this.number = number;
    this.value = value;
  }
}