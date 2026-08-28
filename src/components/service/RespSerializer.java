package components.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class RespSerializer {

    // Translates a simple string (like "OK") into "+OK\r\n"
    public static String serializeSimpleString(String message) {
        return "+" + message + "\r\n";
    }

    // Translates text into a bulk string (like "$4\r\nPING\r\n")
    public static String serializeBulkString(String message) {
        if (message == null) {
            return "$-1\r\n"; // The official Redis null response
        }
        return "$" + message.length() + "\r\n" + message + "\r\n";
    }

    // Translates an error into "-ERR message\r\n"
    public static String serializeError(String errorMessage) {
        return "-ERR " + errorMessage + "\r\n";
    }

    // Reads the raw network stream and translates it into a Java List ["SET", "apple", "red"]
    public static List<String> deserializeArray(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        if (line == null) {
            return null; // Client disconnected
        }

        // Handle standard Redis Array (*3\r\n)
        if (line.startsWith("*")) {
            int numElements = Integer.parseInt(line.substring(1));
            List<String> args = new ArrayList<>(numElements);

            for (int i = 0; i < numElements; i++) {
                String lengthLine = reader.readLine();
                if (lengthLine != null && lengthLine.startsWith("$")) {
                    args.add(reader.readLine()); // Grab the actual string
                }
            }
            return args;
        }

        // Fallback for raw inline commands (like typing "PING" in telnet)
        return List.of(line.split(" "));
    }
}