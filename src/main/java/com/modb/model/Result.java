package com.modb.model;

import java.util.List;

public class Result {
    private final boolean success;
    private final String message;
    private final List<String> colNames;
    private final List<Row> rows;

    public Result(boolean success, String message, List<String> colNames, List<Row> rows) {
        this.success = success;
        this.message = message;
        this.colNames = colNames;
        this.rows = rows;
    }

    public static Result ok(String message) {
        return new Result(true, message, null, null);
    }

    public static Result error(String message) {
        return new Result(false, message, null, null);
    }

    public static Result rows(List<String> colNames, List<Row> rows) {
        return new Result(true, null, colNames, rows);
    }

    public static Result row(List<String> colNames, Row row) {
        return new Result(true, null, colNames, List.of(row));
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public List<Row> getRows() {
        return rows;
    }

    public List<String> getColNames() {
        return colNames;
    }
}
