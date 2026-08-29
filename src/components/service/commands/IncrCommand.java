package components.service.commands;

import components.repository.Store;
import components.service.ClientSession;
import components.service.RedisCommand;
import components.service.RespSerializer;
import components.service.ResponseDto;

import java.util.List;

public class IncrCommand implements RedisCommand {

    @Override
    public ResponseDto execute(List<String> args, Store store, ClientSession session) {
        if (args.size() < 2) {
            return new ResponseDto(RespSerializer.serializeError("wrong number of arguments for 'incr'"), false);
        }

        String key = args.get(1);
        Object data = store.getValue(key);
        long value = 0;

        if (data != null) {
            if (!(data instanceof String)) {
                return new ResponseDto(RespSerializer.serializeError("WRONGTYPE Operation against a key holding the wrong kind of value"), false);
            }
            try {
                value = Long.parseLong((String) data);
            } catch (NumberFormatException e) {
                return new ResponseDto(RespSerializer.serializeError("value is not an integer or out of range"), false);
            }
        }

        // Increment and save back to the store
        value++;
        store.setString(key, String.valueOf(value), null);

        return new ResponseDto(":" + value + "\r\n", true);
    }
}