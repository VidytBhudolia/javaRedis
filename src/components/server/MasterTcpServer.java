package components.server;

import components.infra.Client;
import components.infra.ConnectionPool;
import components.repository.Store;
import components.service.CommandHandler;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class MasterTcpServer {
    private final ServerState state;

    // The server holds the single source of truth for the app
    private final Store store;
    private final ConnectionPool connectionPool;
    private final CommandHandler commandHandler;

    public MasterTcpServer(ServerState state) {
        this.state = state;
        this.store = new Store();
        this.connectionPool = new ConnectionPool(this.state);
        // Pass state into the command handler
        this.commandHandler = new CommandHandler(this.store, this.state, this.connectionPool);
    }

    public void start() {
        if (state.getRole().equals("slave")) {
            Thread.startVirtualThread(new ReplicaHandshake(state, commandHandler));
        }

        try (ServerSocket serverSocket = new ServerSocket(state.getPort())) {
            System.out.println("javaRedis " + state.getRole() + " running on port " + state.getPort());

            while (true) {
                // 2. Block until a network connection arrives
                Socket socket = serverSocket.accept();

                // 3. Package the connection into our Client task
                Client client = new Client(socket, commandHandler, connectionPool);

                // 4. Hand the task to a Virtual Thread and immediately loop back
                Thread.startVirtualThread(client);
            }
        } catch (IOException e) {
            System.err.println("Critical server crash: " + e.getMessage());
        } finally {
            // 5. If the server loop breaks, forcefully disconnect everyone
            connectionPool.closeAll();
        }
    }
}