package components.service.commands;

import java.util.List;

import components.repository.Store;
import components.service.ClientSession;
import components.service.RedisCommand;
import components.service.RespSerializer;
import components.service.ResponseDto;

public class LlenCommand implements RedisCommand {
    @Override
    public ResponseDto execute(List<String> args, Store store, ClientSession session) {
        if (args.size() < 2) {
            return new ResponseDto(RespSerializer.serializeError("wrong number of arguments for 'llen'"), false);
        }

        String key = args.get(1);
        Object data = store.getValue(key);

        if (data == null) {
            return new ResponseDto(":0\r\n", false); // Missing keys default to length 0
        }

        if (!(data instanceof List<?> list)) {
            return new ResponseDto(RespSerializer.serializeError("WRONGTYPE Operation against a key holding the wrong kind of value"), false);
        }

        // Return the integer size
        return new ResponseDto(":" + list.size() + "\r\n", false);

    }
}
