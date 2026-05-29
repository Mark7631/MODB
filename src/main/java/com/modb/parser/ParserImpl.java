package com.modb.parser;

import com.modb.model.*;
import com.modb.model.cmdArgs.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ParserImpl implements Parser {
    @Override
    public Cmd parse(String input) {
        String in = input.toLowerCase();

        if (in.startsWith("create table")) {
            return parseCreateTable(input.substring(12).trim());
        }

        if (in.startsWith("insert")) {
            return parseInsert(input.substring(6).trim());
        }

        if (in.startsWith("select")) {
            return parseSelect(input.substring(6).trim());
        }

        if (in.startsWith("delete")) {
            return parseDelete(input.substring(6).trim());
        }

        if (in.startsWith("update")) {
            return parseUpdate(input.substring(6).trim());
        }

        if (in.startsWith("show tables")) {
            return new Cmd(CmdType.SHOW_TABLES, null, null);
        }

        throw new IllegalArgumentException("Invalid command");
    }

    private Cmd parseCreateTable(String sArg) {
        if (sArg.isEmpty()) {
            throw new IllegalArgumentException("Invalid command args");
        }

        String tableName = sArg.split(" ")[0].trim();
        sArg = sArg.substring(tableName.length()).trim();

        if (!sArg.startsWith("(") || !sArg.endsWith(")")) {
            throw new IllegalArgumentException("Invalid command args");
        }

        sArg = sArg.substring(1, sArg.length() - 1).trim();
        String[] colArgs = sArg.split(",");
        List<Column> columns = new ArrayList<>();
        for (String colArg : colArgs) {
            colArg = colArg.trim();
            if (colArg.isEmpty()) {
                throw new IllegalArgumentException("Invalid column arg");
            }

            String[] colArgParts = colArg.split(" ");
            if (colArgParts.length != 2) {
                throw new IllegalArgumentException("Invalid column arg");
            }

            String colName = colArgParts[0].trim();
            String colType = colArgParts[1].trim().toLowerCase();
            if (colType.equals("char")) {
                columns.add(new Column(colName, ColumnType.CHAR, 32));
            } else if (colType.startsWith("char(") && colType.endsWith(")")) {
                int len;
                String lenS = colType.substring(5, colType.length() - 1);
                if (lenS.isEmpty()) {
                    len = 32;
                } else {
                    len = Integer.parseInt(lenS);
                }
                columns.add(new Column(colName, ColumnType.CHAR, len));
            } else {
                int len = 0;
                ColumnType columnType = ColumnType.fromName(colType);
                switch (columnType) {
                    case ColumnType.INT, ColumnType.FLOAT -> len = 4;
                    case ColumnType.BOOL -> len = 1;
                }
                columns.add(new Column(colName, columnType, len));
            }
        }
        return new Cmd(CmdType.CREATE_TABLE, tableName, new CreateTableArgs(columns));
    }

    private Cmd parseInsert(String sArg) {
        if (sArg.isEmpty()) {
            throw new IllegalArgumentException("Invalid command args");
        }

        String lArg = sArg.toLowerCase();

        if (!lArg.startsWith("into ")) {
            throw new IllegalArgumentException("Invalid command args");
        }
        lArg = lArg.substring(5).trim();

        String tableName = lArg.split(" ")[0].trim();
        if (tableName.isEmpty()) {
            throw new IllegalArgumentException("Invalid command args");
        }
        lArg = lArg.substring(tableName.length()).trim();

        if (!lArg.startsWith("values")) {
            throw new IllegalArgumentException("Invalid command args");
        }
        sArg = sArg.substring(sArg.indexOf("(")).trim();
        sArg = sArg.substring(1, sArg.length() - 1).trim();
        return new Cmd(CmdType.INSERT, tableName, new InsertArgs(parseRow(sArg)));
    }

    private Cmd parseSelect(String sArg) {
        if (sArg.isEmpty()) {
            throw new IllegalArgumentException("Invalid command args");
        }
        String lArg = sArg.toLowerCase();

        if (!lArg.startsWith("* ")) {
            throw new IllegalArgumentException("Invalid command args");
        }
        lArg = lArg.substring(2).trim();

        if (!lArg.startsWith("from ")) {
            throw new IllegalArgumentException("Invalid command args");
        }
        lArg = lArg.substring(5).trim();

        String tableName = lArg.split(" ")[0].trim();
        if (tableName.isEmpty()) {
            throw new IllegalArgumentException("Invalid command args");
        }
        lArg = lArg.substring(tableName.length()).trim();

        if (lArg.isEmpty()) {
            return new Cmd(CmdType.SELECT, tableName, new SelectArgs(false, 0, false));
        }

        if (lArg.startsWith("as")) {
            lArg = lArg.substring(2).trim();
            if (lArg.startsWith("md")) {
                return new Cmd(CmdType.SELECT, tableName, new SelectArgs(false, 0, true));
            }
            throw new IllegalArgumentException("Invalid command args");
        }

        if (!lArg.startsWith("where ")) {
            throw new IllegalArgumentException("Invalid command args");
        }
        lArg = lArg.substring(6).trim();

        if (!lArg.startsWith("id")) {
            throw new IllegalArgumentException("Invalid command args");
        }
        lArg = lArg.substring(2).trim();

        if (!lArg.startsWith("=")) {
            throw new IllegalArgumentException("Invalid command args");
        }
        lArg = lArg.substring(1).trim();

        int id;
        try {
            id = Integer.parseInt(lArg);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid command args");
        }

        return new Cmd(CmdType.SELECT, tableName, new SelectArgs(true, id, false));
    }

    private Cmd parseDelete(String sArg) {
        if (sArg.isEmpty()) {
            throw new IllegalArgumentException("Invalid command args");
        }

        String lArg = sArg.toLowerCase();

        if (!lArg.startsWith("from ")) {
            throw new IllegalArgumentException("Invalid command args");
        }
        lArg = lArg.substring(5).trim();

        String tableName = lArg.split(" ")[0].trim();
        if (tableName.isEmpty()) {
            throw new IllegalArgumentException("Invalid command args");
        }
        lArg = lArg.substring(tableName.length()).trim();

        if (lArg.isEmpty()) {
            throw new IllegalArgumentException("Invalid command args");
        }

        if (!lArg.startsWith("where ")) {
            throw new IllegalArgumentException("Invalid command args");
        }
        lArg = lArg.substring(6).trim();

        if (!lArg.startsWith("id")) {
            throw new IllegalArgumentException("Invalid command args");
        }
        lArg = lArg.substring(2).trim();

        if (!lArg.startsWith("=")) {
            throw new IllegalArgumentException("Invalid command args");
        }
        lArg = lArg.substring(1).trim();

        int id;
        try {
            id = Integer.parseInt(lArg);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid command args");
        }

        return new Cmd(CmdType.DELETE, tableName, new DeleteArgs(id));
    }

    private Cmd parseUpdate(String sArg) {
        if (sArg.isEmpty()) {
            throw new IllegalArgumentException("Invalid command args");
        }
        String lArg = sArg.toLowerCase();
        int setIndex = lArg.indexOf(" set ");
        if (setIndex == -1) {
            throw new IllegalArgumentException("Invalid command args");
        }
        setIndex++;

        int whereIndex = lArg.lastIndexOf(" where ");
        if (whereIndex == -1) {
            throw new IllegalArgumentException("Invalid command args");
        }
        whereIndex++;

        String tableName = lArg.split(" ")[0].trim();
        if (tableName.isEmpty()) {
            throw new IllegalArgumentException("Invalid command args");
        }
        lArg = lArg.substring(tableName.length()).trim();

        String setPart = sArg.substring(setIndex + 3, whereIndex).trim();
        Map<String, Object> values = parseMap(setPart);
        lArg = lArg.substring(lArg.lastIndexOf("where") + 5).trim();

        if (!lArg.startsWith("id")) {
            throw new IllegalArgumentException("Invalid command args");
        }
        lArg = lArg.substring(2).trim();

        if (!lArg.startsWith("=")) {
            throw new IllegalArgumentException("Invalid command args");
        }
        lArg = lArg.substring(1).trim();

        int id;
        try {
            id = Integer.parseInt(lArg);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid command args");
        }

        return new Cmd(CmdType.UPDATE, tableName, new UpdateArgs(id, values));
    }

    private Row parseRow(String sArg) {
        if (sArg.isEmpty()) {
            throw new IllegalArgumentException("Invalid command args");
        }

        String[] data = sArg.split(",");
        Object[] res = new Object[data.length];

        for (int i = 0; i < data.length; i++) {
            String s = data[i].trim();

            if (s.startsWith("'") && s.endsWith("'")) {
                res[i] = s.substring(1, s.length() - 1);
            } else if (s.equalsIgnoreCase("true") || s.equalsIgnoreCase("false")) {
                res[i] = Boolean.parseBoolean(s);
            } else if (s.contains(".")) {
                res[i] = Float.parseFloat(s);
            } else {
                res[i] = Integer.parseInt(s);
            }
        }

        return new Row(res);
    }

    private Map<String, Object> parseMap(String sArg) {
        if (sArg.isEmpty()) {
            throw new IllegalArgumentException("Invalid command args");
        }

        String[] data = sArg.split(",");
        Map<String, Object> res = new HashMap<>();

        for (String datum : data) {
            String colName = datum.split("=")[0].trim();
            String s = datum.split("=")[1].trim();

            if (s.startsWith("'") && s.endsWith("'")) {
                res.put(colName, s.substring(1, s.length() - 1));
            } else if (s.equalsIgnoreCase("true") || s.equalsIgnoreCase("false")) {
                res.put(colName, Boolean.parseBoolean(s));
            } else if (s.contains(".")) {
                res.put(colName, Float.parseFloat(s));
            } else {
                res.put(colName, Integer.parseInt(s));
            }
        }

        return res;
    }
}
