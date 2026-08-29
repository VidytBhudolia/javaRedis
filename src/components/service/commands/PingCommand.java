package components.service.commands;

import java.util.List;

import components.repository.Store;
import components.service.ClientSession;
import components.service.RedisCommand;
import components.service.RespSerializer;
import components.service.ResponseDto;

public class PingCommand implements RedisCommand {
    @Override
    public ResponseDto execute(List<String> args, Store store, ClientSession session) {
        if (args.size() < 2) {
            return new ResponseDto(RespSerializer.serializeError("wrong number of arguments for 'echo'"), false);
        }
        return new ResponseDto(RespSerializer.serializeBulkString(args.get(1)), false);
    }
}
