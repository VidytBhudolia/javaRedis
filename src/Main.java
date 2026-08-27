package com.redisclone;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class Main {

    record RedisValue(String value, Long expiryTimeMs) {}

    private static final ConcurrentHashMap<String, RedisValue> database = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        int port = 6379;
        restoreFromAOF();
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("javaRedis server running on port " + port + " (JDK 26)");

            while (true) {
            Socket clientSocket = serverSocket.accept();
            System.out.println("Client connected: " + clientSocket.getRemoteSocketAddress());

            Thread.startVirtualThread(() -> {
                    handleClient(clientSocket);
                });
        }

        } catch (IOException e) {
            System.err.println("Server exception: " + e.getMessage());
        }
    }

    private static void handleClient(Socket clientSocket) {
        try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                OutputStream out = clientSocket.getOutputStream()
        ) {
            while (true) {
                List<String> commandArgs = parseRESP(reader);
                if (commandArgs == null || commandArgs.isEmpty()) {
                    break;
                }

                System.out.println("Executing: " + commandArgs);
                boolean mutatedDatabase = executeCommand(commandArgs, out);

                if (mutatedDatabase) {
                    appendToAOF(commandArgs);
                }

            }
        } catch (IOException e) {
            System.out.println("Client disconnected: " + e.getMessage());
        }
    }

    private static List<String> parseRESP(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        if (line == null) return null;

        if (line.startsWith("*")) {
            int numElements = Integer.parseInt(line.substring(1));
            List<String> args = new ArrayList<>();

            for (int i = 0; i < numElements; i++) {
                String lengthLine = reader.readLine();
                if (lengthLine != null && lengthLine.startsWith("$")) {
                    String arg = reader.readLine();
                    args.add(arg);
                }
            }
            return args;
        }

        return List.of(line.split(" "));
    }

    private static boolean executeCommand(List<String> args, OutputStream out) throws IOException {
        String command = args.get(0).toUpperCase();
        boolean mutatedDatabase = false;

        switch (command) {
            case "PING" -> {
                out.write("+PONG\r\n".getBytes());
            }

            case "ECHO" -> {
                if (args.size() > 1) {
                    String message = args.get(1);
                    out.write(("$" + message.length() + "\r\n" + message + "\r\n").getBytes());
                } else {
                    out.write("-ERR wrong number of arguments for 'echo'\r\n".getBytes());
                }
            }

            case "SET" -> {
                if (args.size() >= 3) {
                    String key = args.get(1);
                    String value = args.get(2);
                    Long expiryTimeMs = null;

                    if (args.size() >= 5 && args.get(3).toUpperCase().equals("PX")) {
                        long pxDuration = Long.parseLong(args.get(4));
                        expiryTimeMs = System.currentTimeMillis() + pxDuration;
                    }

                    database.put(key, new RedisValue(value, expiryTimeMs));
                    out.write("+OK\r\n".getBytes());
                    mutatedDatabase = true;
                } else {
                    out.write("-ERR wrong number of arguments for 'set'\r\n".getBytes());
                }
            }

            case "GET" -> {
                if (args.size() >= 2) {
                    String key = args.get(1);
                    RedisValue redisValue = database.get(key);

                    if(redisValue != null){
                        if (redisValue.expiryTimeMs() != null && System.currentTimeMillis() > redisValue.expiryTimeMs()) {
                            database.remove(key);
                            redisValue = null;
                        }
                    }
                    if (redisValue == null) {
                        out.write("$-1\r\n".getBytes());
                    } else {
                        String value = redisValue.value();
                        out.write(("$" + value.length() + "\r\n" + value + "\r\n").getBytes());
                    }
                } else {
                    out.write("-ERR wrong number of arguments for 'get'\r\n".getBytes());
                }
            }

            default -> {
                out.write(("-ERR unknown command '" + command + "'\r\n").getBytes());
            }
        }
        out.flush();
        return mutatedDatabase;
    }

    private static synchronized void appendToAOF(List<String> args) {
        try (FileOutputStream fos = new FileOutputStream("appendonly.aof", true)) {

            StringBuilder resp = new StringBuilder();
            resp.append("*").append(args.size()).append("\r\n");

            for (String arg : args) {
                resp.append("$").append(arg.length()).append("\r\n");
                resp.append(arg).append("\r\n");
            }

            fos.write(resp.toString().getBytes());
            fos.getFD().sync();

        } catch (IOException e) {
            System.err.println("Failed to write AOF: " + e.getMessage());
        }
    }

    private static void restoreFromAOF() {
        File aofFile = new File("appendonly.aof");
        if (!aofFile.exists()) {
            return; // Fresh start, no history
        }

        System.out.println("Restoring database from AOF...");

        try (BufferedReader reader = new BufferedReader(new FileReader(aofFile))) {
            // System Concept: The Black Hole Stream
            OutputStream blackHoleStream = OutputStream.nullOutputStream();

            while (true) {
                List<String> commandArgs = parseRESP(reader);
                if (commandArgs == null || commandArgs.isEmpty()) {
                    break; // Reached end of file
                }

                // Replay the command, throwing away the "+OK" responses
                executeCommand(commandArgs, blackHoleStream);
            }
            System.out.println("Restore complete.");
        } catch (IOException e) {
            System.err.println("Error reading AOF: " + e.getMessage());
        }
    }

}