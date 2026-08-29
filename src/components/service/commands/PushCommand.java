package components.service.commands;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import components.repository.Store;
import components.service.RedisCommand;
import components.service.RespSerializer;
import components.service.ResponseDto;

public class PushCommand implements RedisCommand {

    private final boolean isLeft;

    public PushCommand(boolean isLeft) {
        this.isLeft = isLeft;
    }

    @Override
    public ResponseDto execute(List<String> args, Store store) {
         if (args.size() < 3) {
            return new ResponseDto(RespSerializer.serializeError("wrong number of arguments"), false);
        }

        String key = args.get(1);
        Object existingData = store.getValue(key);
        List<String> list;

        // 1. Type Checking & Initialization
        if (existingData == null) {
            // System Concept: We use Collections.synchronizedList to ensure that
            // if two virtual threads push to this list simultaneously, it won't corrupt.
            list = Collections.synchronizedList(new LinkedList<>());
            store.putValue(key, list, null);
        } else if (existingData instanceof List<?> existingList) {
            list = (List<String>) existingList;
        } else {
            return new ResponseDto(RespSerializer.serializeError("WRONGTYPE Operation against a key holding the wrong kind of value"), false);
        }

        // 2. Insert Elements
        synchronized (list) { // Synchronize on the list for atomic bulk insertion
            for (int i = 2; i < args.size(); i++) {
                if (isLeft) {
                    list.add(0, args.get(i)); // Push to front
                } else {
                    list.add(args.get(i));    // Append to back
                }
            }
        }
        store.notifyDataArrived(key);
        // 3. Return the new length of the list (Standard Redis behavior)
        return new ResponseDto(":" + list.size() + "\r\n", true);

    }
}
