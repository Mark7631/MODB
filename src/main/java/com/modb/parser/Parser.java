package com.modb.parser;

import com.modb.model.Cmd;

public interface Parser {
    Cmd parse(String input);
}
