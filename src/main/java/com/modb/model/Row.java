package com.modb.model;

import java.util.Arrays;

public class Row { // не должна включать id по умолчанию
    private Object[] values;

    public Row(Object[] values) {
        this.values = values.clone();
    }

    public Object get(int i) {
        return values[i];
    }

    public void update(int i, Object value) {
        values[i] = value;
    }

    @Override
    public String toString() {
        return Arrays.toString(values);
    }
}
