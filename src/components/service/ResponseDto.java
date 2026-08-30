package components.service;

public record ResponseDto(String responseString, byte[] rawBytes, boolean mutatedDatabase) {
    // Overloaded constructor for standard string commands
    public ResponseDto(String responseString, boolean mutatedDatabase) {
        this(responseString, null, mutatedDatabase);
    }
}