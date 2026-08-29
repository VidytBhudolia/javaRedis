package components.service.commands;

import java.util.List;

import components.repository.Store;
import components.service.ClientSession;
import components.service.RedisCommand;
import components.service.RespSerializer;
import components.service.ResponseDto;

public class BpopCommand implements RedisCommand {

    private final boolean isLeft;

    public BpopCommand(boolean isLeft) {
        this.isLeft = isLeft;
    }

    @Override
    public ResponseDto execute(List<String> args, Store store, ClientSession session) {
        if (args.size() < 3) {
            return new ResponseDto(RespSerializer.serializeError("wrong number of arguments"), false);
        }

        String key = args.get(1);
        long timeout;

        try {
            timeout = Long.parseLong(args.get(2));
        } catch (NumberFormatException e) {
            return new ResponseDto(RespSerializer.serializeError("timeout is not an integer or out of range"), false);
        }

        // We use a loop because of "Spurious Wakeups" (a system quirk where OS threads
        // sometimes wake up for no reason). We must verify data actually exists.
        long startTime = System.currentTimeMillis();

        while (true) {
            Object data = store.getValue(key);

            // If data exists, pop it and return immediately
            if (data != null) {
                if (!(data instanceof List<?> listStr)) {
                    return new ResponseDto(RespSerializer.serializeError("WRONGTYPE"), false);
                }

                @SuppressWarnings("unchecked")
                List<String> list = (List<String>) listStr;

                synchronized (list) {
                    if (!list.isEmpty()) {
                        String poppedValue = isLeft ? list.remove(0) : list.remove(list.size() - 1);

                        if (list.isEmpty()) store.delete(key);

                        // BLPOP returns a 2-element array: [key, popped_value]
                        String response = "*2\r\n" +
                                          "$" + key.length() + "\r\n" + key + "\r\n" +
                                          "$" + poppedValue.length() + "\r\n" + poppedValue + "\r\n";
                        return new ResponseDto(response, true);
                    }
                }
            }

            // If we reach here, the list is empty or doesn't exist.
            // Check if our timeout has expired.
            if (timeout > 0 && (System.currentTimeMillis() - startTime) >= (timeout * 1000)) {
                return new ResponseDto("$-1\r\n", false); // Timeout reached, return nil
            }

            // Put the thread to sleep until someone runs LPUSH
            store.waitForData(key, timeout);
        }
    }
}
