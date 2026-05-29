package com.modb.model;

import com.modb.exception.InvalidColumnTypeCodeException;

public enum ColumnType {
    INT(1),
    FLOAT(2),
    BOOL(3),
    CHAR(4);

    private final int code;

    ColumnType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static ColumnType fromCode(int code) {
        for (ColumnType t : values()) {
            if (t.code == code) return t;
        }
        throw new InvalidColumnTypeCodeException("Invalid column type code: " + code);
    }

    public static ColumnType fromName(String name) throws IllegalArgumentException {
        for (ColumnType t : values()) {
            if (t.name().equalsIgnoreCase(name)) return t;
        }
        throw new IllegalArgumentException("Invalid column type name: " + name);
    }
}
