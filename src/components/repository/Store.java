package components.repository;

import java.util.concurrent.ConcurrentHashMap;

public class Store {
    private final ConcurrentHashMap<String, Value> database;
    // A map to hold "locks" for specific keys so threads can wait on them
    private final ConcurrentHashMap<String, Object> keyLocks = new ConcurrentHashMap<>();

    public Store() {
        this.database = new ConcurrentHashMap<>();
    }

    // Generic put for any data type (Lists, Streams, etc.)
    public void putValue(String key, Object data, Long expiryTimeMs) {
        database.put(key, new Value(data, expiryTimeMs));
    }

    // Generic get that handles expiration
    public Object getValue(String key) {
        Value record = database.get(key);
        if (record == null) {
            return null;
        }
        if (record.isExpired(System.currentTimeMillis())) {
            database.remove(key);
            return null;
        }
        return record.data();
    }

    // Helper specifically for String commands (SET)
    public void setString(String key, String value, Long expiryTimeMs) {
        putValue(key, value, expiryTimeMs);
    }

    // Helper specifically for String commands (GET)
    public String getString(String key) {
        Object data = getValue(key);
        if (data instanceof String str) {
            return str;
        }
        return null; // Handle wrong type access gracefully
    }
    public void delete(String key) {
        database.remove(key);
    }

    // SYSTEM CONCEPT: Getting a specific lock for a specific key
    private Object getLockForKey(String key) {
        return keyLocks.computeIfAbsent(key, k -> new Object());
    }

    // Called by BLPOP to put the thread to sleep
    public void waitForData(String key, long timeoutSeconds) {
        Object lock = getLockForKey(key);
        synchronized (lock) {
            try {
                // If timeout is 0, wait forever. Otherwise, convert to milliseconds.
                long timeoutMs = timeoutSeconds == 0 ? 0 : timeoutSeconds * 1000;

                // .wait() physically suspends the Virtual Thread here. It consumes 0 CPU.
                lock.wait(timeoutMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // Called by LPUSH/RPUSH to wake up sleeping threads
    public void notifyDataArrived(String key) {
        Object lock = keyLocks.get(key);
        if (lock != null) {
            synchronized (lock) {
                // Wakes up all threads waiting on this specific lock
                lock.notifyAll();
            }
        }
    }
}