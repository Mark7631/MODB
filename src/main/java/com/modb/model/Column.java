package com.modb.model;

public class Column {
    private final String name;
    private final ColumnType type;
    private final int size;

    public Column(String name, ColumnType type, int size) {
        this.name = name;
        this.type = type;
        this.size = size;
    }

    public String getName() {
        return name;
    }

    public ColumnType getType() {
        return type;
    }

    public int getSize() {
        return size;
    }
}
