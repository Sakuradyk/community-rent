package com.duyikang.rental.module.order.service;

import com.duyikang.rental.module.item.entity.Item;

import java.time.LocalDate;

/**
 * 下单的**事务部分**（K2 第三层：逐日展开 + 唯一索引兜底）。
 *
 * <p>为什么单独抽一个接口：Redis 锁必须在事务外面（见 {@code PROJECT_RULES.md} §8.3 前提 2）。
 * 如果 {@code @Transactional} 标在 {@link OrderService#create} 上，锁会在方法返回时释放，
 * 而事务要在方法返回**之后**才提交，中间存在"锁已放、数据未提交"的窗口——
 * 后一个请求预检读不到前一笔，只能靠唯一索引抛异常兜底，压测里会退化成一片 4090 异常。
 * 所以分层是：{@code OrderServiceImpl.create}（不标事务，负责锁）→ 本接口（标事务，只管写）。</p>
 */
public interface OrderTxService {

    /**
     * 在一个事务里写订单与占用行。
     *
     * <p>调用方必须已经：校验过物品可租、算过天数、确认过时段无冲突、持有该物品的 Redis 锁。
     * 这里不再做业务校验（事务内查询越多，持锁时间越长），只负责"要么全写成功、要么全不写"。</p>
     *
     * @param item      已校验可租的物品（价格从这里快照）
     * @param renterId  承租方
     * @param startDate 起租日（含）
     * @param endDate   归还日（不含）
     * @param rentDays  计费天数，≥ 1
     * @param remark    备注，可空
     * @return 新订单 id
     */
    Long createInTx(Item item, Long renterId, LocalDate startDate, LocalDate endDate,
                    int rentDays, String remark);
}
