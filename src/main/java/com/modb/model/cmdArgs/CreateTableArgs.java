package com.modb.model.cmdArgs;

import com.modb.model.Column;

import java.util.List;

public record CreateTableArgs (
        List<Column> columns
) implements Args {}