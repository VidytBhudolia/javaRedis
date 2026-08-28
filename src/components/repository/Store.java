package components.repository;

import java.util.concurrent.ConcurrentHashMap;

public class Store {
    // Private so the network layer cannot bypass our rules
    private final ConcurrentHashMap<String, Value> database;

    public Store() {
        this.database = new ConcurrentHashMap<>();
    }

    public void set(String key, String value, Long expiryTimeMs) {
        Value record = new Value(value, expiryTimeMs);
        database.put(key, record);
    }

    public String get(String key) {
        Value record = database.get(key);

        if (record == null) {
            return null;
        }

        // Lazy Eviction applied at the Database Layer
        if (record.isExpired(System.currentTimeMillis())) {
            database.remove(key);
            return null;
        }

        return record.data();
    }

    public boolean remove(String key) {
        // Returns true if the key existed and was removed
        return database.remove(key) != null;
    }

    public void clear() {
        database.clear();
    }
}
