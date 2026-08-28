package components.repository;

public record Value(String data, Long expiryTimeMs) {

    // Helper method to let the Value check its own expiration
    public boolean isExpired(long currentTimeMs) {
        if (expiryTimeMs == null) {
            return false; // No expiration set
        }
        return currentTimeMs > expiryTimeMs;
    }
}