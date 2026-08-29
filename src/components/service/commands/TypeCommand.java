package components.service.commands;

import java.util.List;

import components.repository.Store;
import components.service.ClientSession;
import components.service.RedisCommand;
import components.service.RespSerializer;
import components.service.ResponseDto;

public class TypeCommand implements RedisCommand {
    @Override
    public ResponseDto execute(List<String> args, Store store, ClientSession session) {
        if (args.size() < 2) {
            return new ResponseDto(RespSerializer.serializeError("wrong number of arguments for 'type'"), false);
        }

        Object data = store.getValue(args.get(1));

        if (data == null) {
            return new ResponseDto(RespSerializer.serializeSimpleString("none"), false);
        } else if (data instanceof String) {
            return new ResponseDto(RespSerializer.serializeSimpleString("string"), false);
        } else if (data instanceof java.util.List) {
            return new ResponseDto(RespSerializer.serializeSimpleString("list"), false);
        } else if (data instanceof java.util.LinkedHashMap) { // We will use LinkedHashMap to represent the Stream itself
            return new ResponseDto(RespSerializer.serializeSimpleString("stream"), false);
        }

        return new ResponseDto(RespSerializer.serializeSimpleString("unknown"), false);

    }
}
