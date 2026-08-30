package components.repository;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class RdbParser {

    public static void parseAndLoad(byte[] rdbBytes, Store store) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(rdbBytes);
             DataInputStream dis = new DataInputStream(bais)) {

            // 1. Skip the "REDIS0011" magic string header (9 bytes)
            dis.skipBytes(9);

            while (dis.available() > 0) {
                int opcode = dis.readUnsignedByte();

                // 2. Stop parsing if we hit the End of File marker
                if (opcode == 0xFF) {
                    break;
                }

                // 3. Skip the Database Selector metadata
                if (opcode == 0xFE) {
                    readLength(dis); // skip the database index number
                    continue;
                }

                // 4. Check for Expiration Timestamps
                Long expiry = null;
                if (opcode == 0xFC) { // Expiry in milliseconds
                    expiry = readLittleEndianLong(dis);
                    opcode = dis.readUnsignedByte(); // read the actual value type (should be 0x00)
                }

                // 5. If it's a standard String, parse the key and value, and save to Store
                if (opcode == 0x00) {
                    String key = readString(dis);
                    String value = readString(dis);
                    store.putValue(key, value, expiry);
                }
                // 6. If it's a Linked List, parse the key and list elements, and save to Store
                else if (opcode == 0x01) {
                    String key = readString(dis);
                    int listLength = readLength(dis);
                    java.util.List<String> list = new java.util.ArrayList<>();
                    for (int i = 0; i < listLength; i++) {
                        list.add(readString(dis));
                    }
                    store.putValue(key, list, expiry);
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to parse RDB: " + e.getMessage());
        }
    }

    private static String readString(DataInputStream dis) throws IOException {
        int length = readLength(dis);
        byte[] bytes = new byte[length];
        dis.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static int readLength(DataInputStream dis) throws IOException {
        int b = dis.readUnsignedByte();
        int type = (b & 0xC0) >> 6;

        if (type == 0) { // 00xxxxxx (fits in 1 byte)
            return b & 0x3F;
        } else if (type == 1) { // 01xxxxxx (fits in 2 bytes)
            int next = dis.readUnsignedByte();
            return ((b & 0x3F) << 8) | next;
        } else { // 10000000 (32-bit integer)
            return dis.readInt();
        }
    }

    private static long readLittleEndianLong(DataInputStream dis) throws IOException {
        long result = 0;
        for (int i = 0; i < 8; i++) {
            result |= ((long) dis.readUnsignedByte()) << (8 * i);
        }
        return result;
    }
}