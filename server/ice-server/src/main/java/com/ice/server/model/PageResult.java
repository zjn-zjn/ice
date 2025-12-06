package com.ice.server.model;

import lombok.Data;

import java.util.List;

@Data
public class PageResult<T> {
    private long total;
    private int pageNum;
    private int pageSize;
    private int pages;
    private List<T> list;
}
