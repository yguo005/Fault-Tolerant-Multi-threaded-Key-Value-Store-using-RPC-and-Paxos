import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Learner extends Thread {
  private Server server;
  private Map<Long, Map<String, Integer>> learnedValues = new ConcurrentHashMap<>();
  private int quorum;

  public Learner(Server server, int quorum){
    this.server = server;
    this.quorum = quorum;
  }

  @Override
  public void run(){
    // The learner doesn't need to continuously run
    // It will be triggered by accept messages
  }

  public synchronized void learn(long proposalNumber, String value){
    learnedValues.computeIfAbsent(proposalNumber,k -> new ConcurrentHashMap<>())
        .merge(value, 1, Integer::sum);
  }

  public boolean waitForConsensus(ProposalId proposalId) throws InterruptedException {
    long startTime = System.currentTimeMillis();
    while (System.currentTimeMillis() - startTime < 10000) {// Wait for up to 10 seconds
      Map<String, Integer> proposalLearned = learnedValues.get(proposalId.number);
      if (proposalLearned != null) {
        for (Map.Entry<String, Integer> entry : proposalLearned.entrySet()) {
          if (entry.getValue() >= quorum) {
            return true;
          }
        }
      }
      Thread.sleep(100);
    }
    return false;
  }


}
