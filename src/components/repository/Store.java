package components.repository;

import java.util.concurrent.ConcurrentHashMap;

public class Store {
    private final ConcurrentHashMap<String, Value> database;
    private final ConcurrentHashMap<String, Object> keyLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> keyVersions = new ConcurrentHashMap<>();

    public Store() {
        this.database = new ConcurrentHashMap<>();
    }

    public void putValue(String key, Object data, Long expiryTimeMs) {
        database.put(key, new Value(data, expiryTimeMs));
        incrementVersion(key);
    }

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

    public void setString(String key, String value, Long expiryTimeMs) {
        putValue(key, value, expiryTimeMs);
    }

    public String getString(String key) {
        Object data = getValue(key);
        if (data instanceof String str) {
            return str;
        }
        return null;
    }

    public void delete(String key) {
        database.remove(key);
        incrementVersion(key);
    }

    private Object getLockForKey(String key) {
        return keyLocks.computeIfAbsent(key, k -> new Object());
    }

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

    public void notifyDataArrived(String key) {
        Object lock = keyLocks.get(key);
        if (lock != null) {
            synchronized (lock) {
                // Wakes up all threads waiting on this specific lock
                lock.notifyAll();
            }
        }
    }

    public long getVersion(String key) {
        return keyVersions.getOrDefault(key, 0L);
    }

    private void incrementVersion(String key) {
        keyVersions.compute(key, (k, v) -> (v == null) ? 1L : v + 1L);
    }

}