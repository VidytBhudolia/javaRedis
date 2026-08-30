# javaRedis

A high-performance, thread-safe, in-memory key-value store built entirely in Java. This project is a custom implementation of the Redis protocol (RESP), designed to support concurrent client connections, complex data structures, and distributed Master-Replica data synchronization.

## Architecture & Intent

The intent of javaRedis is to recreate the core mechanics of a distributed in-memory database from the ground up, utilizing modern Java concurrency primitives (like Virtual Threads) without relying on external web frameworks. 

The system is divided into four primary layers:
1. **Network / Infrastructure Layer:** Handles raw TCP socket connections, managing incoming streams, and maintaining connection pools for active clients and replicas.
2. **Protocol Layer (RESP):** Serializes and deserializes the strict Redis Serialization Protocol, converting raw byte streams into executable Java command arrays.
3. **Service Layer:** Routes parsed commands through a dynamic registry to specific execution logic, managing client sessions and blocking states.
4. **Storage Layer:** A thread-safe, concurrent repository utilizing optimistic locking and `ConcurrentHashMap` to manage the keyspace, data types, and expiration TTLs.

## Current Capabilities

### Data Structures & Commands
* **Strings:** `GET`, `SET`, `DEL`, `INCR`, `ECHO`, `TYPE`
* **Lists / Queues:** `LPUSH`, `RPUSH`, `LPOP`, `RPOP`, `LRANGE`, `LLEN`, `LREM`
* **Streams:** `XADD`, `XRANGE`, `XREAD` (supports blocking reads)
* **Transactions:** `WATCH`, `UNWATCH` (Optimistic locking via key versioning)
* **Server / Meta:** `PING`, `INFO`, `WAIT`

### Distributed Replication (Master-Replica)
The engine supports a complete, asynchronous replication topology matching real Redis behavior:
* **Role Identification:** Boot instances dynamically as `master` or `slave` using command-line arguments (e.g., `--replicaof localhost 6379`).
* **The Replication Handshake:** Replicas automatically initiate a background connection, verify capabilities, and request a state synchronization (`PSYNC`).
* **Dynamic RDB Generation:** The Master dynamically serializes its live, in-memory `ConcurrentHashMap` into a strict binary Redis Database (RDB) format.
* **State Hydration:** Replicas parse the binary RDB file directly from the raw TCP stream, bypassing string buffers to safely recreate historical database state.
* **Live Command Propagation:** The Master intercepts mutating commands (like `SET` or `DEL`) and broadcasts them down open TCP sockets to all connected replicas in real-time.
* **Synchronous Wait:** The `WAIT` command tracks exact byte-offsets transmitted over the network and halts execution until the requested number of replicas acknowledge receipt via `REPLCONF ACK`.

## Running the Server

Start the Master instance on the default port (6379):
```bash
java -jar javaRedis.jar