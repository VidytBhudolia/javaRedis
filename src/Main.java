import components.server.MasterTcpServer;
import components.server.ServerState;

public class Main {
    public static void main(String[] args) {
        int port = 6379;
        String masterHost = null;
        int masterPort = -1;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--port") && i + 1 < args.length) {
                port = Integer.parseInt(args[i + 1]);
            } else if (args[i].equals("--replicaof") && i + 2 < args.length) {
                masterHost = args[i + 1];
                masterPort = Integer.parseInt(args[i + 2]);
            }
        }

        ServerState state = new ServerState(port, masterHost, masterPort);
        MasterTcpServer server = new MasterTcpServer(state);
        server.start();
    }
}