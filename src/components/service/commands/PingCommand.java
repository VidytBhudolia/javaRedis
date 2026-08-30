package components.service.commands;

import components.repository.Store;
import components.service.ClientSession;
import components.service.RedisCommand;
import components.service.RespSerializer;
import components.service.ResponseDto;
import java.util.List;

public class PingCommand implements RedisCommand {
    @Override
    public ResponseDto execute(List<String> args, Store store, ClientSession session) {
        return new ResponseDto(RespSerializer.serializeSimpleString("PONG"), false);
    }
}