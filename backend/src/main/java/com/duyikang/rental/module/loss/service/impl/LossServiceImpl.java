package com.duyikang.rental.module.loss.service.impl;

import com.duyikang.rental.common.constant.ItemStatus;
import com.duyikang.rental.common.constant.ReturnState;
import com.duyikang.rental.common.exception.BizException;
import com.duyikang.rental.common.constant.ErrorCode;
import com.duyikang.rental.module.item.entity.Category;
import com.duyikang.rental.module.item.entity.Item;
import com.duyikang.rental.module.item.mapper.CategoryMapper;
import com.duyikang.rental.module.item.mapper.ItemMapper;
import com.duyikang.rental.module.loss.entity.LossLog;
import com.duyikang.rental.module.loss.entity.LossPolicy;
import com.duyikang.rental.module.loss.mapper.LossLogMapper;
import com.duyikang.rental.module.loss.mapper.LossPolicyMapper;
import com.duyikang.rental.module.loss.service.LossService;
import com.duyikang.rental.module.order.entity.RentalOrder;
import com.duyikang.rental.module.settlement.calc.FeeCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 损耗核算实现（K6）。w 与 L 的口径全部委托 {@link FeeCalculator}，这里只负责取数据与落库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LossServiceImpl implements LossService {

    private static final long POLICY_ID = 1L;

    private final LossPolicyMapper lossPolicyMapper;

    private final LossLogMapper lossLogMapper;

    private final CategoryMapper categoryMapper;

    private final ItemMapper itemMapper;

    @Override
    public LossResult apply(Item item, RentalOrder order, String returnState) {
        if (!ReturnState.isValid(returnState)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "归还状态只能是：完好 / 轻微磨损 / 损坏");
        }
        LossPolicy policy = lossPolicyMapper.selectById(POLICY_ID);
        if (policy == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "损耗策略未初始化（loss_policy 缺少 id=1）");
        }
        Category category = categoryMapper.selectById(item.getCategoryId());
        BigDecimal categoryCoef = category == null || category.getLossCoef() == null
                ? BigDecimal.ZERO : category.getLossCoef();

        BigDecimal before = item.getLossValue() == null ? BigDecimal.ZERO : item.getLossValue();
        BigDecimal weight = FeeCalculator.lossWeight(order.getRentDays(), categoryCoef, returnState, policy);
        BigDecimal after = FeeCalculator.lossAfter(before, weight);
        BigDecimal lossFee = FeeCalculator.lossFee(order.getDeposit(), weight);
        String action = FeeCalculator.actionOf(after, policy);
        String nextStatus = "REPAIR".equals(action) ? ItemStatus.MAINTAINING : ItemStatus.ON_SHELF;

        LossLog entity = new LossLog();
        entity.setItemId(item.getId());
        entity.setOrderId(order.getId());
        entity.setTermCoef(FeeCalculator.termCoef(order.getRentDays(), policy));
        entity.setCategoryCoef(categoryCoef);
        entity.setReturnCoef(FeeCalculator.returnCoef(returnState, policy));
        entity.setWeight(weight);
        entity.setLossBefore(before);
        entity.setLossAfter(after);
        entity.setReturnState(returnState);
        entity.setAction(action);
        entity.setCreatedAt(LocalDateTime.now());
        lossLogMapper.insert(entity);

        Item patch = new Item();
        patch.setId(item.getId());
        patch.setLossValue(after);
        patch.setStatus(nextStatus);
        itemMapper.updateById(patch);

        log.info("损耗核算 itemId={} orderId={} 状态={} w={} L: {} -> {} 动作={} 物品转={}",
                item.getId(), order.getId(), ReturnState.labelOf(returnState), weight, before, after, action, nextStatus);
        return new LossResult(weight, lossFee, before, after, action, nextStatus);
    }
}
