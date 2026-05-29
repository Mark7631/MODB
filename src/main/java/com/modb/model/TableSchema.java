package com.modb.model;

import java.util.List;

public class TableSchema {
    private int tableId;
    private String name;
    private int rootPage;
    private int lastPage;
    private int availableRowId;
    private List<Column> columns;
    private int rowSize;
    private int locatePageId;
    private int locateOffset;

    public TableSchema(int tableId, String name, int rootPage, int lastPage, int availableRowId, List<Column> columns, int locatePageId, int locateOffset) {
        this.tableId = tableId;
        this.name = name;
        this.rootPage = rootPage;
        this.lastPage = lastPage;
        this.availableRowId = availableRowId;
        this.locatePageId = locatePageId;
        this.locateOffset = locateOffset;
        this.columns = columns;
        recountRowSize();
    }

    public int getTableId() {
        return tableId;
    }

    public String getName() {
        return name;
    }

    public int getRootPage() {
        return rootPage;
    }

    public int getLastPage() {
        return lastPage;
    }

    public void setLastPage(int lastPage) {
        this.lastPage = lastPage;
    }

    public int getAvailableRowId() {
        return availableRowId;
    }

    public List<Column> getColumns() {
        return columns;
    }

    public int getLocatePageId() {
        return locatePageId;
    }

    public int getLocateOffset() {
        return locateOffset;
    }

    public int getRowSize() {
        return rowSize;
    } // не учитывает байт состояния

    public void increaseAvailableRowId() {
        availableRowId++;
    }

    public void addAllColumns(List<Column> columns) {
        this.columns.addAll(columns);
        recountRowSize();
    }

    private void recountRowSize() {
        rowSize = 0;
        for (Column column : columns) {
            rowSize += column.getSize();
        }
    }

}
