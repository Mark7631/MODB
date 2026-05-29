package com.modb.model;

import com.modb.model.cmdArgs.Args;

public record Cmd(
        CmdType type,
        String tableName,
        Args args
) {
    public Cmd(CmdType type, String tableName, Args args) {
        this.type = type;
        if (tableName == null) {
            this.tableName = null;
        } else {
            this.tableName = tableName.toLowerCase();
        }
        this.args = args;
    }
}
