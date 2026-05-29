package com.modb.pager;

import com.modb.model.Page;

public interface Pager {
    Page getPage(int pageId);
    void writePage(Page page);
    int allocatePage();
    int getPagesCount();
    void close();
}
