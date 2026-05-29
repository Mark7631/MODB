package com.modb.executor;

import com.modb.model.Cmd;
import com.modb.model.Result;
import com.modb.model.cmdArgs.*;
import com.modb.storageAPI.Storage;

import java.util.List;

public class ExecutorImpl implements Executor {
    private final Storage storage;

    public ExecutorImpl(Storage storage) {
        this.storage = storage;
    }

    @Override
    public Result execute(Cmd cmd) {
        switch (cmd.type()) {
            case CREATE_TABLE -> {
                return executeCreateTable(cmd);
            }
            case INSERT -> {
                return executeInsert(cmd);
            }
            case SELECT -> {
                return executeSelect(cmd);
            }
            case DELETE -> {
                return executeDelete(cmd);
            }
            case UPDATE -> {
                return executeUpdate(cmd);
            }
            case SHOW_TABLES -> {
                return executeShowTables(cmd);
            }
            default -> throw new IllegalArgumentException("Unknown command type: " + cmd.type());
        }
    }

    private Result executeCreateTable(Cmd cmd) {
        CreateTableArgs args = (CreateTableArgs) cmd.args();
        storage.createTable(cmd.tableName(), args.columns());
        return Result.ok("Table created successfully");
    }

    private Result executeInsert(Cmd cmd) {
        InsertArgs args = (InsertArgs) cmd.args();
        storage.insert(cmd.tableName(), args.row());
        return Result.ok("Row inserted successfully");
    }

    private Result executeSelect(Cmd cmd) {
        SelectArgs args = (SelectArgs) cmd.args();
        if (args.hasWhere()) {
            return Result.row(storage.getColNames(cmd.tableName()), storage.selectById(cmd.tableName(), args.id()));
        } else if (!args.asMD()) {
            return Result.rows(storage.getColNames(cmd.tableName()), storage.selectAll(cmd.tableName()));
        } else {
            storage.selectAllAsMd(cmd.tableName());
            return Result.ok("Md created successfully");
        }
    }

    private Result executeDelete(Cmd cmd) {
        DeleteArgs args = (DeleteArgs) cmd.args();
        storage.delete(cmd.tableName(), args.id());
        return Result.ok("Row deleted successfully");
    }

    private Result executeUpdate(Cmd cmd) {
        UpdateArgs args = (UpdateArgs) cmd.args();
        storage.update(cmd.tableName(), args.id(), args.toChange());
        return Result.ok("Row updated successfully");
    }

    private Result executeShowTables(Cmd cmd) {
        return Result.row(List.of("Tables: "), storage.getTables());
    }
}
