import components.server.MasterTcpServer;

public class Main {
    public static void main(String[] args) {
        // Define the default Redis port
        int port = 6379;

        // Instantiate the server shell
        MasterTcpServer server = new MasterTcpServer(port);

        // Boot the system
        server.start();
    }
}