# CS 6650 Project #4: Fault-Tolerant Multi-threaded Key-Value Store using RPC and Paxos

**Assigned:** 3/24/20
**Due:** 4/15/20

## Project Overview

This project extends Project #3 by incorporating fault tolerance into the replicated Key-Value (KV) store. Instead of relying on the non-fault-tolerant Two-Phase Commit (2PC) protocol, this project requires the implementation of the Paxos consensus algorithm, as described in Lamport's "Paxos Made Simple" paper. The goal is to ensure continual operation and consistent updates across the replicated KV-store servers despite potential replica failures.

The key enhancements are:
1.  **Paxos for Consensus:** Implement the Paxos algorithm to achieve fault-tolerant consensus for PUT and DELETE operations among the 5 replicated KV-store servers. This involves implementing the Proposer, Acceptor, and Learner roles. The focus is on the Paxos implementation and its algorithmic steps for event ordering.
2.  **Simulated Acceptor Failures:** The Acceptor roles (implemented as threads or processes) must be configured to "fail" randomly and periodically. This could involve a timeout mechanism that stops an Acceptor thread. A new Acceptor thread can then be restarted after a delay, though it won't have the same state as the failed one. This demonstrates how Paxos handles server failures. (Extra credit may be available if all Paxos roles are designed to fail and restart randomly).

Client threads can still generate requests (PUT, GET, DELETE) to any of the replicas at any time. Leader election among Proposers may be used to minimize livelock but is not a strict requirement. The client's role in pre-populating the store and performing a set number of operations (5 PUTs, 5 GETs, 5 DELETEs) remains.

## Features to Implement

*   **RPC-based Communication:** (Leveraged from previous projects)
    *   Client-server interaction and inter-server communication using an RPC framework (e.g., Java RMI, Apache Thrift).
*   **Multi-threaded Servers:** (Leveraged from previous projects)
    *   Each of the 5 server replicas must be multi-threaded to handle concurrent client requests and internal Paxos messages.
