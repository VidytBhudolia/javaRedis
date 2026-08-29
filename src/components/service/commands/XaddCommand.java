package components.service.commands;

import components.repository.Store;
import components.repository.StreamEntry;
import components.service.ClientSession;
import components.service.RedisCommand;
import components.service.RespSerializer;
import components.service.ResponseDto;

import java.util.LinkedHashMap;
import java.util.List;

public class XaddCommand implements RedisCommand {

    @Override
    public ResponseDto execute(List<String> args, Store store, ClientSession session) {
        if (args.size() < 5 || (args.size() - 3) % 2 != 0) {
            return new ResponseDto(RespSerializer.serializeError("wrong number of arguments for 'xadd'"), false);
        }

        String key = args.get(1);
        String rawId = args.get(2);
        Object data = store.getValue(key);

        LinkedHashMap<String, StreamEntry> stream;
        String lastId = "0-0";

        // 1. Initialize or retrieve the Stream
        if (data == null) {
            stream = new LinkedHashMap<>();
            store.putValue(key, stream, null);
        } else if (data instanceof LinkedHashMap) {
            @SuppressWarnings("unchecked")
            LinkedHashMap<String, StreamEntry> existingStream = (LinkedHashMap<String, StreamEntry>) data;
            stream = existingStream;

            // Get the last inserted ID
            if (!stream.isEmpty()) {
                String[] keys = stream.keySet().toArray(new String[0]);
                lastId = keys[keys.length - 1];
            }
        } else {
            return new ResponseDto(RespSerializer.serializeError("WRONGTYPE Operation against a key holding the wrong kind of value"), false);
        }

        // 2. Generate and Validate the ID
        String generatedId = generateAndValidateId(rawId, lastId);
        if (!generatedId.matches("^\\d+-\\d+$")) {
            return new ResponseDto(RespSerializer.serializeError(generatedId), false);
        }

        // 3. Parse the fields and values
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        for (int i = 3; i < args.size(); i += 2) {
            fields.put(args.get(i), args.get(i + 1));
        }

        // 4. Insert into the stream
        synchronized (stream) {
            stream.put(generatedId, new StreamEntry(generatedId, fields));
        }

        store.notifyDataArrived(key);

        return new ResponseDto(RespSerializer.serializeBulkString(generatedId), true);
    }

    private String generateAndValidateId(String rawId, String lastId) {
        long currentMs;
        long currentSeq;

        String[] lastParts = lastId.split("-");
        long lastMs = Long.parseLong(lastParts[0]);
        long lastSeq = Long.parseLong(lastParts[1]);

        if (rawId.equals("*")) {
            currentMs = System.currentTimeMillis();
            currentSeq = (currentMs == lastMs) ? lastSeq + 1 : 0;
        } else if (rawId.endsWith("-*")) {
            currentMs = Long.parseLong(rawId.split("-")[0]);
            currentSeq = (currentMs == lastMs) ? lastSeq + 1 : 0;
        } else {
            String[] rawParts = rawId.split("-");
            currentMs = Long.parseLong(rawParts[0]);
            currentSeq = Long.parseLong(rawParts[1]);
        }

        if (currentMs == 0 && currentSeq == 0) {
            return "The ID specified in XADD must be greater than 0-0";
        }

        if (currentMs < lastMs || (currentMs == lastMs && currentSeq <= lastSeq)) {
            return "The ID specified in XADD is equal or smaller than the target stream top item";
        }

        return currentMs + "-" + currentSeq;
    }
}