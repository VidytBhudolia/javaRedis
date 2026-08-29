package components.repository;

public record Value(Object data, Long expiryTimeMs) {

    public boolean isExpired(long currentTimeMs) {
        if (expiryTimeMs == null) {
            return false;
        }
        return currentTimeMs > expiryTimeMs;
    }
}