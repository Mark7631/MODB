package com.modb.model.cmdArgs;

public record SelectArgs (
        boolean hasWhere,
        int id,
        boolean asMD
) implements Args {}