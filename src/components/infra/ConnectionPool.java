package components.infra;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionPool {
    // A thread-safe Set backed by a ConcurrentHashMap to track active clients
    private final Set<Client> activeClients = ConcurrentHashMap.newKeySet();

    public void add(Client client) {
        activeClients.add(client);
    }

    public void remove(Client client) {
        activeClients.remove(client);
    }

    public void closeAll() {
        for (Client client : activeClients) {
            try {
                client.close();
            } catch (IOException e) {
                // Swallow the exception; we are shutting down anyway
            }
        }
        activeClients.clear();
    }
}