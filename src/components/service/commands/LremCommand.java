package components.service.commands;

import java.util.List;

import components.repository.Store;
import components.service.RedisCommand;
import components.service.RespSerializer;
import components.service.ResponseDto;

public class LremCommand implements RedisCommand {
    @Override
    public ResponseDto execute(List<String> args, Store store) {
        if (args.size() < 4) {
            return new ResponseDto(RespSerializer.serializeError("wrong number of arguments for 'lrem'"), false);
        }

        String key = args.get(1);
        int count;
        try {
            count = Integer.parseInt(args.get(2));
        } catch (NumberFormatException e) {
            return new ResponseDto(RespSerializer.serializeError("value is not an integer or out of range"), false);
        }
        String element = args.get(3);

        Object data = store.getValue(key);
        if (data == null) {
            return new ResponseDto(":0\r\n", false);
        }

        if (!(data instanceof List<?> listStr)) {
            return new ResponseDto(RespSerializer.serializeError("WRONGTYPE Operation against a key holding the wrong kind of value"), false);
        }

        // Suppress warning because we know it's a list of strings
        @SuppressWarnings("unchecked")
        List<String> list = (List<String>) listStr;
        int removed = 0;

        synchronized (list) {
            if (count == 0) {
                // Remove all occurrences
                while (list.remove(element)) removed++;
            } else if (count > 0) {
                // Remove from head to tail
                for (int i = 0; i < list.size() && removed < count; ) {
                    if (list.get(i).equals(element)) {
                        list.remove(i);
                        removed++;
                    } else {
                        i++;
                    }
                }
            } else {
                // Remove from tail to head
                int absCount = Math.abs(count);
                for (int i = list.size() - 1; i >= 0 && removed < absCount; i--) {
                    if (list.get(i).equals(element)) {
                        list.remove(i);
                        removed++;
                    }
                }
            }

            // System Concept: Memory Management. If the list is empty, delete the key entirely.
            if (list.isEmpty()) {
                store.delete(key);
            }
        }

        return new ResponseDto(":" + removed + "\r\n", true);

    }

}
