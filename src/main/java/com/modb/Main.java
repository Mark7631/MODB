package com.modb;

import com.modb.client.DBClient;
import com.modb.client.ReplClient;
import com.modb.executor.Executor;
import com.modb.executor.ExecutorImpl;
import com.modb.parser.Parser;
import com.modb.parser.ParserImpl;
import com.modb.storageAPI.FileStorage;
import com.modb.storageAPI.Storage;

import java.io.IOException;

public class Main {
    static void main(String[] args) {
        try {
            Storage storage = new FileStorage();
            Parser parser = new ParserImpl();
            Executor executor = new ExecutorImpl(storage);
            DBClient client = new ReplClient(parser, executor);
            client.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
} // добавить в row имена стобцов, чтобы в консоль красиво выводить