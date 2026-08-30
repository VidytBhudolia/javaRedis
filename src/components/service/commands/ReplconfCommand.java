package components.service.commands;

import components.repository.Store;
import components.service.ClientSession;
import components.service.RedisCommand;
import components.service.RespSerializer;
import components.service.ResponseDto;
import java.util.List;

public class ReplconfCommand implements RedisCommand {
    @Override
    public ResponseDto execute(List<String> args, Store store, ClientSession session) {
        if (args.size() >= 3 && args.get(1).equalsIgnoreCase("ACK")) {
            long offset = Long.parseLong(args.get(2));
            session.setAckedOffset(offset);

            // Return null strings so the Master does not send a +OK back down the replication stream
            return new ResponseDto(null, null, false);
        }

        return new ResponseDto(RespSerializer.serializeSimpleString("OK"), false);
    }
}