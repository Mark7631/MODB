package com.modb.pager;

import com.modb.model.Page;

import java.io.IOException;
import java.io.RandomAccessFile;

public class FilePager implements Pager {
    private final RandomAccessFile file;
    private final int pageSize;

    public FilePager(String path, int pageSize) throws IOException {
        this.file = new RandomAccessFile(path, "rw");
        this.pageSize = pageSize;
    }

    @Override
    public Page getPage(int pageId) {
        try {
            byte[] data = new byte[pageSize];

            long offset = (long) pageId * pageSize;
            file.seek(offset);
            int read = file.read(data);

            if (read == -1) {
                return new Page(pageId, new byte[pageSize]);
            }

            return new Page(pageId, data);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read page " + pageId, e);
        }
    }

    @Override
    public void writePage(Page page) {
        try {
            long offset = (long) page.getPageId() * pageSize;
            file.seek(offset);

            file.write(page.getData());

        } catch (IOException e) {
            throw new RuntimeException("Failed to write page " + page.getPageId(), e);
        }
    }

    @Override
    public int allocatePage() {
        try {
            long fileSize = file.length();
            int pageId = (int) (fileSize / pageSize);

            file.setLength(fileSize + pageSize);

            return pageId;
        } catch (IOException e) {
            throw new RuntimeException("Failed to allocate page", e);
        }
    }

    @Override
    public int getPagesCount() {
        try {
            return (int) (file.length() / pageSize);
        } catch (IOException e) {
            throw new RuntimeException("Failed to get pages count", e);
        }
    }

    @Override
    public void close() {
        try {
            file.close();
        } catch (IOException e) {
            throw new RuntimeException("Failed to close file", e);
        }
    }
}
