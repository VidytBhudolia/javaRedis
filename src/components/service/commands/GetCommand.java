package components.service.commands;

import java.util.List;

import components.repository.Store;
import components.service.ClientSession;
import components.service.RedisCommand;
import components.service.RespSerializer;
import components.service.ResponseDto;

public class GetCommand implements RedisCommand {
    @Override
    public ResponseDto execute(List<String> args, Store store, ClientSession session) {
        if (args.size() < 2) {
            return new ResponseDto(RespSerializer.serializeError("wrong number of arguments for 'get'"), false);
        }

        String key = args.get(1);
        Object data = store.getValue(key);

        if (data == null) {
            return new ResponseDto("$-1\r\n", false); // Null Bulk String
        }

        // WRONGTYPE Protection: Ensure someone isn't trying to GET a List
        if (!(data instanceof String)) {
            return new ResponseDto(RespSerializer.serializeError("WRONGTYPE Operation against a key holding the wrong kind of value"), false);
        }

        return new ResponseDto(RespSerializer.serializeBulkString((String) data), false);

    }
}
