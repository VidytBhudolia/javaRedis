package components.server;

import components.repository.RdbParser;
import components.service.ClientSession;
import components.service.CommandHandler;
import components.service.RespSerializer;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.List;

public class ReplicaHandshake implements Runnable {
    private final ServerState state;
    private final CommandHandler commandHandler;

    public ReplicaHandshake(ServerState state, CommandHandler commandHandler) {
        this.state = state;
        this.commandHandler = commandHandler;
    }

    @Override
    public void run() {
        try {
            Socket masterSocket = new Socket(state.getMasterHost(), state.getMasterPort());

            // 1. We start with the RAW InputStream so we don't over-buffer binary data
            InputStream in = masterSocket.getInputStream();
            OutputStream out = masterSocket.getOutputStream();

            sendCommand(out, "PING");
            System.out.println("Master replied: " + readLine(in));

            sendCommand(out, "REPLCONF", "listening-port", String.valueOf(state.getPort()));
            System.out.println("Master replied: " + readLine(in));

            sendCommand(out, "REPLCONF", "capa", "psync2");
            System.out.println("Master replied: " + readLine(in));

            sendCommand(out, "PSYNC", "?", "-1");
            String psyncResponse = readLine(in);
            System.out.println("Master replied: " + psyncResponse);

            long replicaOffset = 0;
            if (psyncResponse != null && psyncResponse.startsWith("+FULLRESYNC")) {
                String[] parts = psyncResponse.split(" ");
                if (parts.length == 3) {
                    replicaOffset = Long.parseLong(parts[2]);
                }
            }

            // 2. Consume the exact bytes of the RDB File directly from the stream
            String dirLine = readLine(in);
            if (dirLine != null && dirLine.startsWith("$")) {
                int rdbLength = Integer.parseInt(dirLine.substring(1));

                byte[] rdbBytes = new byte[rdbLength];
                int totalRead = 0;
                while (totalRead < rdbLength) {
                    int read = in.read(rdbBytes, totalRead, rdbLength - totalRead);
                    if (read == -1) break;
                    totalRead += read;
                }
                // NEW: Actually parse the bytes and load them into the Replica's database!
                RdbParser.parseAndLoad(rdbBytes, commandHandler.getStore());

                System.out.println("Received RDB file of " + totalRead + " bytes. Starting offset at: " + replicaOffset);
            }

            System.out.println("Replication Handshake Complete! Listening for Master commands...");

            // 3. The RDB file is cleared! It is now safe to wrap the stream in a BufferedReader
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));

            while (true) {
                List<String> propagatedCommand = RespSerializer.deserializeArray(reader);
                if (propagatedCommand == null || propagatedCommand.isEmpty()) break;

                StringBuilder sb = new StringBuilder();
                sb.append("*").append(propagatedCommand.size()).append("\r\n");
                for (String arg : propagatedCommand) {
                    sb.append("$").append(arg.length()).append("\r\n").append(arg).append("\r\n");
                }
                replicaOffset += sb.toString().getBytes().length;

                if (propagatedCommand.get(0).equalsIgnoreCase("REPLCONF") &&
                    propagatedCommand.get(1).equalsIgnoreCase("GETACK")) {
                    sendCommand(out, "REPLCONF", "ACK", String.valueOf(replicaOffset));
                    continue;
                }

                commandHandler.execute(propagatedCommand, new ClientSession());
            }

        } catch (Exception e) {
            System.err.println("Disconnected from Master: " + e.getMessage());
        }
    }

    // Helper method to write commands to the Master
    private void sendCommand(OutputStream out, String... args) throws Exception {
        StringBuilder builder = new StringBuilder();
        builder.append("*").append(args.length).append("\r\n");
        for (String arg : args) {
            builder.append("$").append(arg.length()).append("\r\n").append(arg).append("\r\n");
        }
        out.write(builder.toString().getBytes());
        out.flush();
    }

    // Custom helper to read text line-by-line without an aggressive char buffer
    private String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\r') {
                int next = in.read();
                if (next == '\n') break;
                sb.append((char) b).append((char) next);
            } else if (b == '\n') {
                break;
            } else {
                sb.append((char) b);
            }
        }
        return sb.toString();
    }
}