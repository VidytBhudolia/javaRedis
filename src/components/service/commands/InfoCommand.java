package components.service.commands;

import components.repository.Store;
import components.server.ServerState;
import components.service.ClientSession;
import components.service.RedisCommand;
import components.service.RespSerializer;
import components.service.ResponseDto;

import java.util.List;

public class InfoCommand implements RedisCommand {
    private final ServerState state;

    public InfoCommand(ServerState state) {
        this.state = state;
    }

    @Override
    public ResponseDto execute(List<String> args, Store store, ClientSession session) {
        if (args.size() > 1 && args.get(1).equalsIgnoreCase("replication")) {
            StringBuilder info = new StringBuilder();

            info.append("role:").append(state.getRole()).append("\n");

            if (state.getRole().equals("master")) {
                info.append("master_replid:").append(state.getMasterReplId()).append("\n");
                info.append("master_repl_offset:").append(state.getMasterReplOffset()).append("\n");
            }

            return new ResponseDto(RespSerializer.serializeBulkString(info.toString()), false);
        }

        // Default empty info for other sections
        return new ResponseDto(RespSerializer.serializeBulkString(""), false);
    }
}