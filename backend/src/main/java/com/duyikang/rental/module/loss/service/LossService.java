package com.duyikang.rental.module.loss.service;

import com.duyikang.rental.module.item.entity.Item;
import com.duyikang.rental.module.order.entity.RentalOrder;

import java.math.BigDecimal;

/**
 * 损耗核算服务（K6，被 K3 结算调用）。
 *
 * <p>一次归还做三件事：算出本次损耗值 w → 累加进物品的 `loss_value`（L）→ 按 L 决定物品去向
 * （继续在架 / 需保养 / 需检修），并写一条 `loss_log` 让累计过程可复盘。</p>
 */
public interface LossService {

    /**
     * 核算一次归还的损耗，并写回物品与损耗日志
     *
     * @param item        物品（调用方已加载）
     * @param order       订单（提供 rentDays 与 deposit 快照）
     * @param returnState 归还状态 INTACT / WORN / DAMAGED
     * @return 核算结果（供结算单直接使用）
     */
    LossResult apply(Item item, RentalOrder order, String returnState);

    /**
     * 一次损耗核算的结果
     *
     * @param weight     本次损耗值 w
     * @param lossFee    损耗费 = 押金 × w
     * @param lossBefore 归还前累计损耗
     * @param lossAfter  归还后累计损耗
     * @param action     处置动作 NONE / MAINTAIN / REPAIR
     * @param itemStatus 物品核算后的状态
     */
    record LossResult(BigDecimal weight, BigDecimal lossFee, BigDecimal lossBefore,
                      BigDecimal lossAfter, String action, String itemStatus) {
    }
}
