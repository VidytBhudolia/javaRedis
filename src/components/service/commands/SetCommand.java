package components.service.commands;

import components.repository.Store;
import components.service.ClientSession;
import components.service.RedisCommand;
import components.service.RespSerializer;
import components.service.ResponseDto;
import java.util.List;

public class SetCommand implements RedisCommand {
    @Override
    public ResponseDto execute(List<String> args, Store store, ClientSession session) {
        if (args.size() < 3) {
            return new ResponseDto(RespSerializer.serializeError("wrong number of arguments for 'set'"), false);
        }

        String key = args.get(1);
        String value = args.get(2);
        Long expiryTimeMs = null;

        if (args.size() >= 5 && args.get(3).toUpperCase().equals("PX")) {
            try {
                long pxDuration = Long.parseLong(args.get(4));
                expiryTimeMs = System.currentTimeMillis() + pxDuration;
            } catch (NumberFormatException e) {
                return new ResponseDto(RespSerializer.serializeError("value is not an integer or out of range"), false);
            }
        }

        store.setString(key, value, expiryTimeMs);
        return new ResponseDto(RespSerializer.serializeSimpleString("OK"), true);
    }
}