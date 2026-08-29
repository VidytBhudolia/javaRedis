package components.service;

import components.repository.Store;
import components.service.commands.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommandHandler {
    private final Store store;

    // The Registry mapping a string like "SET" to its specific class
    private final Map<String, RedisCommand> commandRegistry;

    public CommandHandler(Store store) {
        this.store = store;
        this.commandRegistry = new HashMap<>();

        // Register all commands here
        commandRegistry.put("PING", new PingCommand());
        commandRegistry.put("ECHO", new EchoCommand());
        commandRegistry.put("SET", new SetCommand());
        commandRegistry.put("GET", new GetCommand());
        commandRegistry.put("TYPE", new TypeCommand());


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
    }

    public ResponseDto execute(List<String> args) {
        if (args == null || args.isEmpty()) {
            return new ResponseDto(RespSerializer.serializeError("empty command"), false);
        }

        String commandName = args.get(0).toUpperCase();

        // Look up the command in the registry
        RedisCommand command = commandRegistry.get(commandName);

        if (command == null) {
            return new ResponseDto(RespSerializer.serializeError("unknown command '" + commandName + "'"), false);
        }

        // Execute the dynamically looked-up command
        return command.execute(args, store);
    }
}