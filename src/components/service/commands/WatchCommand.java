package components.service.commands;

import components.repository.Store;
import components.service.ClientSession;
import components.service.RedisCommand;
import components.service.RespSerializer;
import components.service.ResponseDto;
import java.util.List;

public class WatchCommand implements RedisCommand {
    @Override
    public ResponseDto execute(List<String> args, Store store, ClientSession session) {
        if (args.size() < 2) {
            return new ResponseDto(RespSerializer.serializeError("wrong number of arguments for 'watch'"), false);
        }

        for (int i = 1; i < args.size(); i++) {
            String key = args.get(i);
            long currentVersion = store.getVersion(key);
            session.watch(key, currentVersion); // Memorize the version
        }

        return new ResponseDto(RespSerializer.serializeSimpleString("OK"), false);
    }
}