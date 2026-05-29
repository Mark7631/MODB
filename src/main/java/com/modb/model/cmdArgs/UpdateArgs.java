package com.modb.model.cmdArgs;

import com.modb.model.Row;

import java.util.Map;

public record UpdateArgs (
        int id,
        Map<String, Object> toChange
) implements Args {}