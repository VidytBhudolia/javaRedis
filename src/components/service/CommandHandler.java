package components.service;

import components.repository.Store;
import java.util.List;

public class CommandHandler {
    // The handler needs access to the database to do its job
    private final Store store;

    public CommandHandler(Store store) {
        this.store = store;
    }

    public String execute(List<String> args) {
        if (args == null || args.isEmpty()) {
            return RespSerializer.serializeError("empty command");
        }

        String command = args.get(0).toUpperCase();

        // Modern Java Switch Expression to route the command
        return switch (command) {
            case "PING" -> handlePing();
            case "ECHO" -> handleEcho(args);
            case "SET"  -> handleSet(args);
            case "GET"  -> handleGet(args);
            default     -> RespSerializer.serializeError("unknown command '" + command + "'");
        };
    }

    private String handlePing() {
        return RespSerializer.serializeSimpleString("PONG");
    }

    private String handleEcho(List<String> args) {
        if (args.size() < 2) {
            return RespSerializer.serializeError("wrong number of arguments for 'echo'");
        }
        return RespSerializer.serializeBulkString(args.get(1));
    }

    private String handleSet(List<String> args) {
        if (args.size() < 3) {
            return RespSerializer.serializeError("wrong number of arguments for 'set'");
        }

        String key = args.get(1);
        String value = args.get(2);
        Long expiryTimeMs = null;

        // Parse optional PX argument
        if (args.size() >= 5 && args.get(3).toUpperCase().equals("PX")) {
            try {
                long pxDuration = Long.parseLong(args.get(4));
                expiryTimeMs = System.currentTimeMillis() + pxDuration;
            } catch (NumberFormatException e) {
                return RespSerializer.serializeError("value is not an integer or out of range");
            }
        }

        // Delegate the actual saving to the Store
        store.set(key, value, expiryTimeMs);
        return RespSerializer.serializeSimpleString("OK");
    }

    private String handleGet(List<String> args) {
        if (args.size() < 2) {
            return RespSerializer.serializeError("wrong number of arguments for 'get'");
        }

        String key = args.get(1);
        // Delegate the retrieval (and lazy eviction) to the Store
        String value = store.get(key);

        return RespSerializer.serializeBulkString(value);
    }
}