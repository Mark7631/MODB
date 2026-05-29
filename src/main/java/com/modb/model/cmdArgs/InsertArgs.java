package com.modb.model.cmdArgs;

import com.modb.model.Row;

public record InsertArgs (
        Row row
) implements Args {}