package components.service.commands;

import components.repository.Store;
import components.server.ServerState;
import components.service.ClientSession;
import components.service.RedisCommand;
import components.service.RespSerializer;
import components.service.ResponseDto;

import java.util.List;

public class PsyncCommand implements RedisCommand {
    private final ServerState state;

    // This is the standard, hardcoded Hex String for an empty Redis RDB file
    private static final String EMPTY_RDB_HEX = "524544495330303131fa0972656469732d76657205372e322e30fa0a72656469732d62697473c040fa056374696d65c26d08bc65fa08757365642d6d656dc2b0c41000fa08616f662d62617365c000fff06e3bfec0ff5aa2";

    public PsyncCommand(ServerState state) {
        this.state = state;
    }

    @Override
    public ResponseDto execute(List<String> args, Store store, ClientSession session) {
        String resyncStr = RespSerializer.serializeSimpleString("FULLRESYNC " + state.getMasterReplId() + " " + state.getMasterReplOffset());

        byte[] rdbBytes = hexStringToByteArray(EMPTY_RDB_HEX);
        String rdbHeader = "$" + rdbBytes.length + "\r\n";

        // We combine the +FULLRESYNC string and the RDB length header, then attach the raw bytes
        return new ResponseDto(resyncStr + rdbHeader, rdbBytes, false);
    }

    // Helper to convert hex to raw bytes
    private byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                 + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }
}