*   **Server Replication:** (Leveraged from Project #3)
    *   Maintain 5 instances of the Key-Value Store server.
*   **Paxos Consensus Algorithm for PUT/DELETE Operations:**
    *   **Proposer Role:**
        *   Initiates a proposal for a KV operation (PUT or DELETE).
        *   Phase 1a (Prepare): Sends `prepare(n)` requests to a quorum of Acceptors with a unique, increasing proposal number `n`.
        *   Phase 1b (Promise): If it receives promises from a quorum of Acceptors (potentially with previously accepted values `(n_a, v_a)`), it proceeds. If a promise includes a previously accepted value, the Proposer must choose that value for its proposal.
        *   Phase 2a (Accept): Sends `accept(n, value)` requests to the quorum of Acceptors with its chosen proposal number `n` and the value (the KV operation details).
    *   **Acceptor Role:**
        *   Responds to `prepare(n)` requests: If `n` is higher than any proposal number it has responded to, it promises not to accept any proposals numbered less than `n` and returns any previously accepted proposal `(n_a, v_a)`.
        *   Responds to `accept(n, value)` requests: If it has not already promised a higher proposal number, it accepts the proposal `(n, value)`, records it persistently (conceptually), and notifies Learners.
        *   **Failure Simulation:** Acceptor threads must be designed to fail periodically (e.g., timeout and stop) and then potentially restart after a delay.
    *   **Learner Role:**
        *   Learns that a value (KV operation) has been chosen (agreed upon) when it has been accepted by a majority of Acceptors.
        *   Once a value is learned, it is considered committed, and the KV operation can be applied to the local state machine (the Key-Value store).
    *   Client requests for PUT/DELETE will trigger a Paxos instance.
*   **Data Consistency for GET Operations:**
    *   A GET request to any replica should return the latest *committed* value for the key. This implies that the replica serving the GET request must have learned about the committed state of the key.
*   **Client Functionality:**
    *   Ability to connect to any of the 5 server replicas.
    *   Pre-populate the replicated Key-Value store.
    *   Perform at least 5 PUTs, 5 GETs, and 5 DELETEs after pre-population.

## Suggested Technologies (for Java)

*   **RPC Framework:** Java RMI or Apache Thrift.
*   **Multi-threading:** Java's `java.util.concurrent` package and synchronization primitives.
*   **Paxos Implementation:** Each server will need to embody Proposer, Acceptor, and Learner logic, or these roles could be separate threads/objects within each server.
*   **Inter-Server Communication (for Paxos):** The chosen RPC mechanism will be used for all Paxos messages (`prepare`, `promise`, `accept`, `accepted`, etc.) between server replicas.

## Project Structure (Conceptual)

*   **`PaxosInterfaces.java` (or similar for Thrift IDL):**
    *   Defines remote methods for Paxos messages: `prepare(proposalID)`, `promise(proposalID, acceptedID, acceptedValue)`, `acceptRequest(proposalID, value)`, `accepted(proposalID, value)`.
*   **`KeyValueServiceInterface.java`**: Remote interface for client-server KV operations (PUT, GET, DELETE).
*   **`KeyValueServer.java`**:
    *   Implements `KeyValueServiceInterface` for client requests.
    *   Implements Paxos roles (Proposer, Acceptor, Learner) and their communication logic using `PaxosInterfaces`.
    *   Manages its local Key-Value store (state machine).
    *   Handles multi-threading for client requests and Paxos messages.
    *   Contains logic to initiate Paxos for PUT/DELETE and serve GETs based on learned/committed values.
    *   Manages state related to Paxos (e.g., highest proposal ID promised/accepted for Acceptors).
    *   Simulates Acceptor failures and restarts.
*   **`KeyValueClient.java`**:
    *   Connects to one of the 5 server replicas.
    *   Invokes methods on `KeyValueServiceInterface`.

## Compilation and Running

Similar to Project #3, instructions will depend heavily on the chosen RPC framework.

1.  **Define Service Interfaces:** For client-server KV operations and for inter-server Paxos messages.
2.  **Generate Stubs/Skeletons:** If using a framework like Thrift.
3.  **Implement Server Logic:**
    *   Implement client-facing KV service methods, which will internally trigger Paxos for writes.
    *   Implement the Proposer, Acceptor, and Learner roles and the logic for exchanging Paxos messages via RPC.
    *   Implement the Acceptor failure and restart mechanism.
4.  **Implement Client Logic:**
    *   No major changes from Project #3, other than expecting the system to be fault-tolerant.
5.  **Compile:** Compile all source code, including any generated RPC files and necessary libraries.
6.  **Run the Servers:**
    *   Start 5 instances of your `KeyValueServer` application. Each will need its own configuration (e.g., port, ID) and knowledge of its peers for Paxos communication.
7.  **Run the Client(s):**
    *   The client should be able to connect to any of the 5 server ports.

## Key Considerations

*   **Paxos Algorithm Details:** Strict adherence to the steps outlined in "Paxos Made Simple" is crucial, including proposal numbering, quorums, and handling of previously accepted values.
*   **State Management:** Acceptors need to persist (at least in memory for this project, unless disk persistence is explicitly added) the highest proposal number promised (`maxPrepare`) and the accepted proposal (`acceptedProposalID`, `acceptedValue`).
*   **Learner Consensus:** How do Learners determine a value is chosen? Typically, when a Proposer receives acceptances from a majority or when a Learner hears about an accepted value from a majority of Acceptors.
*   **Applying to State Machine:** Once a value (a KV operation) is learned/chosen via Paxos, it must be applied to the actual Key-Value store on each replica.
*   **Acceptor Failures:** The simulation of failures should demonstrate that the system can continue to make progress and reach consensus as long as a majority of Acceptors are alive and can communicate. When a failed Acceptor restarts, it will rejoin the Paxos protocol, potentially missing past rounds but able to participate in new ones.
*   **GET Operations:** How does a server handle a GET request? It should return a value that has been committed through Paxos. This might involve a read-optimized path or ensuring the server has learned the latest committed value.
