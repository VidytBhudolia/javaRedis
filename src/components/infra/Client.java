package components.infra;

import components.service.ClientSession;
import components.service.CommandHandler;
import components.service.RespSerializer;
import components.service.ResponseDto;

import java.io.*;
import java.net.Socket;
import java.util.List;

// Implements Runnable so it can be passed directly to a Virtual Thread
public class Client implements Runnable, Closeable {
    private final Socket socket;
    private final CommandHandler commandHandler;
    private final ConnectionPool pool;
    private boolean isReplica = false;
    private OutputStream out; // Elevate this so other methods can access it
    private ClientSession session;

    public Client(Socket socket, CommandHandler commandHandler, ConnectionPool pool) {
        this.socket = socket;
        this.commandHandler = commandHandler;
        this.pool = pool;
        this.session = new ClientSession();
    }

    @Override
    public void run() {
        pool.add(this);
        System.out.println("Client connected: " + socket.getRemoteSocketAddress());

        try (
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            OutputStream outputStream = socket.getOutputStream()
        ) {
            this.out = outputStream; // Assign to instance variable

            while (true) {
                List<String> commandArgs = RespSerializer.deserializeArray(reader);
                if (commandArgs == null || commandArgs.isEmpty()) break;

                // NEW: Identify if this connection is a Replica Handshake
                if (commandArgs.get(0).equalsIgnoreCase("PSYNC")) {
                    this.isReplica = true;
                }

                ResponseDto dto = commandHandler.execute(commandArgs, session);

                if (dto != null) {
                    if (dto.responseString() != null) {
                        out.write(dto.responseString().getBytes());
                    }
                    if (dto.rawBytes() != null) {
                        out.write(dto.rawBytes());
                    }
                    out.flush();

                    // NEW: Command Propagation Broadcast
                    if (dto.mutatedDatabase()) {
                        pool.broadcastToReplicas(commandArgs);
                    }
                }
            }
        }catch (IOException e) {
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

    public void sendRaw(byte[] payload) {
        try {
            if (this.out != null) {
                this.out.write(payload);
                this.out.flush();
            }
        } catch (IOException e) {
            System.err.println("Failed to broadcast to replica");
        }
    }

    public boolean isReplica() {
        return isReplica;
    }

    public ClientSession getSession() {
        return session;
    }
}