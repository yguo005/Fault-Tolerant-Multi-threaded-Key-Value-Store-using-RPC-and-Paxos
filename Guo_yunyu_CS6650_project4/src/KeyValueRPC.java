import java.rmi.Remote;
import java.rmi.RemoteException;

public interface KeyValueRPC extends Remote {
  String put(String key, String value) throws RemoteException;
  String get(String key) throws RemoteException;
  String delete(String key) throws RemoteException;
  String getAll() throws RemoteException;

  //Paxos methods
  String prepare(long proposalNumber) throws RemoteException;
  String accept(long proposalNumber, String value) throws RemoteException;
  void learn(long proposalNumber, String value) throws RemoteException;
  String ping() throws RemoteException;

}
