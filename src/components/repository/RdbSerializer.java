package components.repository;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class RdbSerializer {

    public static byte[] serialize(Store store) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(baos)) {

            // 1. Header: Magic String + Version
            dos.write("REDIS0011".getBytes(StandardCharsets.UTF_8));

            // 2. Database Selector (Select DB 0)
            dos.writeByte(0xFE);
            encodeLength(dos, 0);

            // 3. Iterate through the database and write Key-Value pairs
            // Note: For simplicity in this phase, we only serialize basic String values.
            for (Map.Entry<String, Value> entry : store.getDatabase().entrySet()) {
                String key = entry.getKey();
                Value record = entry.getValue(); // Get your custom Value record
                Object data = record.data();

                if (data instanceof String) {
                    Long expiry = record.expiryTimeMs(); // Extract the expiry

                    if (expiry != null) {
                        // If it has an expiry, write the 0xFC flag and the 8-byte timestamp
                        dos.writeByte(0xFC);
                        writeLittleEndianLong(dos, expiry);
                    }

                    // Write Value Type (0x00 for String)
                    dos.writeByte(0x00);

                    // Write Key
                    byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
                    encodeLength(dos, keyBytes.length);
                    dos.write(keyBytes);

                    // Write Value
                    byte[] valBytes = ((String) data).getBytes(StandardCharsets.UTF_8);
                    encodeLength(dos, valBytes.length);
                    dos.write(valBytes);
                }
                else if (data instanceof java.util.List<?> list) {
                    Long expiry = record.expiryTimeMs();
                    if (expiry != null) {
                        dos.writeByte(0xFC);
                        writeLittleEndianLong(dos, expiry);
                    }

                    // Write Value Type (0x01 for Linked List)
                    dos.writeByte(0x01);

                    // Write Key
                    byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
                    encodeLength(dos, keyBytes.length);
                    dos.write(keyBytes);

                    // Write List Length
                    encodeLength(dos, list.size());

                    // Write each element in the list
                    for (Object item : list) {
                        byte[] itemBytes = String.valueOf(item).getBytes(StandardCharsets.UTF_8);
                        encodeLength(dos, itemBytes.length);
                        dos.write(itemBytes);
                    }
                }
            }

            // 4. End of File Marker
            dos.writeByte(0xFF);

            // 5. 8-Byte Checksum (We send 8 bytes of zeros as a dummy checksum)
            dos.write(new byte[8]);

            return baos.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize RDB", e);
        }
    }

    // Redis Length Encoding: If length < 64, it fits in 1 byte.
    private static void encodeLength(DataOutputStream dos, int length) throws IOException {
        if (length < 64) {
            dos.writeByte(length); // 00xxxxxx
        } else if (length < 16384) {
            dos.writeByte((length >> 8) | 0x40); // 01xxxxxx
            dos.writeByte(length & 0xFF);
        } else {
            dos.writeByte(0x80); // 10000000
            dos.writeInt(length);
        }
    }

    // Java DataOutputStream writes in Big-Endian. Redis RDB expects timestamps in Little-Endian.
    private static void writeLittleEndianLong(DataOutputStream dos, long value) throws IOException {
        dos.writeByte((int) (value & 0xFF));
        dos.writeByte((int) ((value >> 8) & 0xFF));
        dos.writeByte((int) ((value >> 16) & 0xFF));
        dos.writeByte((int) ((value >> 24) & 0xFF));
        dos.writeByte((int) ((value >> 32) & 0xFF));
        dos.writeByte((int) ((value >> 40) & 0xFF));
        dos.writeByte((int) ((value >> 48) & 0xFF));
        dos.writeByte((int) ((value >> 56) & 0xFF));
    }
}