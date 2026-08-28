package components.infra;

import components.service.CommandHandler;
import components.service.RespSerializer;

import java.io.*;
import java.net.Socket;
import java.util.List;

// Implements Runnable so it can be passed directly to a Virtual Thread
public class Client implements Runnable, Closeable {
    private final Socket socket;
    private final CommandHandler commandHandler;
    private final ConnectionPool pool;

    public Client(Socket socket, CommandHandler commandHandler, ConnectionPool pool) {
        this.socket = socket;
        this.commandHandler = commandHandler;
        this.pool = pool;
    }

    @Override
    public void run() {
        // Register this client in the pool the moment the thread starts
        pool.add(this);
        System.out.println("Client connected: " + socket.getRemoteSocketAddress());

        try (
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            OutputStream out = socket.getOutputStream()
        ) {
            while (true) {
                // 1. Read and parse the raw network bytes
                List<String> commandArgs = RespSerializer.deserializeArray(reader);

                if (commandArgs == null || commandArgs.isEmpty()) {
                    break; // Client closed their terminal
                }

                // 2. Pass the parsed list to the Engine
                String response = commandHandler.execute(commandArgs);

                // 3. Write the formatted response back to the client
                out.write(response.getBytes());
                out.flush();
            }
        } catch (IOException e) {
            System.out.println("Client disconnected unexpectedly: " + e.getMessage());
        } finally {
            // System Concept: Guaranteed Cleanup
            pool.remove(this);
            closeQuietly();
        }
    }

    @Override
    public void close() throws IOException {
        if (!socket.isClosed()) {
            socket.close();
        }
    }

    private void closeQuietly() {
        try {
            close();
        } catch (IOException e) {
            // Ignore
        }
    }
}