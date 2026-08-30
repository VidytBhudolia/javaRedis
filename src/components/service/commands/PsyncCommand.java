package components.service.commands;

import components.repository.RdbSerializer;
import components.repository.Store;
import components.server.ServerState;
import components.service.ClientSession;
import components.service.RedisCommand;
import components.service.RespSerializer;
import components.service.ResponseDto;

import java.util.List;

public class PsyncCommand implements RedisCommand {
    private final ServerState state;

    public PsyncCommand(ServerState state) {
        this.state = state;
    }

    @Override
    public ResponseDto execute(List<String> args, Store store, ClientSession session) {
        String resyncStr = RespSerializer.serializeSimpleString("FULLRESYNC " + state.getMasterReplId() + " " + state.getMasterReplOffset());

        // NEW: Dynamically generate the binary snapshot of the current database
        byte[] rdbBytes = RdbSerializer.serialize(store);

        String rdbHeader = "$" + rdbBytes.length + "\r\n";

        return new ResponseDto(resyncStr + rdbHeader, rdbBytes, false);
    }
}