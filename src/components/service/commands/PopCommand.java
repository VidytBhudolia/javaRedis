package components.service.commands;

import java.util.List;

import components.repository.Store;
import components.service.ClientSession;
import components.service.RedisCommand;
import components.service.RespSerializer;
import components.service.ResponseDto;

public class PopCommand implements RedisCommand {

    private final boolean isLeft;

    public PopCommand(boolean isLeft) {
        this.isLeft = isLeft;
    }

    @Override
    public ResponseDto execute(List<String> args, Store store, ClientSession session) {
        if (args.size() < 2) {
            return new ResponseDto(RespSerializer.serializeError("wrong number of arguments"), false);
        }

        String key = args.get(1);
        Object data = store.getValue(key);

        if (data == null) {
            return new ResponseDto("$-1\r\n", false); // Nil
        }

        if (!(data instanceof List<?> listStr)) {
            return new ResponseDto(RespSerializer.serializeError("WRONGTYPE"), false);
        }

        @SuppressWarnings("unchecked")
        List<String> list = (List<String>) listStr;
        String poppedValue;

        synchronized (list) {
            if (list.isEmpty()) {
                return new ResponseDto("$-1\r\n", false);
            }
            poppedValue = isLeft ? list.remove(0) : list.remove(list.size() - 1);

            if (list.isEmpty()) {
                store.delete(key);
            }
        }

        return new ResponseDto(RespSerializer.serializeBulkString(poppedValue), true);


    }
}
