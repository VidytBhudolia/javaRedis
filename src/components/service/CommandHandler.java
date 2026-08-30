package components.service;

import components.infra.ConnectionPool;
import components.repository.Store;
import components.server.ServerState;
import components.service.commands.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommandHandler {
    private final Store store;
    private final ServerState serverState;
    // The Registry mapping a string like "SET" to its specific class
    private final Map<String, RedisCommand> commandRegistry;
    private final ConnectionPool connectionPool;

    public CommandHandler(Store store, ServerState serverState, ConnectionPool connectionPool) {
        this.store = store;
        this.serverState = serverState;
        this.commandRegistry = new HashMap<>();
        this.connectionPool =connectionPool;

        // Register all commands here
        commandRegistry.put("PING", new PingCommand());
        commandRegistry.put("ECHO", new EchoCommand());
        commandRegistry.put("SET", new SetCommand());
        commandRegistry.put("GET", new GetCommand());
        commandRegistry.put("TYPE", new TypeCommand());
        commandRegistry.put("DEL", new DelCommand());


        commandRegistry.put("LPUSH", new PushCommand(true));
        commandRegistry.put("RPUSH", new PushCommand(false));
        commandRegistry.put("LPOP", new PopCommand(true));
        commandRegistry.put("RPOP", new PopCommand(false));
        commandRegistry.put("BLPOP", new BpopCommand(true));
        commandRegistry.put("BRPOP", new BpopCommand(false));

        commandRegistry.put("LRANGE", new LrangeCommand());
        commandRegistry.put("LLEN", new LlenCommand());
        commandRegistry.put("LREM", new LremCommand());

        commandRegistry.put("XADD", new XaddCommand());
        commandRegistry.put("XRANGE", new XrangeCommand());
        commandRegistry.put("XREAD", new XreadCommand());

        commandRegistry.put("INCR", new IncrCommand());

        commandRegistry.put("WATCH", new WatchCommand());
        commandRegistry.put("UNWATCH", new UnwatchCommand());

        commandRegistry.put("INFO", new InfoCommand(this.serverState));
        commandRegistry.put("REPLCONF", new ReplconfCommand());
        commandRegistry.put("PSYNC", new PsyncCommand(this.serverState));
        commandRegistry.put("WAIT", new WaitCommand(serverState, connectionPool));
    }

    // Notice we added ClientSession session to the parameters
    public ResponseDto execute(List<String> args, ClientSession session) {
        if (args == null || args.isEmpty()) {
            return new ResponseDto(RespSerializer.serializeError("empty command"), false);
        }

        String commandName = args.get(0).toUpperCase();

        // 1. Transaction Lifecycle Commands
        if (commandName.equals("MULTI")) {
            session.beginTransaction();
            return new ResponseDto(RespSerializer.serializeSimpleString("OK"), false);
        }

        if (commandName.equals("DISCARD")) {
            if (!session.isMulti()) {
                return new ResponseDto(RespSerializer.serializeError("DISCARD without MULTI"), false);
            }
            session.discardTransaction();
            return new ResponseDto(RespSerializer.serializeSimpleString("OK"), false);
        }

        if (commandName.equals("EXEC")) {
            if (!session.isMulti()) {
                return new ResponseDto(RespSerializer.serializeError("EXEC without MULTI"), false);
            }

            // NEW: Optimistic Locking Validation
            boolean isTransactionValid = true;
            for (Map.Entry<String, Long> entry : session.getWatchedKeys().entrySet()) {
                if (store.getVersion(entry.getKey()) != entry.getValue()) {
                    isTransactionValid = false;
                    break;
                }
            }

            // If a watched key was changed by someone else, abort!
            if (!isTransactionValid) {
                session.getQueueAndClear(); // Flush the queue
                return new ResponseDto("$-1\r\n", false); // Return nil array
            }

            // Get the queue and automatically close the transaction
            List<List<String>> queue = session.getQueueAndClear();

            // Build a massive RESP array containing the results of every command in the queue
            StringBuilder execResponse = new StringBuilder();
            execResponse.append("*").append(queue.size()).append("\r\n");

            boolean databaseMutated = false;

            for (List<String> queuedArgs : queue) {
                RedisCommand cmd = commandRegistry.get(queuedArgs.get(0).toUpperCase());
                if (cmd == null) {
                    // Edge case: someone queued an invalid command name
                    execResponse.append(RespSerializer.serializeError("unknown command"));
                } else {
                    // Execute the command against the store
                    ResponseDto result = cmd.execute(queuedArgs, store, session);
                    execResponse.append(result.responseString());
                    if (result.mutatedDatabase()) {
                        databaseMutated = true;
                    }
                }
            }
            return new ResponseDto(execResponse.toString(), databaseMutated);
        }

        // 2. Lookup standard commands (SET, GET, XRANGE, etc.)
        RedisCommand command = commandRegistry.get(commandName);
        if (command == null) {
            return new ResponseDto(RespSerializer.serializeError("unknown command '" + commandName + "'"), false);
        }

        // 3. Command Interception: Are we currently inside a transaction?
        if (session.isMulti()) {
            session.enqueueCommand(args);
            return new ResponseDto(RespSerializer.serializeSimpleString("QUEUED"), false); // Notice it returns +QUEUED
        }

        // 4. Normal Live Execution
        return command.execute(args, store, session);
    }
}