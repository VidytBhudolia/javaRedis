package components.service.commands;

import components.repository.Store;
import components.service.ClientSession;
import components.service.RedisCommand;
import components.service.RespSerializer;
import components.service.ResponseDto;
import java.util.List;

public class UnwatchCommand implements RedisCommand {
    @Override
    public ResponseDto execute(List<String> args, Store store, ClientSession session) {
        // Clear all tracked versions for this specific network connection
        session.unwatch();

        return new ResponseDto(RespSerializer.serializeSimpleString("OK"), false);
    }
}