package components.service.commands;

import java.util.List;

import components.repository.Store;
import components.service.RedisCommand;
import components.service.RespSerializer;
import components.service.ResponseDto;

public class LrangeCommand implements RedisCommand {
    @Override
    public ResponseDto execute(List<String> args, Store store) {
        if (args.size() < 4) {
            return new ResponseDto(RespSerializer.serializeError("wrong number of arguments"), false);
        }

        String key = args.get(1);
        Object existingData = store.getValue(key);

        if (existingData == null) {
            // Empty list returns an empty array, not a null string
            return new ResponseDto("*0\r\n", false);
        }

        if (!(existingData instanceof List<?> list)) {
            return new ResponseDto(RespSerializer.serializeError("WRONGTYPE Operation against a key holding the wrong kind of value"), false);
        }

        int start, stop;
        try {
            start = Integer.parseInt(args.get(2));
            stop = Integer.parseInt(args.get(3));
        } catch (NumberFormatException e) {
            return new ResponseDto(RespSerializer.serializeError("value is not an integer or out of range"), false);
        }

        synchronized (list) {
            int size = list.size();

            // Normalize negative indices
            if (start < 0) start = Math.max(0, size + start);
            if (stop < 0) stop = Math.max(0, size + stop);

            // Clamp out-of-bounds indices
            start = Math.min(start, size);
            stop = Math.min(stop, size - 1);

            if (start > stop || start >= size) {
                return new ResponseDto("*0\r\n", false);
            }

            // Build the RESP Array response
            List<?> subList = list.subList(start, stop + 1);
            StringBuilder response = new StringBuilder();
            response.append("*").append(subList.size()).append("\r\n");

            for (Object item : subList) {
                String strItem = (String) item;
                response.append("$").append(strItem.length()).append("\r\n");
                response.append(strItem).append("\r\n");
            }

            return new ResponseDto(response.toString(), false);
        }
    }
}
