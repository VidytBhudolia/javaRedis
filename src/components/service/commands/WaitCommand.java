package components.service.commands;

import components.infra.Client;
import components.infra.ConnectionPool;
import components.repository.Store;
import components.server.ServerState;
import components.service.ClientSession;
import components.service.RedisCommand;
import components.service.ResponseDto;

import java.util.List;

public class WaitCommand implements RedisCommand {
    private final ServerState state;
    private final ConnectionPool pool;

    public WaitCommand(ServerState state, ConnectionPool pool) {
        this.state = state;
        this.pool = pool;
    }

    @Override
    public ResponseDto execute(List<String> args, Store store, ClientSession session) {
        int expectedReplicas = Integer.parseInt(args.get(1));
        long timeoutMs = Long.parseLong(args.get(2));

        long targetOffset = state.getMasterReplOffset();

        // If no writes have ever happened, just return the number of connected replicas
        if (targetOffset == 0) {
            return new ResponseDto(":" + pool.getReplicas().size() + "\r\n", false);
        }

        // Prompt all replicas to report their current offsets
        pool.broadcastGetAck();

        long startTime = System.currentTimeMillis();
        int acks = 0;

        // Block the thread and poll the replicas' sessions
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            acks = 0;
            for (Client replica : pool.getReplicas()) {
                if (replica.getSession().getAckedOffset() >= targetOffset) {
                    acks++;
                }
            }

            if (acks >= expectedReplicas) {
                break;
            }

            try {
                Thread.sleep(50); // Pause briefly before checking again
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // Return the integer count of replicas that successfully caught up
        return new ResponseDto(":" + acks + "\r\n", false);
    }
}