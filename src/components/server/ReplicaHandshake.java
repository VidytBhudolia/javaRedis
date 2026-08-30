package components.server;

import components.service.ClientSession;
import components.service.CommandHandler;
import components.service.RespSerializer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.util.List;

public class ReplicaHandshake implements Runnable {
    private final ServerState state;
    private final CommandHandler commandHandler; // NEW

    public ReplicaHandshake(ServerState state, CommandHandler commandHandler) {
        this.state = state;
        this.commandHandler = commandHandler;
    }

    @Override
    public void run() {
        try {
            Socket masterSocket = new Socket(state.getMasterHost(), state.getMasterPort());
            BufferedReader reader = new BufferedReader(new InputStreamReader(masterSocket.getInputStream()));
            OutputStream out = masterSocket.getOutputStream();

            sendCommand(out, "PING");
            System.out.println("Master replied: " + reader.readLine());

            sendCommand(out, "REPLCONF", "listening-port", String.valueOf(state.getPort()));
            System.out.println("Master replied: " + reader.readLine());

            sendCommand(out, "REPLCONF", "capa", "psync2");
            System.out.println("Master replied: " + reader.readLine());

            String psyncResponse = reader.readLine();
            System.out.println("Master replied: " + psyncResponse);

            // 1. Extract the starting offset from the +FULLRESYNC reply
            long replicaOffset = 0;
            if (psyncResponse != null && psyncResponse.startsWith("+FULLRESYNC")) {
                String[] parts = psyncResponse.split(" ");
                if (parts.length == 3) {
                    replicaOffset = Long.parseLong(parts[2]);
                }
            }

            // 2. Consume the RDB File to clear the TCP stream
            String dirLine = reader.readLine(); // Reads the "$88" length header
            if (dirLine != null && dirLine.startsWith("$")) {
                int rdbLength = Integer.parseInt(dirLine.substring(1));
                char[] rdbBuffer = new char[rdbLength];
                reader.read(rdbBuffer, 0, rdbLength);
                System.out.println("Received RDB file of " + rdbLength + " bytes.");
            }

            System.out.println("Replication Handshake Complete! Listening for Master commands...");


            while (true) {
                List<String> propagatedCommand = RespSerializer.deserializeArray(reader);
                if (propagatedCommand == null || propagatedCommand.isEmpty()) break;

                // 1. Calculate the exact byte length of the received command
                StringBuilder sb = new StringBuilder();
                sb.append("*").append(propagatedCommand.size()).append("\r\n");
                for (String arg : propagatedCommand) {
                    sb.append("$").append(arg.length()).append("\r\n").append(arg).append("\r\n");
                }
                replicaOffset += sb.toString().getBytes().length;

                // 2. Intercept GETACK and reply immediately
                if (propagatedCommand.get(0).equalsIgnoreCase("REPLCONF") &&
                    propagatedCommand.get(1).equalsIgnoreCase("GETACK")) {

                    sendCommand(out, "REPLCONF", "ACK", String.valueOf(replicaOffset));
                    continue; // Skip local execution
                }

                // 3. Normal execution
                commandHandler.execute(propagatedCommand, new ClientSession());
            }

        } catch (Exception e) {
            System.err.println("Disconnected from Master: " + e.getMessage());
        }
    }

    private void sendCommand(OutputStream out, String... args) throws Exception {
        StringBuilder builder = new StringBuilder();
        builder.append("*").append(args.length).append("\r\n");
        for (String arg : args) {
            builder.append("$").append(arg.length()).append("\r\n").append(arg).append("\r\n");
        }
        out.write(builder.toString().getBytes());
        out.flush();
    }
}