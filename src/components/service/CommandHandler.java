package components.service;

import components.repository.Store;
import components.service.ResponseDto;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class CommandHandler {
    // The handler needs access to the database to do its job
    private final Store store;

    public CommandHandler(Store store) {
        this.store = store;
    }

    public ResponseDto execute(List<String> args) {
        if (args == null || args.isEmpty()) {
            return new ResponseDto(RespSerializer.serializeError("empty command"), false);
        }

        String command = args.get(0).toUpperCase();

        return switch (command) {
            case "PING"   -> handlePing();
            case "ECHO"   -> handleEcho(args);
            case "SET"    -> handleSet(args);
            case "GET"    -> handleGet(args);
            case "LPUSH"  -> handlePush(args, true);
            case "RPUSH"  -> handlePush(args, false);
            case "LRANGE" -> handleLRange(args);
            case "LLEN"   -> handleLlen(args);
            case "LREM"   -> handleLrem(args);
            case "LPOP"  -> handlePop(args, true);
            case "RPOP"  -> handlePop(args, false);
            case "BLPOP"  -> handleBpop(args, true);
            case "BRPOP"  -> handleBpop(args, false);
            default       -> new ResponseDto(RespSerializer.serializeError("unknown command '" + command + "'"), false);
        };
    }

    private ResponseDto handlePing() {
        return new ResponseDto(RespSerializer.serializeSimpleString("PONG"), false);
    }

    private ResponseDto handleEcho(List<String> args) {
        if (args.size() < 2) {
            return new ResponseDto(RespSerializer.serializeError("wrong number of arguments for 'echo'"), false);
        }
        return new ResponseDto(RespSerializer.serializeBulkString(args.get(1)), false);
    }

    private ResponseDto handleSet(List<String> args) {
        if (args.size() < 3) {
            return new ResponseDto(RespSerializer.serializeError("wrong number of arguments for 'set'"), false);
        }

        String key = args.get(1);
        String value = args.get(2);
        Long expiryTimeMs = null;

        if (args.size() >= 5 && args.get(3).toUpperCase().equals("PX")) {
            try {
                long pxDuration = Long.parseLong(args.get(4));
                expiryTimeMs = System.currentTimeMillis() + pxDuration;
            } catch (NumberFormatException e) {
                return new ResponseDto(RespSerializer.serializeError("value is not an integer or out of range"), false);
            }
        }

        store.setString(key, value, expiryTimeMs);
        // SET mutates the database, so we pass true
        return new ResponseDto(RespSerializer.serializeSimpleString("OK"), true);
    }

    private ResponseDto handleGet(List<String> args) {
        if (args.size() < 2) {
            return new ResponseDto(RespSerializer.serializeError("wrong number of arguments for 'get'"), false);
        }

        String key = args.get(1);
        Object data = store.getValue(key);

        if (data == null) {
            return new ResponseDto("$-1\r\n", false); // Null Bulk String
        }

        // WRONGTYPE Protection: Ensure someone isn't trying to GET a List
        if (!(data instanceof String)) {
            return new ResponseDto(RespSerializer.serializeError("WRONGTYPE Operation against a key holding the wrong kind of value"), false);
        }

        return new ResponseDto(RespSerializer.serializeBulkString((String) data), false);
    }

    private ResponseDto handlePush(List<String> args, boolean isLeft) {
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

    private ResponseDto handleLRange(List<String> args) {
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

    private ResponseDto handleLlen(List<String> args) {
        if (args.size() < 2) {
            return new ResponseDto(RespSerializer.serializeError("wrong number of arguments for 'llen'"), false);
        }

        String key = args.get(1);
        Object data = store.getValue(key);

        if (data == null) {
            return new ResponseDto(":0\r\n", false); // Missing keys default to length 0
        }

        if (!(data instanceof List<?> list)) {
            return new ResponseDto(RespSerializer.serializeError("WRONGTYPE Operation against a key holding the wrong kind of value"), false);
        }

        // Return the integer size
        return new ResponseDto(":" + list.size() + "\r\n", false);
    }

    private ResponseDto handleLrem(List<String> args) {
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

    private ResponseDto handlePop(List<String> args, boolean isLeft) {
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

    private ResponseDto handleBpop(List<String> args, boolean isLeft) {
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