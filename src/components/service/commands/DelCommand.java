package components.service.commands;

import components.repository.Store;
import components.service.ClientSession;
import components.service.RedisCommand;
import components.service.RespSerializer;
import components.service.ResponseDto;

import java.util.List;

public class DelCommand implements RedisCommand {

    @Override
    public ResponseDto execute(List<String> args, Store store, ClientSession session) {
        if (args.size() < 2) {
            return new ResponseDto(RespSerializer.serializeError("wrong number of arguments for 'del'"), false);
        }

        int deletedCount = 0;
        for (int i = 1; i < args.size(); i++) {
            String key = args.get(i);

            // Verify existence to keep an accurate count for the response
            if (store.getValue(key) != null) {
                store.delete(key);
                deletedCount++;
            }
        }

        // Only broadcast to replicas if we actually deleted something
        boolean mutatedDatabase = deletedCount > 0;

        return new ResponseDto(":" + deletedCount + "\r\n", mutatedDatabase);
    }
}