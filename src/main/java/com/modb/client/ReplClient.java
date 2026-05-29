package com.modb.client;


import com.modb.executor.Executor;
import com.modb.model.Cmd;
import com.modb.model.Result;
import com.modb.model.Row;
import com.modb.parser.Parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ReplClient implements DBClient {
    private final Parser parser;
    private final Executor executor;
    private final String helpMsg = "Available database commands:\n" +
            "    CREATE TABLE table (colName TYPE, ...)\n" +
            "    INSERT INTO table VALUES (type all values, without id)\n" +
            "    SELECT * FROM table\n" +
            "    SELECT * FROM table WHERE id = value\n" +
            "    SELECT * FROM table AS MD\n" +
            "    DELETE FROM table WHERE id = value\n" +
            "    UPDATE table SET col1 = value1, col2 = value2 WHERE id = value\n" +
            "    SHOW TABLES\n" +
            "Available Types:\n" +
            "    INT\n" +
            "    FLOAT\n" +
            "    BOOL\n" +
            "    CHAR(len default=32) note: write 'value' for string values\n" +
            "Console commands:\n" +
            "    help\n" +
            "    exit\n";

    public ReplClient(Parser parser, Executor executor) {
        this.parser = parser;
        this.executor = executor;
    }

    @Override
    public void start() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        while (true) {
            try {
                IO.print("modb> ");
                String line = reader.readLine();

                if (line == null || line.trim().equalsIgnoreCase("exit")) {
                    break;
                }

                if (line.trim().isEmpty()) {
                    continue;
                }

                if (line.trim().equalsIgnoreCase("help")) {
                    printHelp();
                    continue;
                }

                try {
                    Cmd cmd = parser.parse(line.trim());
                    Result res = executor.execute(cmd);

                    if (res.isSuccess()) {
                        if (res.getMessage() != null) {
                            IO.println(res.getMessage());
                        }

                        if (res.getRows() != null) {
                            if (res.getColNames() != null) {
                                IO.println(String.join(" ", res.getColNames()));
                            }
                            for (Row row : res.getRows()) {
                                IO.println(row);
                            }
                        }
                    } else {
                        IO.println(res.getMessage());
                    }
                } catch (RuntimeException e) {
                    IO.println(e.getMessage());
                }
            } catch (IOException e) {
                IO.println(e.getMessage());
            }
        }
    }

    private void printHelp() {
        IO.println(helpMsg);
    }
}
