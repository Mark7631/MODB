package com.modb.storageAPI;

import com.modb.model.Column;
import com.modb.model.Row;

import java.util.List;
import java.util.Map;

public interface Storage {
    void createTable(String tableName, List<Column> columns);
    void insert(String tableName, Row row);
    List<Row> selectAll(String tableName);
    void selectAllAsMd(String tableName);
    Row selectById(String tableName, int id);
    void delete(String tableName, int id);
    void update(String tableName, int id, Map<String, Object> toChange);
    List<String> getColNames(String tableName);
    Row getTables();
    void close();
}
