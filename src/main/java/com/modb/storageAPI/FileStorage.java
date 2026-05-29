package com.modb.storageAPI;

import com.modb.exception.*;
import com.modb.model.*;
import com.modb.pager.FilePager;
import com.modb.pager.Pager;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class FileStorage implements Storage {
    private final Pager pager;
    private Map<String, TableSchema> schemas = new HashMap<>();
    private final String FILE_PATH = "modb.db";
    private final static int PAGE_SIZE = 4096;
    private final static int MAGIC = 0x4D4F4442;
    private final static int VERSION = 1;
    private int availableTableId = 1;

    private void initDB() {
        // записываем заголовок
        byte[] data = new byte[PAGE_SIZE];
        ByteBuffer buffer = ByteBuffer.wrap(data);

        buffer.putInt(0, MAGIC);           // magic
        buffer.putInt(4, VERSION);         // version
        buffer.putInt(8, PAGE_SIZE);       // pageSize

        int checkSum = 0; //считаем чек сумму
        for (byte b : data) {
            checkSum += (b & 0xFF);
        }

        buffer.putInt(12, checkSum); //записываем чек сумму

        Page page = new Page(0, data);
        try {
            pager.writePage(page);
        } catch (RuntimeException e) {
            throw new RuntimeException("Failed to write page 0", e);
        }

        // инициализируем пустую страницу под table_id | name | root_page | last_page | available_row_id
        data = new byte[PAGE_SIZE];
        buffer = ByteBuffer.wrap(data);
        buffer.putInt(0, -1);
        buffer.putInt(4, 8);
        page = new Page(1, data);

        try {
            pager.writePage(page);
        } catch (RuntimeException e) {
            throw new RuntimeException("Failed to write page 1", e);
        }

        // инициализируем пустую страницу под table_id | column_name | column_type | column_size
        data = new byte[PAGE_SIZE];
        buffer = ByteBuffer.wrap(data);
        buffer.putInt(0, -1);
        buffer.putInt(4, 8);
        page = new Page(2, data);

        try {
            pager.writePage(page);
        } catch (RuntimeException e) {
            throw new RuntimeException("Failed to write page 2", e);
        }

        availableTableId = 1;
        IO.println("DB successfully initialized");
    }

    private boolean checkDB() throws NotInitedDBException {
        File file = new File(FILE_PATH);
        if (file.length() == 0) {
            return false;
        }

        boolean isInited = true;
        try {
            Page page = pager.getPage(0);
            byte[] data = page.getData();
            ByteBuffer buffer = ByteBuffer.wrap(data);

            int checkSum = 0; // считаем чек сумму
            for (int i = 0; i < 12; i++) {
                byte b = buffer.get(i);
                checkSum += (b & 0xFF);
            }

            for (int i = 16; i < PAGE_SIZE; i++) {
                byte b = buffer.get(i);
                checkSum += (b & 0xFF);
            }

            if (checkSum == 0) {
                isInited = false;
                throw new NotInitedDBException("Database is not initialized");
            }

            if (checkSum != buffer.getInt(12)) { // проверка, что файл не битый, хотя возможно выпадение при неправильном magic, version и тп
                throw new InvalidCheckSumException("Invalid check sum");
            }

            if (MAGIC != buffer.getInt(0)) {
                throw new InvalidMagicException("Invalid magic");
            }

            if (VERSION != buffer.getInt(4)) {
                throw new InvalidVersionException("Invalid version");
            }

            if (PAGE_SIZE != buffer.getInt(8)) {
                throw new InvalidPageSizeException("Invalid page size");
            }

            IO.println("DB is OK");
            return true;
        } catch (InvalidMagicException e) {
            IO.println("Invalid magic");
            return false;
        } catch (InvalidVersionException e) {
            IO.println("Invalid version");
            return false;
        } catch (InvalidPageSizeException e) {
            IO.println("Invalid page size");
            return false;
        } catch (InvalidCheckSumException e) {
            IO.println("Invalid check sum");
            return false;
        } catch (RuntimeException e) {
            if (isInited) {
                IO.println("Failed to read page 0");
                return false;
            }
        }

        throw new NotInitedDBException("Database is not initialized");
    }

    private void loadSchemas() {
        schemas.clear();
        availableTableId = 1;

        Page page;
        byte[] data;
        ByteBuffer buffer;
        int nextSchema = 1;
        int freeOffset;
        int nowOffset;

        while (nextSchema != -1) { // добавляем схемы без колонок
            page = pager.getPage(nextSchema);
            data = page.getData();
            buffer = ByteBuffer.wrap(data);

            nextSchema = buffer.getInt(0);
            freeOffset = buffer.getInt(4);
            nowOffset = 8;

            if (freeOffset < 8 || (freeOffset - 8) % 48 != 0) {
                throw new InvalidFreeOffsetException("Incorrect freeOffset value: " + freeOffset + " pageId: " + page.getPageId() + " " + ((byte) (freeOffset - 8) % 48 == 0));
            }

            if (freeOffset == 8) {
                break;
            }

            int tableId;

            while (nowOffset != freeOffset) {
                tableId = buffer.getInt(nowOffset);
                nowOffset += 4;

                byte[] bytes = new byte[32];
                buffer.position(nowOffset);
                buffer.get(bytes);

                int i = 0;
                while (i < bytes.length && bytes[i] != 0) {
                    i++;
                }

                String tableName = new String(bytes, StandardCharsets.US_ASCII);
                nowOffset += 32;
                int rootPage = buffer.getInt(nowOffset);
                nowOffset += 4;

                int lastPage = buffer.getInt(nowOffset);
                nowOffset += 4;

                int availableRowId = buffer.getInt(nowOffset);
                nowOffset += 4;

                TableSchema tableSchema = new TableSchema(tableId, tableName.trim(), rootPage, lastPage, availableRowId, new ArrayList<>(), page.getPageId(), nowOffset - 48);
                schemas.put(tableName.trim(), tableSchema);
                availableTableId++;
            }
        }

        nextSchema = 2;
        List<Column> columns = new ArrayList<>();
        int lastTableId;
        int tableId = 1;

        while (nextSchema != -1) {
            { // ищем колонки и добавляем к схеме
                if (!columns.isEmpty()) {// добовляем часть с прошлой таблицы
                    for (TableSchema schema : schemas.values()) {
                        if (schema.getTableId() == tableId) {
                            schema.addAllColumns(columns);
                            columns = new ArrayList<>();
                            break;
                        }
                    }
                }
                page = pager.getPage(nextSchema);
                data = page.getData();
                buffer = ByteBuffer.wrap(data);

                nextSchema = buffer.getInt(0);
                freeOffset = buffer.getInt(4);
                nowOffset = 8;

                if (freeOffset < 8 || (freeOffset - 8) % 39 != 0) {
                    throw new InvalidFreeOffsetException("Incorrect freeOffset value: " + freeOffset + " pageId: " + page.getPageId());
                }

                if (freeOffset == 8) {
                    break;
                }

                while (nowOffset != freeOffset) {
                    lastTableId = tableId;
                    tableId = buffer.getInt(nowOffset);
                    nowOffset += 4;

                    if (tableId != lastTableId) { // добавляем к прошлому id
                        for (TableSchema schema : schemas.values()) {
                            if (schema.getTableId() == lastTableId) {
                                schema.addAllColumns(columns);
                                columns = new ArrayList<>();
                                break;
                            }
                        }
                    }

                    byte[] bytes = new byte[32];
                    buffer.position(nowOffset);
                    buffer.get(bytes);

                    int i = 0;
                    while (i < bytes.length && bytes[i] != 0) {
                        i++;
                    }

                    String columnName = new String(bytes, 0, i);
                    nowOffset += 32;
                    byte columnType = buffer.get(nowOffset);
                    nowOffset += 1;
                    int columnSize = buffer.getShort(nowOffset) & 0xFFFF; // для адекватного преобразования short
                    nowOffset += 2;

                    columns.add(new Column(columnName.trim(), ColumnType.fromCode(columnType), columnSize));
                }
            }
        }

        if (!columns.isEmpty()) { // добавляем последнии оставшиеся
            for (TableSchema schema : schemas.values()) {
                if (schema.getTableId() == tableId) {
                    schema.addAllColumns(columns);
                    columns = new ArrayList<>();
                    break;
                }
            }
        }
    }

    public List<String> getColNames(String tableName) {
        List<Column> columns = schemas.get(tableName).getColumns();

        return columns.stream()
                .map(col -> {
                    if (col.getType() == ColumnType.CHAR) {
                        return col.getName()
                                + " : "
                                + col.getType()
                                + "("
                                + col.getSize()
                                + ")";
                    }

                    return col.getName()
                            + " : "
                            + col.getType();
                })
                .toList();
    }

    public Row getTables() throws RuntimeException {
        return new Row(schemas.values().stream()
                .map(TableSchema::getName)
                .toArray());
    }

    private void updateLastPageOnDisk(String tableName) {
        TableSchema schema = schemas.get(tableName);
        Page page = pager.getPage(schema.getLocatePageId());
        byte[] data = page.getData();
        ByteBuffer buffer = ByteBuffer.wrap(data);
        int offset = schema.getLocateOffset();
        buffer.putInt(offset + 40, schema.getLastPage());
        pager.writePage(page);
    }

    private void updateAvailableRowIdOnDisk(String tableName) {
        TableSchema schema = schemas.get(tableName);
        Page page = pager.getPage(schema.getLocatePageId());
        byte[] data = page.getData();
        ByteBuffer buffer = ByteBuffer.wrap(data);
        int offset = schema.getLocateOffset();
        buffer.putInt(offset + 44, schema.getAvailableRowId());
        pager.writePage(page);
    }

    public FileStorage() throws InitializeDBException, IOException {
        pager = new FilePager(FILE_PATH, PAGE_SIZE);
        schemas = new HashMap<>();
        try {
            if (!checkDB()) {
                throw new NotInitedDBException("Database is not initialized and couldn't");
            }
            loadSchemas();
        } catch (NotInitedDBException e) {
            initDB();
        }
    }

    @Override
    public void createTable(String tableName, List<Column> columns) throws TableAlreadyExistsException, InvalidFreeOffsetException {
        for (TableSchema schema : schemas.values()) { // проверяем что таблицы с таким именем нету
            if (schema.getName().equalsIgnoreCase(tableName)) {
                throw new TableAlreadyExistsException("Table with name " + tableName + " already exists");
            }
        }

        columns.addFirst(new Column("id", ColumnType.INT, 4)); // добовляем колонк id


        Page page = new Page(0, new byte[]{});
        byte[] data = new byte[PAGE_SIZE];
        ByteBuffer buffer = ByteBuffer.wrap(data);
        int nextSchema = 1;
        int freeOffset = 0;

        while (nextSchema != -1) { // ищем последнюю таблицу куда добавть
            page = pager.getPage(nextSchema);
            data = page.getData();
            buffer = ByteBuffer.wrap(data);

            nextSchema = buffer.getInt(0);
            freeOffset = buffer.getInt(4);

            if (freeOffset < 8 || (freeOffset - 8) % 48 != 0) {
                throw new InvalidFreeOffsetException("Incorrect freeOffset value: " + freeOffset + " pageId: " + page.getPageId());
            }
        }

        // добавляем table_id | name | root_page | last_page
        if ((PAGE_SIZE - freeOffset) < 48) { // если на найденной странице не осталось места
            int newPageId = pager.allocatePage();
            buffer.putInt(0, newPageId);
            page = pager.getPage(newPageId);
            data = page.getData();
            buffer = ByteBuffer.wrap(data);
            buffer.putInt(0, -1);

            freeOffset = 8;
            buffer.putInt(freeOffset, availableTableId);
            freeOffset += 4;

            byte[] bytes = tableName.getBytes(StandardCharsets.US_ASCII);
            byte[] fixed = new byte[32];

            System.arraycopy(bytes, 0, fixed, 0, bytes.length);
            buffer.position(freeOffset);
            buffer.put(fixed);
            freeOffset += 32;

            int rootPage = pager.allocatePage();
            buffer.putInt(freeOffset, rootPage);
            freeOffset += 4;

            buffer.putInt(freeOffset, rootPage); // lastPage == rootPage
            freeOffset += 4;

            buffer.putInt(freeOffset, 1);
            freeOffset += 4;

            buffer.putInt(4, freeOffset);
            pager.writePage(page); // TODO IOException

            page = pager.getPage(rootPage); // инициализируем rootPage
            data = page.getData();
            buffer = ByteBuffer.wrap(data);
            buffer.putInt(0, -1);
            buffer.putInt(4, 8);
            pager.writePage(page); // TODO IOException

            TableSchema schema = new TableSchema(availableTableId, tableName.trim(), rootPage, rootPage, 1, new ArrayList<>(), newPageId, freeOffset - 48);
            schemas.put(tableName.trim(), schema);
            availableTableId++;
        } else {
            int locatePageId = page.getPageId();
            buffer.putInt(freeOffset, availableTableId);
            freeOffset += 4;

            byte[] bytes = tableName.getBytes(StandardCharsets.US_ASCII);
            byte[] fixed = new byte[32];

            System.arraycopy(bytes, 0, fixed, 0, bytes.length);
            buffer.position(freeOffset);
            buffer.put(fixed);
            freeOffset += 32;

            int rootPage = pager.allocatePage();
            buffer.putInt(freeOffset, rootPage);
            freeOffset += 4;

            buffer.putInt(freeOffset, rootPage); // lastPage == rootPage
            freeOffset += 4;

            buffer.putInt(freeOffset, 1);
            freeOffset += 4;

            buffer.putInt(4, freeOffset);
            pager.writePage(page); // TODO IOException

            page = pager.getPage(rootPage);
            data = page.getData();
            buffer = ByteBuffer.wrap(data);
            buffer.putInt(0, -1);
            buffer.putInt(4, 8);
            pager.writePage(page); // TODO IOException

            TableSchema schema = new TableSchema(availableTableId, tableName.trim(), rootPage, rootPage, 1, new ArrayList<>(), locatePageId, freeOffset - 48);
            schemas.put(tableName.trim(), schema);
            availableTableId++;
        }

        nextSchema = 2;
        while (nextSchema != -1) { // ищем последнюю таблицу куда добавть
            page = pager.getPage(nextSchema);
            data = page.getData();
            buffer = ByteBuffer.wrap(data);

            nextSchema = buffer.getInt(0);
            freeOffset = buffer.getInt(4);

            if (freeOffset < 8 || (freeOffset - 8) % 39 != 0) {
                throw new InvalidFreeOffsetException("Incorrect freeOffset value: " + freeOffset + " pageId: " + page.getPageId());
            }
        }

        // добавляем table_id | column_name | column_type | column_size
        int needToWrite = columns.size();
        int couldBeWriteOnPage = ( PAGE_SIZE - freeOffset) / 39;
        int i = 0;
        Column col;

        while (couldBeWriteOnPage > 0 && needToWrite > 0) {
            col = columns.get(i);
            buffer.putInt(freeOffset, availableTableId - 1);
            freeOffset += 4;

            byte[] bytes = col.getName().getBytes(StandardCharsets.US_ASCII);
            byte[] fixed = new byte[32];

            System.arraycopy(bytes, 0, fixed, 0, bytes.length);
            buffer.position(freeOffset);
            buffer.put(fixed);
            freeOffset += 32;

            buffer.put(freeOffset, (byte) col.getType().getCode());
            freeOffset += 1;

            buffer.putShort(freeOffset, (short) col.getSize());
            freeOffset += 2;

            i++;
            needToWrite--;
            couldBeWriteOnPage--;
        }
        buffer.putInt(4, freeOffset);

        if (needToWrite > 0) {
            int newPageId = pager.allocatePage();
            buffer.putInt(0, newPageId);
            pager.writePage(page); // TODO IOException
            page = pager.getPage(newPageId);
            data = page.getData();
            buffer = ByteBuffer.wrap(data);
            buffer.putInt(0, -1);
            freeOffset = 8;

            while (i < needToWrite) {
                col = columns.get(i);
                buffer.putInt(freeOffset, availableTableId - 1);
                freeOffset += 4;

                byte[] bytes = col.getName().getBytes(StandardCharsets.US_ASCII);
                byte[] fixed = new byte[32];

                System.arraycopy(bytes, 0, fixed, 0, bytes.length);
                buffer.position(freeOffset);
                buffer.put(fixed);
                freeOffset += 32;

                buffer.put(freeOffset, (byte) col.getType().getCode());
                freeOffset += 1;

                buffer.putShort(freeOffset, (short) col.getSize());
                freeOffset += 2;

                i++;
                couldBeWriteOnPage--;
                needToWrite--;
            }

            buffer.putInt(4, freeOffset);
        }
        pager.writePage(page); // TODO IOException
        schemas.get(tableName).addAllColumns(columns);
    }

    @Override
    public void insert(String tableName, Row row) throws TableDoesntExistsException {
        TableSchema schema = schemas.get(tableName);
        if (schema == null) {
            throw new TableDoesntExistsException("Table: " + tableName + " doesn't  exist");
        }

        List<Column> columns = schema.getColumns();
        Page page = pager.getPage(schema.getLastPage());
        byte[] data = page.getData();
        ByteBuffer buffer = ByteBuffer.wrap(data);
        int freeOffset = buffer.getInt(4);

        if (PAGE_SIZE - freeOffset < schema.getRowSize() + 1) {
            int newPageId = pager.allocatePage();
            buffer.putInt(0, newPageId);
            pager.writePage(page); //TODO IOException
            schema.setLastPage(newPageId);
            updateLastPageOnDisk(tableName);
            page = pager.getPage(newPageId);
            data = page.getData();
            buffer = ByteBuffer.wrap(data);
            freeOffset = 8;
            buffer.putInt(0, -1);
        }

        buffer.put(freeOffset, (byte) 1);
        freeOffset += 1;

        buffer.putInt(freeOffset, schema.getAvailableRowId());
        freeOffset += 4;
        schema.increaseAvailableRowId();
        updateAvailableRowIdOnDisk(tableName);

        for (int i = 1; i < columns.size(); i++) {
            switch (columns.get(i).getType()) {
                case INT:
                    buffer.putInt(freeOffset, (Integer) row.get(i - 1));
                    break;

                case FLOAT:
                    buffer.putFloat(freeOffset, (Float) row.get(i - 1));
                    break;

                case BOOL:
                    buffer.put(freeOffset, (byte) ((Boolean) row.get(i - 1) ? 1 : 0));
                    break;

                case CHAR:
                    byte[] bytes = ((String) row.get(i - 1))
                            .getBytes(StandardCharsets.US_ASCII);

                    byte[] fixed = new byte[columns.get(i).getSize()];
                    System.arraycopy(bytes, 0, fixed, 0, Math.min(bytes.length, columns.get(i).getSize()));
                    buffer.position(freeOffset);
                    buffer.put(fixed);
                    break;
            }
            freeOffset += columns.get(i).getSize();
        }

        buffer.putInt(4, freeOffset);
        pager.writePage(page); //TODO IOException
    }

    @Override
    public List<Row> selectAll(String tableName) throws TableDoesntExistsException {
        List<Row> all = new ArrayList<>();
        TableSchema schema = schemas.get(tableName);

        if (schema == null) {
            throw new TableDoesntExistsException("Table: " + tableName + " doesn't  exist");
        }

        List<Column> columns = schema.getColumns();
        int nowPageId = schema.getRootPage();
        Page page;
        byte[] data;
        ByteBuffer buffer;
        int freeOffset;
        while (nowPageId != -1) {
            page = pager.getPage(nowPageId);
            data = page.getData();
            buffer = ByteBuffer.wrap(data);
            freeOffset = buffer.getInt(4);
            Row row;

            int i = 8;
            while (i < freeOffset) {
                Object[] objects = new Object[columns.size()];
                byte state = buffer.get(i);
                i += 1;

                if (state == 0) {
                    i += schema.getRowSize();
                    continue;
                }

                for (int j = 0; j < columns.size(); j++) {
                    switch (columns.get(j).getType()) {
                        case INT:
                            objects[j] = buffer.getInt(i);
                            break;

                        case FLOAT:
                            objects[j] = buffer.getFloat(i);
                            break;

                        case BOOL:
                            objects[j] = buffer.get(i) == 1;
                            break;

                        case CHAR:
                            byte[] bytes = new byte[columns.get(j).getSize()];
                            buffer.position(i);
                            buffer.get(bytes);

                            int len = 0;
                            while (len < bytes.length && bytes[len] != 0) {
                                len++;
                            }

                            objects[j] = new String(bytes, 0, len, StandardCharsets.US_ASCII);
                            break;
                    }
                    i += columns.get(j).getSize();
                }
                row = new Row(objects);
                all.add(row);
            }
            nowPageId = buffer.getInt(0);
        }
        return all;
    }

    @Override
    public void selectAllAsMd(String tableName) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(tableName + ".md"))) {

            TableSchema schema = schemas.get(tableName);

            if (schema == null) {
                throw new TableDoesntExistsException("Table: " + tableName + " doesn't exist");
            }

            List<Column> columns = schema.getColumns();

            // HEADER
            writer.write("| ");
            for (Column col : columns) {
                writer.write(col.getName() + " : " + col.getType());
                if (col.getType() == ColumnType.CHAR) {
                    writer.write("(" + col.getSize() + ")");
                }
                writer.write(" | ");
            }
            writer.newLine();

            writer.write("| ");
            for (int j = 0; j < columns.size(); j++) {
                writer.write("--- | ");
            }
            writer.newLine();

            int nowPageId = schema.getRootPage();

            while (nowPageId != -1) {
                Page page = pager.getPage(nowPageId);
                ByteBuffer buffer = ByteBuffer.wrap(page.getData());

                int freeOffset = buffer.getInt(4);
                int i = 8;

                while (i < freeOffset) {
                    byte state = buffer.get(i);
                    i += 1;

                    if (state == 0) {
                        i += schema.getRowSize();
                        continue;
                    }

                    writer.write("| ");

                    for (int j = 0; j < columns.size(); j++) {
                        Object value = null;

                        switch (columns.get(j).getType()) {
                            case INT:
                                value = buffer.getInt(i);
                                break;

                            case FLOAT:
                                value = buffer.getFloat(i);
                                break;

                            case BOOL:
                                value = buffer.get(i) == 1;
                                break;

                            case CHAR:
                                byte[] bytes = new byte[columns.get(j).getSize()];
                                buffer.position(i);
                                buffer.get(bytes);

                                int len = 0;
                                while (len < bytes.length && bytes[len] != 0) {
                                    len++;
                                }

                                value = new String(bytes, 0, len, StandardCharsets.US_ASCII);
                                break;
                        }

                        writer.write(value + " | ");
                        i += columns.get(j).getSize();
                    }

                    writer.newLine();
                }

                nowPageId = buffer.getInt(0);
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to write markdown file", e);
        }
    }

    @Override
    public Row selectById(String tableName, int id) {
        if (id < 1) {
            throw new InvalidItemIDException("Id less than 1");
        }
        TableSchema schema = schemas.get(tableName);

        if (schema == null) {
            throw new TableDoesntExistsException("Table doesn't exist: " + tableName);
        }

        int rowsOnPage = (PAGE_SIZE - 8) / (schema.getRowSize() + 1);
        int pageNum = id / rowsOnPage;
        if (id % rowsOnPage != 0) pageNum++;

        int nowPageId = schema.getRootPage();
        Page page;
        byte[] data;
        ByteBuffer buffer = null;

        for (int i = 0; i < pageNum; i++) {
            page = pager.getPage(nowPageId);
            data = page.getData();
            buffer = ByteBuffer.wrap(data);
            nowPageId = buffer.getInt(0);
            if (nowPageId == -1 && i != pageNum - 1) {
                throw new InvalidItemIDException("Invalid id " + id + " not exist so many pages");
            }
        }

        if (buffer != null) {
            int freeOffset = buffer.getInt(4);
            int localId = id - rowsOnPage * (pageNum - 1) - 1;
            if ((freeOffset - 8) / (schema.getRowSize() + 1) <= localId) {
                throw new InvalidItemIDException("Invalid id " + id + " not exist so many rows in page");
            }

            int nowOffset = localId * (schema.getRowSize() + 1) + 8;
            List<Column> columns = schema.getColumns();
            Object[] objects = new Object[columns.size()];
            byte state = buffer.get(nowOffset);
            nowOffset++;

            if (state == 0) {
                throw new DeadItemException("Item by id: " + id + " is deleated");
            }

            for (int j = 0; j < columns.size(); j++) {
                switch (columns.get(j).getType()) {
                    case INT:
                        objects[j] = buffer.getInt(nowOffset);
                        break;

                    case FLOAT:
                        objects[j] = buffer.getFloat(nowOffset);
                        break;

                    case BOOL:
                        objects[j] = buffer.get(nowOffset) == 1;
                        break;

                    case CHAR:
                        byte[] bytes = new byte[columns.get(j).getSize()];
                        buffer.position(nowOffset);
                        buffer.get(bytes);

                        int len = 0;
                        while (len < bytes.length && bytes[len] != 0) {
                            len++;
                        }

                        objects[j] = new String(bytes, 0, len, StandardCharsets.US_ASCII);
                        break;
                }
                nowOffset += columns.get(j).getSize();
            }
            return new Row(objects);
        }
        return null;
    }

    @Override
    public void delete(String tableName, int id) {
        if (id < 1) {
            throw new InvalidItemIDException("Id less than 1");
        }
        TableSchema schema = schemas.get(tableName);

        if (schema == null) {
            throw new TableDoesntExistsException("Table doesn't exist: " + tableName);
        }

        int rowsOnPage = (PAGE_SIZE - 8) / (schema.getRowSize() + 1);
        int pageNum = id / rowsOnPage;
        if (id % rowsOnPage != 0) pageNum++;

        int nowPageId = schema.getRootPage();
        Page page = null;
        byte[] data;
        ByteBuffer buffer = null;

        for (int i = 0; i < pageNum; i++) {
            page = pager.getPage(nowPageId);
            data = page.getData();
            buffer = ByteBuffer.wrap(data);
            nowPageId = buffer.getInt(0);
            if (nowPageId == -1 && i != pageNum - 1) {
                throw new InvalidItemIDException("Invalid id " + id + " not exist so many pages");
            }
        }

        if (buffer != null) {
            int freeOffset = buffer.getInt(4);
            int localId = id - rowsOnPage * (pageNum - 1) - 1;
            if ((freeOffset - 8) / (schema.getRowSize() + 1) <= localId) {
                throw new InvalidItemIDException("Invalid id " + id + " not exist so many rows in page");
            }

            int nowOffset = localId * (schema.getRowSize() + 1) + 8;
            byte state = buffer.get(nowOffset);

            if (state == 0) {
                throw new DeadItemException("Item already deleted");
            }

            buffer.put(nowOffset, (byte) 0);
            pager.writePage(page);
        }
    }

    @Override
    public void update(String tableName, int id, Map<String, Object> toChange) {
        if (id < 1) {
            throw new InvalidItemIDException("Id less than 1");
        }
        TableSchema schema = schemas.get(tableName);

        if (schema == null) {
            throw new TableDoesntExistsException("Table doesn't exist: " + tableName);
        }

        if (toChange.containsKey("id")) {
            throw new RuntimeException("Impossible update id");
        }

        List<Column> columns = schema.getColumns();
        List<String> colNames = columns.stream()
                .map(Column::getName)
                .toList();

        for (String colS : toChange.keySet()) {
            if (!colNames.contains(colS)) {
                throw new NotExistColumnUpdateException("Column " + colS + " doesn't exist");
            }
        }

        int rowsOnPage = (PAGE_SIZE - 8) / (schema.getRowSize() + 1);
        int pageNum = id / rowsOnPage;
        if (id % rowsOnPage != 0) pageNum++;

        int nowPageId = schema.getRootPage();
        Page page = null;
        byte[] data;
        ByteBuffer buffer = null;

        for (int i = 0; i < pageNum; i++) {
            page = pager.getPage(nowPageId);
            data = page.getData();
            buffer = ByteBuffer.wrap(data);
            nowPageId = buffer.getInt(0);
            if (nowPageId == -1 && i != pageNum - 1) {
                throw new InvalidItemIDException("Invalid id " + id + " not exist so many pages");
            }
        }

        if (buffer != null) {
            int freeOffset = buffer.getInt(4);
            int localId = id - rowsOnPage * (pageNum - 1) - 1;
            if ((freeOffset - 8) / (schema.getRowSize() + 1) <= localId) {
                throw new InvalidItemIDException("Invalid id " + id + " not exist so many rows in page");
            }

            int nowOffset = localId * (schema.getRowSize() + 1) + 8;
            byte state = buffer.get(nowOffset);

            if (state == 0) {
                throw new DeadItemException("Item by id: " + id + " is deleted");
            }

            nowOffset += 5; // так как state и id переписывать не надо

            for (int i = 1; i < columns.size(); i++) {
                if (toChange.containsKey(columns.get(i).getName())) {
                    switch (columns.get(i).getType()) {
                        case INT:
                            buffer.putInt(nowOffset, (Integer) toChange.get(columns.get(i).getName()));
                            break;

                        case FLOAT:
                            buffer.putFloat(nowOffset, (Float) toChange.get(columns.get(i).getName()));
                            break;

                        case BOOL:
                            buffer.put(nowOffset, (byte) ((Boolean) toChange.get(columns.get(i).getName()) ? 1 : 0));
                            break;

                        case CHAR:
                            byte[] bytes = ((String) toChange.get(columns.get(i).getName()))
                                    .getBytes(StandardCharsets.US_ASCII);

                            byte[] fixed = new byte[columns.get(i).getSize()];
                            System.arraycopy(bytes, 0, fixed, 0, Math.min(bytes.length, columns.get(i).getSize()));
                            buffer.position(nowOffset);
                            buffer.put(fixed);
                            break;
                    }
                }

                nowOffset += columns.get(i).getSize();
            }
            pager.writePage(page);
        }
    }

    @Override
    public void close() {
        pager.close();
    }
}
