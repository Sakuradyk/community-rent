package com.duyikang.rental.common.result;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 分页响应体。
 *
 * <p>约定（PROJECT_RULES.md §7）：pageNum 从 1 起，pageSize 上限 100。</p>
 *
 * @param <T> 列表元素类型
 */
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前页数据。 */
    private List<T> list;

    /** 总记录数。 */
    private long total;

    /** 当前页码，从 1 开始。 */
    private long pageNum;

    /** 每页条数。 */
    private long pageSize;

    public PageResult() {
        this.list = new ArrayList<>();
    }

    public PageResult(List<T> list, long total, long pageNum, long pageSize) {
        this.list = list == null ? new ArrayList<>() : list;
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
    }

    public static <T> PageResult<T> of(List<T> list, long total, long pageNum, long pageSize) {
        return new PageResult<>(list, total, pageNum, pageSize);
    }

    public static <T> PageResult<T> empty(long pageNum, long pageSize) {
        return new PageResult<>(new ArrayList<>(), 0L, pageNum, pageSize);
    }

    public List<T> getList() {
        return list;
    }

    public void setList(List<T> list) {
        this.list = list;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getPageNum() {
        return pageNum;
    }

    public void setPageNum(long pageNum) {
        this.pageNum = pageNum;
    }

    public long getPageSize() {
        return pageSize;
    }

    public void setPageSize(long pageSize) {
        this.pageSize = pageSize;
    }
}
