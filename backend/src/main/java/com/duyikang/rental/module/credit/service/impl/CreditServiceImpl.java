package com.duyikang.rental.module.credit.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.duyikang.rental.common.result.PageResult;
import com.duyikang.rental.module.credit.entity.CreditLog;
import com.duyikang.rental.module.credit.entity.CreditPolicy;
import com.duyikang.rental.module.credit.mapper.CreditLogMapper;
import com.duyikang.rental.module.credit.mapper.CreditPolicyMapper;
import com.duyikang.rental.module.credit.service.CreditService;
import com.duyikang.rental.module.credit.vo.CreditLogVO;
import com.duyikang.rental.module.user.entity.User;
import com.duyikang.rental.module.user.mapper.UserMapper;
import com.duyikang.rental.security.CurrentUser;
import com.duyikang.rental.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 信誉分服务实现（K4）。写库顺序：先查幂等与封顶 → 更新 user.credit_score → 插 credit_log。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreditServiceImpl implements CreditService {

    private static final long POLICY_ID = 1L;

    private static final int PAGE_SIZE_DEFAULT = 10;

    private static final int PAGE_SIZE_MAX = 100;

    private final CreditLogMapper creditLogMapper;

    private final CreditPolicyMapper creditPolicyMapper;

    private final UserMapper userMapper;

    @Override
    public void apply(Long userId, Long orderId, String reasonCode, int change, String reasonDesc) {
        if (userId == null || orderId == null || reasonCode == null || change == 0) {
            return;
        }
        // 幂等：同一订单同一原因只记一次
        long exists = creditLogMapper.selectCount(Wrappers.<CreditLog>lambdaQuery()
                .eq(CreditLog::getUserId, userId)
                .eq(CreditLog::getOrderId, orderId)
                .eq(CreditLog::getReasonCode, reasonCode));
        if (exists > 0) {
            log.debug("信誉变动已存在，跳过 userId={} orderId={} reason={}", userId, orderId, reasonCode);
            return;
        }

        CreditPolicy policy = creditPolicyMapper.selectById(POLICY_ID);
        if (policy == null) {
            log.warn("credit_policy 缺少 id=1，信誉变动被跳过 userId={} orderId={}", userId, orderId);
            return;
        }

        int effective = change;
        if (change > 0) {
            int cap = policy.getRewardDailyCap() == null ? Integer.MAX_VALUE : policy.getRewardDailyCap();
            Integer gained = creditLogMapper.selectObjs(Wrappers.<CreditLog>lambdaQuery()
                            .select(CreditLog::getChange)
                            .eq(CreditLog::getUserId, userId)
                            .gt(CreditLog::getChange, 0)
                            .ge(CreditLog::getCreatedAt, LocalDate.now().atStartOfDay()))
                    .stream()
                    .mapToInt(obj -> obj == null ? 0 : ((Number) obj).intValue())
                    .sum();
            int room = cap - gained;
            if (room <= 0) {
                log.info("用户 {} 今日信誉加分已达上限 {}，本次不加分 orderId={}", userId, cap, orderId);
                return;
            }
            effective = Math.min(change, room);
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            log.warn("信誉变动目标用户不存在 userId={}", userId);
            return;
        }
        int before = user.getCreditScore() == null ? 0 : user.getCreditScore();
        int min = policy.getMinScore() == null ? 0 : policy.getMinScore();
        int max = policy.getMaxScore() == null ? 100 : policy.getMaxScore();
        int after = Math.max(min, Math.min(max, before + effective));
        if (after == before) {
            log.info("用户 {} 信誉分已到边界（{}），本次变动被忽略 orderId={}", userId, before, orderId);
            return;
        }

        User patch = new User();
        patch.setId(userId);
        patch.setCreditScore(after);
        userMapper.updateById(patch);

        CreditLog entity = new CreditLog();
        entity.setUserId(userId);
        entity.setOrderId(orderId);
        entity.setChange(after - before);
        entity.setBeforeScore(before);
        entity.setAfterScore(after);
        entity.setReasonCode(reasonCode);
        entity.setReasonDesc(reasonDesc);
        entity.setCreatedAt(LocalDateTime.now());
        creditLogMapper.insert(entity);
        log.info("信誉变动 userId={} orderId={} reason={} {} -> {}", userId, orderId, reasonCode, before, after);
    }

    @Override
    public PageResult<CreditLogVO> myLogs(Integer pageNum, Integer pageSize) {
        LoginUser user = CurrentUser.require();
        long page = pageNum == null || pageNum < 1 ? 1 : pageNum;
        long size = pageSize == null || pageSize < 1 ? PAGE_SIZE_DEFAULT : Math.min(pageSize, PAGE_SIZE_MAX);
        Page<CreditLog> result = creditLogMapper.selectPage(new Page<>(page, size),
                Wrappers.<CreditLog>lambdaQuery()
                        .eq(CreditLog::getUserId, user.userId())
                        .orderByDesc(CreditLog::getCreatedAt)
                        .orderByDesc(CreditLog::getId));
        List<CreditLogVO> list = result.getRecords().stream()
                .map(item -> new CreditLogVO(item.getId(), item.getOrderId(), item.getChange(),
                        item.getBeforeScore(), item.getAfterScore(), item.getReasonCode(),
                        item.getReasonDesc(), item.getCreatedAt()))
                .toList();
        return PageResult.of(list, result.getTotal(), page, size);
    }
}
