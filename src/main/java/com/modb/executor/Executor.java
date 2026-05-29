package com.modb.executor;

import com.modb.model.Cmd;
import com.modb.model.Result;

public interface Executor {
    Result execute(Cmd cmd);
}
