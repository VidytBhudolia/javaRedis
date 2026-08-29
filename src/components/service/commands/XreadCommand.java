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

public class XreadCommand implements RedisCommand {

    @Override
    public ResponseDto execute(List<String> args, Store store) {
        // Syntax: XREAD STREAMS stream1 stream2 0-0 0-0
        int streamsIndex = args.indexOf("STREAMS");
        if (streamsIndex == -1) {
            return new ResponseDto(RespSerializer.serializeError("syntax error"), false);
        }

        int remainingArgs = args.size() - (streamsIndex + 1);
        if (remainingArgs % 2 != 0) {
            return new ResponseDto(RespSerializer.serializeError("Unbalanced XREAD list of streams and IDs"), false);
        }

        int streamCount = remainingArgs / 2;
        List<String> keys = args.subList(streamsIndex + 1, streamsIndex + 1 + streamCount);
        List<String> ids = args.subList(streamsIndex + 1 + streamCount, args.size());

        StringBuilder response = new StringBuilder();
        int streamsWithData = 0;

        for (int i = 0; i < streamCount; i++) {
            String key = keys.get(i);
            String requestedId = ids.get(i);
            Object data = store.getValue(key);

            if (data instanceof LinkedHashMap) {
                @SuppressWarnings("unchecked")
                LinkedHashMap<String, StreamEntry> stream = (LinkedHashMap<String, StreamEntry>) data;
                List<StreamEntry> validEntries = new ArrayList<>();

                synchronized (stream) {
                    for (Map.Entry<String, StreamEntry> entry : stream.entrySet()) {
                        // XREAD strictly requires entries GREATER than the requested ID
                        if (StreamEntry.compareIds(entry.getKey(), requestedId, false) > 0) {
                            validEntries.add(entry.getValue());
                        }
                    }
                }

                if (!validEntries.isEmpty()) {
                    streamsWithData++;
                    response.append(buildSingleStreamResponse(key, validEntries));
                }
            }
        }

        if (streamsWithData == 0) {
            return new ResponseDto("$-1\r\n", false);
        }

        return new ResponseDto("*" + streamsWithData + "\r\n" + response.toString(), false);
    }

    private String buildSingleStreamResponse(String key, List<StreamEntry> entries) {
        StringBuilder sb = new StringBuilder();
        sb.append("*2\r\n");
        sb.append("$").append(key.length()).append("\r\n").append(key).append("\r\n");
        sb.append("*").append(entries.size()).append("\r\n");

        for (StreamEntry entry : entries) {
            sb.append("*2\r\n");
            sb.append("$").append(entry.id().length()).append("\r\n").append(entry.id()).append("\r\n");

            Map<String, String> fields = entry.fields();
            sb.append("*").append(fields.size() * 2).append("\r\n");

            for (Map.Entry<String, String> field : fields.entrySet()) {
                sb.append("$").append(field.getKey().length()).append("\r\n").append(field.getKey()).append("\r\n");
                sb.append("$").append(field.getValue().length()).append("\r\n").append(field.getValue()).append("\r\n");
            }
        }
        return sb.toString();
    }
}