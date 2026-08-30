package components.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClientSession {
    private boolean isMulti = false;
    private long ackedOffset = 0;
    private final List<List<String>> transactionQueue = new ArrayList<>();
    private final Map<String, Long> watchedKeys = new HashMap<>();

    public void beginTransaction() {
        isMulti = true;
        transactionQueue.clear();
    }

    public void discardTransaction() {
        isMulti = false;
        transactionQueue.clear();
        unwatch();
    }

    public boolean isMulti() {
        return isMulti;
    }

    public void enqueueCommand(List<String> commandArgs) {
        transactionQueue.add(commandArgs);
    }

    public List<List<String>> getQueueAndClear() {
        // Create a copy of the queue to return
        List<List<String>> queuedCommands = new ArrayList<>(transactionQueue);
        // Reset the session
        isMulti = false;
        transactionQueue.clear();
        unwatch();
        return queuedCommands;
    }

    public void watch(String key, long version) {
        watchedKeys.put(key, version);
    }

    public void unwatch() {
        watchedKeys.clear();
    }

    public Map<String, Long> getWatchedKeys() {
        return watchedKeys;
    }

    public void setAckedOffset(long offset) {
        this.ackedOffset = offset;
    }

    public long getAckedOffset() {
        return ackedOffset;
    }

}