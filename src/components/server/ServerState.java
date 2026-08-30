package components.server;

import java.util.UUID;

public class ServerState {
    private final int port;
    private final String role;
    private final String masterHost;
    private final int masterPort;
    private final String masterReplId;
    private long  masterReplOffset;

    public ServerState(int port, String masterHost, int masterPort) {
        this.port = port;
        this.masterHost = masterHost;
        this.masterPort = masterPort;

        if (masterHost != null) {
            this.role = "slave";
            this.masterReplId = "?"; // Replicas don't generate their own ID
        } else {
            this.role = "master";
            // A random 40-character alphanumeric string simulating a Redis Replication ID
            this.masterReplId = UUID.randomUUID().toString().replace("-", "") + "00000000";
        }
        this.masterReplOffset = 0;
    }

    public int getPort() { return port; }
    public String getRole() { return role; }
    public String getMasterReplId() { return masterReplId; }
    public long getMasterReplOffset() { return masterReplOffset; }
    public String getMasterHost() {
        return masterHost;
    }
    public int getMasterPort() {
        return masterPort;
    }
    public synchronized void addMasterReplOffset(long bytes) {
        this.masterReplOffset += bytes;
    }
}