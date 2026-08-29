package components.service;

import components.repository.Store;
import java.util.List;

public interface RedisCommand {
    // Every command takes the raw arguments and the database, and returns a DTO
    ResponseDto execute(List<String> args, Store store);
}