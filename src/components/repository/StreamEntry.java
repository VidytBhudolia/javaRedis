package components.repository;

import java.util.LinkedHashMap;

public record StreamEntry(String id, LinkedHashMap<String, String> fields) {

    // System Concept: A custom Comparator. Returns negative if id1 < id2, 0 if equal, positive if id1 > id2.
    public static int compareIds(String id1, String id2, boolean isEndBound) {
        if (id1.equals("-")) return -1;
        if (id1.equals("+")) return 1;
        if (id2.equals("-")) return 1;
        if (id2.equals("+")) return -1;

        long ms1, seq1, ms2, seq2;

        String[] parts1 = id1.split("-");
        ms1 = Long.parseLong(parts1[0]);
        // If a partial ID like "1526985054069" is provided for the END bound, it implies the maximum possible sequence.
        seq1 = parts1.length > 1 ? Long.parseLong(parts1[1]) : (isEndBound ? Long.MAX_VALUE : 0);

        String[] parts2 = id2.split("-");
        ms2 = Long.parseLong(parts2[0]);
        seq2 = parts2.length > 1 ? Long.parseLong(parts2[1]) : 0;

        if (ms1 != ms2) {
            return Long.compare(ms1, ms2);
        }
        return Long.compare(seq1, seq2);
    }
}