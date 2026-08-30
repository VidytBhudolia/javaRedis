package components.infra;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import components.server.ServerState;

public class ConnectionPool {
    // A thread-safe Set backed by a ConcurrentHashMap to track active clients
    private final CopyOnWriteArrayList<Client> clients = new CopyOnWriteArrayList<>();
    private final ServerState state;

    public ConnectionPool(ServerState state) {
        this.state = state;
    }

    public void add(Client client) {
        clients.add(client);
    }

    public void remove(Client client) {
        clients.remove(client);
    }

    public void closeAll() {
        for (Client client : clients) {
            try {
                client.close();
            } catch (IOException e) {
                // Swallow the exception; we are shutting down anyway
            }
        }
        clients.clear();
    }

    public void broadcastToReplicas(List<String> commandArgs) {
        StringBuilder builder = new StringBuilder();
        builder.append("*").append(commandArgs.size()).append("\r\n");
        for (String arg : commandArgs) {
            builder.append("$").append(arg.length()).append("\r\n").append(arg).append("\r\n");
        }
        byte[] payload = builder.toString().getBytes();

        state.addMasterReplOffset(payload.length);

        for (Client client : clients) {
            if (client.isReplica()) {
                client.sendRaw(payload);
            }
        }
    }

    public List<Client> getReplicas() {
        List<Client> replicas = new ArrayList<>();
        for (Client c : clients) {
            if (c.isReplica()) replicas.add(c);
        }
        return replicas;
    }

    public void broadcastGetAck() {
        String getAckPayload = "*3\r\n$8\r\nREPLCONF\r\n$6\r\nGETACK\r\n$1\r\n*\r\n";
        byte[] payload = getAckPayload.getBytes();
        for (Client client : clients) {
            if (client.isReplica()) client.sendRaw(payload);
        }
    }

}