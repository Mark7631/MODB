package com.modb.model;

public class Page {
    private final int pageId;
    private final byte[] data;

    public Page(int pageId, byte[] data) {
        this.pageId = pageId;
        this.data = data.clone();
    }

    public int getPageId() {
        return pageId;
    }

    public byte[] getData() {
        return data;
    }
}
