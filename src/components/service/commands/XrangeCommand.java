package components.service.commands;

import components.repository.Store;
import components.repository.StreamEntry;
import components.service.RedisCommand;
import components.service.RespSerializer;
import components.service.ResponseDto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class XrangeCommand implements RedisCommand {

    @Override
    public ResponseDto execute(List<String> args, Store store) {
        if (args.size() < 4) {
            return new ResponseDto(RespSerializer.serializeError("wrong number of arguments for 'xrange'"), false);
        }

        String key = args.get(1);
        String startId = args.get(2);
        String endId = args.get(3);

        Object data = store.getValue(key);
        if (data == null) {
            return new ResponseDto("*0\r\n", false);
        }

        if (!(data instanceof LinkedHashMap)) {
            return new ResponseDto(RespSerializer.serializeError("WRONGTYPE Operation against a key holding the wrong kind of value"), false);
        }

        @SuppressWarnings("unchecked")
        LinkedHashMap<String, StreamEntry> stream = (LinkedHashMap<String, StreamEntry>) data;
        List<StreamEntry> results = new ArrayList<>();

        // 1. Iterate and filter based on bounds
        synchronized (stream) {
            for (Map.Entry<String, StreamEntry> entry : stream.entrySet()) {
                String currentId = entry.getKey();

                boolean isGreaterOrEqualStart = StreamEntry.compareIds(currentId, startId, false) >= 0;
                boolean isLessOrEqualEnd = StreamEntry.compareIds(currentId, endId, true) <= 0;

                if (isGreaterOrEqualStart && isLessOrEqualEnd) {
                    results.add(entry.getValue());
                }
            }
        }

        // 2. Build the deeply nested RESP Array
        return new ResponseDto(buildRespArray(results), false);
    }

    private String buildRespArray(List<StreamEntry> entries) {
        StringBuilder sb = new StringBuilder();
        sb.append("*").append(entries.size()).append("\r\n");

        for (StreamEntry entry : entries) {
            sb.append("*2\r\n"); // Each stream entry is a 2-element array: [ID, [fields]]
            sb.append("$").append(entry.id().length()).append("\r\n").append(entry.id()).append("\r\n");

            Map<String, String> fields = entry.fields();
            sb.append("*").append(fields.size() * 2).append("\r\n"); // Field array

            for (Map.Entry<String, String> field : fields.entrySet()) {
                sb.append("$").append(field.getKey().length()).append("\r\n").append(field.getKey()).append("\r\n");
                sb.append("$").append(field.getValue().length()).append("\r\n").append(field.getValue()).append("\r\n");
            }
        }
        return sb.toString();
    }
}