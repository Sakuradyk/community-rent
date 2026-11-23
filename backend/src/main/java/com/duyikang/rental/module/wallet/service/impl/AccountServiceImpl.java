package com.duyikang.rental.module.wallet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.duyikang.rental.common.constant.AccountConst;
import com.duyikang.rental.common.constant.ErrorCode;
import com.duyikang.rental.common.exception.BizException;
import com.duyikang.rental.common.result.PageResult;
import com.duyikang.rental.module.order.entity.RentalOrder;
import com.duyikang.rental.module.system.entity.SysConfig;
import com.duyikang.rental.module.system.mapper.SysConfigMapper;
import com.duyikang.rental.module.user.entity.User;
import com.duyikang.rental.module.user.mapper.UserMapper;
import com.duyikang.rental.module.wallet.entity.Account;
import com.duyikang.rental.module.wallet.entity.AccountEntry;
import com.duyikang.rental.module.wallet.mapper.AccountEntryMapper;
import com.duyikang.rental.module.wallet.mapper.AccountMapper;
import com.duyikang.rental.module.wallet.service.AccountService;
import com.duyikang.rental.module.wallet.vo.AdminAccountVO;
import com.duyikang.rental.module.wallet.vo.AdminEntryVO;
import com.duyikang.rental.module.wallet.vo.ReconcileVO;
import com.duyikang.rental.module.wallet.vo.WalletEntryVO;
import com.duyikang.rental.module.wallet.vo.WalletVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 钱包与复式账务实现。
 *
 * <p>三条硬约束（对应规则 §8.9）：</p>
 * <ol>
 *   <li><b>复式</b>：每次变动都写成一对（出账方减、入账方加），金额恒正，方向由 from/to 表达；</li>
 *   <li><b>幂等</b>：{@code (biz_type, biz_id, action, from, to)} 唯一，重复触发只入账一次；</li>
 *   <li><b>守恒</b>：所有账户（可用 + 冻结）之和恒为 0，任何时刻可用一条 SQL 验证。</li>
 * </ol>
 *
 * <p>余额变动一律走 {@code AccountMapper} 的条件更新（{@code WHERE 余额 >= amount}），
 * 受影响行数为 0 就代表钱不够，直接失败并提示具体金额——不做「先查余额再扣」，
 * 那是并发下丢更新的经典写法。</p>
 *
 * @author duyikang
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final AccountMapper accountMapper;
    private final AccountEntryMapper accountEntryMapper;
    private final UserMapper userMapper;
    private final SysConfigMapper sysConfigMapper;

    /** 金额统一两位小数、四舍五入。 */
    private static final int MONEY_SCALE = 2;
    private static final BigDecimal ZERO_MONEY = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    private static final int PAGE_SIZE_DEFAULT = 10;
    private static final int PAGE_SIZE_MAX = 100;
    private static final String ENTRY_NO_DATE = "yyyyMMdd";
    private static final String RECHARGE_MAX_KEY = "wallet.recharge.max";
    private static final BigDecimal DEFAULT_RECHARGE_MAX = new BigDecimal("5000.00");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern(ENTRY_NO_DATE);

    // ------------------------------------------------------------------ 查询

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WalletVO wallet(Long userId) {
        Account account = ensureAccount(userId);
        return toWalletVO(account);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PageResult<WalletEntryVO> myEntries(Long userId, Integer pageNum, Integer pageSize) {
        Account account = ensureAccount(userId);
        int page = normalizePage(pageNum);
        int size = normalizeSize(pageSize);
        LambdaQueryWrapper<AccountEntry> wrapper = Wrappers.<AccountEntry>lambdaQuery()
                .and(w -> w.eq(AccountEntry::getFromAccountId, account.getId())
                        .or()
                        .eq(AccountEntry::getToAccountId, account.getId()))
                .orderByDesc(AccountEntry::getCreatedAt)
                .orderByDesc(AccountEntry::getId);
        Page<AccountEntry> result = accountEntryMapper.selectPage(new Page<>(page, size), wrapper);
        List<WalletEntryVO> list = result.getRecords().stream()
                .map(entry -> toWalletEntryVO(entry, account.getId()))
                .collect(Collectors.toList());
        return PageResult.of(list, result.getTotal(), page, size);
    }

    // ------------------------------------------------------------------ 资金动作

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WalletVO recharge(Long userId, BigDecimal amount) {
        BigDecimal money = scale2(amount);
        if (money.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "充值金额必须大于 0");
        }
        BigDecimal max = rechargeMax();
        if (money.compareTo(max) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "单次充值不能超过 " + max.toPlainString() + " 元");
        }
        Account userAccount = ensureAccount(userId);
        Account external = ensureExternalAccount();
        if (accountMapper.deductExternal(external.getId(), money) == 0) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "外部账户状态异常，请稍后重试");
        }
        if (accountMapper.addCash(userAccount.getId(), money) == 0) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "充值失败，请稍后重试");
        }
        // 充值没有天然单据号：用 64 位伪随机业务号保证幂等键 uk_entry_biz 不撞
        // （同一外部账户 → 同一用户账户的组合每次充值都相同，取 0 会让第二次充值直接撞键）
        writeEntry(AccountConst.BIZ_RECHARGE, nextRechargeBizId(), AccountConst.ACT_RECHARGE,
                external.getId(), AccountConst.BUCKET_CASH, userAccount.getId(), AccountConst.BUCKET_CASH,
                money, 1, "模拟充值 " + money.toPlainString() + " 元（演示，无支付渠道）");
        log.info("模拟充值成功：userId={}，金额={}", userId, money.toPlainString());
        return toWalletVO(accountMapper.selectById(userAccount.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void freezeForOrder(RentalOrder order) {
        BigDecimal amount = scale2(safe(order.getRentAmount()).add(safe(order.getDeposit())));
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        Account account = ensureAccount(order.getRenterId());
        if (accountMapper.moveCashToFrozen(account.getId(), amount) == 0) {
            BigDecimal cash = safe(account.getCashBalance());
            throw new BizException(ErrorCode.BALANCE_NOT_ENOUGH,
                    "账户余额不足：本次需冻结 " + amount.toPlainString() + " 元（租金 "
                            + safe(order.getRentAmount()).toPlainString() + " + 押金 "
                            + safe(order.getDeposit()).toPlainString() + "），当前可用 "
                            + cash.toPlainString() + " 元，请先充值");
        }
        writeEntry(AccountConst.BIZ_ORDER_FREEZE, order.getId(), AccountConst.ACT_FREEZE,
                account.getId(), AccountConst.BUCKET_CASH, account.getId(), AccountConst.BUCKET_FROZEN,
                amount, 1, "下单冻结（租金 + 押金），订单号 " + order.getOrderNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releaseForOrder(RentalOrder order, String remark) {
        // 幂等 + 防"凭空印钱"：没有冻结分录就不解冻（例如本功能上线前创建的老订单）
        if (accountEntryMapper.countByBiz(AccountConst.BIZ_ORDER_FREEZE, order.getId(),
                AccountConst.ACT_FREEZE) == 0) {
            log.info("订单 {} 无冻结记录，跳过解冻（老订单或已解冻）", order.getId());
            return;
        }
        if (accountEntryMapper.countByBiz(AccountConst.BIZ_ORDER_RELEASE, order.getId(),
                AccountConst.ACT_RELEASE) > 0) {
            log.info("订单 {} 已解冻过，跳过", order.getId());
            return;
        }
        BigDecimal amount = scale2(safe(order.getRentAmount()).add(safe(order.getDeposit())));
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        Account account = ensureAccount(order.getRenterId());
        if (accountMapper.moveFrozenToCash(account.getId(), amount) == 0) {
            log.warn("订单 {} 解冻失败：冻结余额不足，可能已被结算划走", order.getId());
            return;
        }
        writeEntry(AccountConst.BIZ_ORDER_RELEASE, order.getId(), AccountConst.ACT_RELEASE,
                account.getId(), AccountConst.BUCKET_FROZEN, account.getId(), AccountConst.BUCKET_CASH,
                amount, 1, remark == null ? "解除冻结，全额退回可用余额" : remark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void settleOrder(RentalOrder order, BigDecimal rentAmount, BigDecimal overdueFee,
                            BigDecimal lossFee, BigDecimal refundAmount) {
        Account renter = ensureAccount(order.getRenterId());
        Account owner = ensureAccount(order.getOwnerId());
        BigDecimal rent = scale2(rentAmount);
        BigDecimal overdue = scale2(overdueFee);
        BigDecimal loss = scale2(lossFee);
        BigDecimal refund = scale2(refundAmount);

        // 有冻结记录走「冻结桶」；没有（本功能上线前创建的老订单、或测试直接插库的订单）走「可用余额」
        boolean viaFrozen = accountEntryMapper.countByBiz(AccountConst.BIZ_ORDER_FREEZE, order.getId(),
                AccountConst.ACT_FREEZE) > 0;

        // 四条成对分录：租金 / 逾期费 / 损耗费全部归物主，押金剩余退承租方
        List<Flow> flows = new ArrayList<>();
        flows.add(new Flow(AccountConst.ACT_RENT, rent, "租金结转给物主"));
        flows.add(new Flow(AccountConst.ACT_OVERDUE, overdue, "逾期费结转给物主"));
        flows.add(new Flow(AccountConst.ACT_LOSS, loss, "损耗费结转为物主损失补偿"));
        int seq = 1;
        for (Flow flow : flows) {
            if (flow.amount().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            int deducted = viaFrozen
                    ? accountMapper.deductFrozen(renter.getId(), flow.amount())
                    : accountMapper.deductCash(renter.getId(), flow.amount());
            if (deducted == 0) {
                throw new BizException(ErrorCode.BALANCE_NOT_ENOUGH,
                        "承租方" + (viaFrozen ? "冻结" : "可用") + "余额不足，无法完成结算（" + flow.remark() + "）");
            }
            if (accountMapper.addCash(owner.getId(), flow.amount()) == 0) {
                throw new BizException(ErrorCode.SYSTEM_ERROR, "物主账户入账失败，请稍后重试");
            }
            writeEntry(AccountConst.BIZ_SETTLE, order.getId(), flow.action(),
                    renter.getId(), viaFrozen ? AccountConst.BUCKET_FROZEN : AccountConst.BUCKET_CASH,
                    owner.getId(), AccountConst.BUCKET_CASH,
                    flow.amount(), seq++, flow.remark() + "，订单号 " + order.getOrderNo());
        }
        if (refund.compareTo(BigDecimal.ZERO) > 0 && viaFrozen) {
            if (accountMapper.moveFrozenToCash(renter.getId(), refund) == 0) {
                throw new BizException(ErrorCode.BALANCE_NOT_ENOUGH, "承租方冻结余额不足，无法退回押金");
            }
            writeEntry(AccountConst.BIZ_SETTLE, order.getId(), AccountConst.ACT_REFUND,
                    renter.getId(), AccountConst.BUCKET_FROZEN, renter.getId(), AccountConst.BUCKET_CASH,
                    refund, seq, "押金应退部分退回可用余额，订单号 " + order.getOrderNo());
        } else if (refund.compareTo(BigDecimal.ZERO) > 0) {
            log.info("订单 {} 无冻结记录（老订单），押金本未实收，跳过退回动作", order.getId());
        }
        log.info("结算划转完成：orderId={}，物主入账={}，承租方退回={}", order.getId(),
                rent.add(overdue).add(loss).toPlainString(), refund.toPlainString());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void verdictAdjust(RentalOrder order, BigDecimal delta, String remark) {
        BigDecimal change = scale2(delta);
        if (change.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        Account renter = ensureAccount(order.getRenterId());
        Account owner = ensureAccount(order.getOwnerId());
        BigDecimal amount = change.abs();
        Long fromId;
        Long toId;
        if (change.compareTo(BigDecimal.ZERO) > 0) {
            // 应退金额变大：物主退回给承租方
            if (accountMapper.deductCash(owner.getId(), amount) == 0) {
                throw new BizException(ErrorCode.BALANCE_NOT_ENOUGH,
                        "物主可用余额不足，无法执行冲正（需 " + amount.toPlainString() + " 元）");
            }
            fromId = owner.getId();
            toId = renter.getId();
        } else {
            // 应退金额变小：承租方补付给物主
            if (accountMapper.deductCash(renter.getId(), amount) == 0) {
                throw new BizException(ErrorCode.BALANCE_NOT_ENOUGH,
                        "承租方可用余额不足，无法执行冲正（需 " + amount.toPlainString() + " 元）");
            }
            fromId = renter.getId();
            toId = owner.getId();
        }
        if (accountMapper.addCash(toId, amount) == 0) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "裁决冲正入账失败，请稍后重试");
        }
        writeEntry(AccountConst.BIZ_VERDICT, order.getId(), AccountConst.ACT_ADJUST,
                fromId, AccountConst.BUCKET_CASH, toId, AccountConst.BUCKET_CASH,
                amount, 9, remark == null ? "裁决冲正" : remark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WalletVO adminAdjust(Long userId, BigDecimal amount, String remark) {
        BigDecimal change = scale2(amount);
        if (change.compareTo(BigDecimal.ZERO) == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "调整金额不能为 0");
        }
        Account userAccount = ensureAccount(userId);
        Account external = ensureExternalAccount();
        BigDecimal abs = change.abs();
        Long fromId;
        Long toId;
        if (change.compareTo(BigDecimal.ZERO) > 0) {
            accountMapper.deductExternal(external.getId(), abs);
            if (accountMapper.addCash(userAccount.getId(), abs) == 0) {
                throw new BizException(ErrorCode.SYSTEM_ERROR, "调整失败，请稍后重试");
            }
            fromId = external.getId();
            toId = userAccount.getId();
        } else {
            if (accountMapper.deductCash(userAccount.getId(), abs) == 0) {
                throw new BizException(ErrorCode.BALANCE_NOT_ENOUGH,
                        "该用户可用余额不足，无法扣减 " + abs.toPlainString() + " 元");
            }
            accountMapper.addCash(external.getId(), abs);
            fromId = userAccount.getId();
            toId = external.getId();
        }
        writeEntry(AccountConst.BIZ_ADMIN_ADJUST, nextManualBizId(), AccountConst.ACT_ADJUST,
                fromId, AccountConst.BUCKET_CASH, toId, AccountConst.BUCKET_CASH,
                abs, 1, remark == null ? "管理端余额调整" : remark);
        return toWalletVO(accountMapper.selectById(userAccount.getId()));
    }

    // ------------------------------------------------------------------ 管理端查询

    @Override
    public PageResult<AdminAccountVO> adminAccounts(String keyword, Integer pageNum, Integer pageSize) {
        int page = normalizePage(pageNum);
        int size = normalizeSize(pageSize);
        List<Long> matchedUserIds = Collections.emptyList();
        if (keyword != null && !keyword.isBlank()) {
            String like = keyword.trim();
            matchedUserIds = userMapper.selectList(Wrappers.<User>lambdaQuery()
                            .like(User::getNickname, like)
                            .or()
                            .like(User::getPhone, like))
                    .stream().map(User::getId).collect(Collectors.toList());
            if (matchedUserIds.isEmpty()) {
                return PageResult.empty(page, size);
            }
        }
        LambdaQueryWrapper<Account> wrapper = Wrappers.<Account>lambdaQuery()
                .eq(Account::getOwnerType, AccountConst.OWNER_USER)
                .orderByDesc(Account::getUpdateTime)
                .orderByDesc(Account::getId);
        if (!matchedUserIds.isEmpty()) {
            wrapper.in(Account::getOwnerId, matchedUserIds);
        }
        Page<Account> result = accountMapper.selectPage(new Page<>(page, size), wrapper);
        Map<Long, User> users = loadUsers(result.getRecords().stream()
                .map(Account::getOwnerId).collect(Collectors.toList()));
        List<AdminAccountVO> list = result.getRecords().stream()
                .map(account -> {
                    User user = users.get(account.getOwnerId());
                    long entryCount = accountEntryMapper.selectCount(Wrappers.<AccountEntry>lambdaQuery()
                            .and(w -> w.eq(AccountEntry::getFromAccountId, account.getId())
                                    .or()
                                    .eq(AccountEntry::getToAccountId, account.getId())));
                    return new AdminAccountVO(account.getId(), account.getOwnerType(), account.getOwnerId(),
                            user == null ? "（用户已删除）" : user.getNickname(),
                            account.getCashBalance(), account.getFrozenBalance(),
                            sum(account.getCashBalance(), account.getFrozenBalance()),
                            entryCount, account.getUpdateTime());
                })
                .collect(Collectors.toList());
        return PageResult.of(list, result.getTotal(), page, size);
    }

    @Override
    public PageResult<AdminEntryVO> adminEntries(String bizType, Integer pageNum, Integer pageSize) {
        int page = normalizePage(pageNum);
        int size = normalizeSize(pageSize);
        LambdaQueryWrapper<AccountEntry> wrapper = Wrappers.<AccountEntry>lambdaQuery()
                .eq(bizType != null && !bizType.isBlank(), AccountEntry::getBizType,
                        bizType == null ? null : bizType.trim())
                .orderByDesc(AccountEntry::getCreatedAt)
                .orderByDesc(AccountEntry::getId);
        Page<AccountEntry> result = accountEntryMapper.selectPage(new Page<>(page, size), wrapper);
        if (result.getRecords().isEmpty()) {
            return PageResult.empty(page, size);
        }
        List<Long> accountIds = new ArrayList<>();
        result.getRecords().forEach(e -> {
            accountIds.add(e.getFromAccountId());
            accountIds.add(e.getToAccountId());
        });
        Map<Long, Account> accounts = accountMapper.selectBatchIds(accountIds).stream()
                .collect(Collectors.toMap(Account::getId, a -> a, (a, b) -> a));
        Map<Long, User> users = loadUsers(accounts.values().stream()
                .filter(a -> AccountConst.OWNER_USER.equals(a.getOwnerType()))
                .map(Account::getOwnerId).collect(Collectors.toList()));
        List<AdminEntryVO> list = result.getRecords().stream()
                .map(entry -> {
                    Account from = accounts.get(entry.getFromAccountId());
                    Account to = accounts.get(entry.getToAccountId());
                    return new AdminEntryVO(entry.getId(), entry.getEntryNo(), entry.getBizType(), entry.getBizId(),
                            entry.getAction(), AccountConst.labelOf(entry.getAction()),
                            from == null ? "" : from.getOwnerType(), from == null ? null : from.getOwnerId(),
                            nameOf(from, users), to == null ? "" : to.getOwnerType(),
                            to == null ? null : to.getOwnerId(), nameOf(to, users),
                            entry.getFromBucket(), entry.getToBucket(), entry.getAmount(),
                            entry.getFromBalanceAfter(), entry.getToBalanceAfter(),
                            entry.getRemark(), entry.getCreatedAt());
                })
                .collect(Collectors.toList());
        return PageResult.of(list, result.getTotal(), page, size);
    }

    @Override
    public ReconcileVO reconcile() {
        List<Account> accounts = accountMapper.selectList(null);
        BigDecimal external = ZERO_MONEY;
        BigDecimal userCash = ZERO_MONEY;
        BigDecimal userFrozen = ZERO_MONEY;
        for (Account account : accounts) {
            if (AccountConst.OWNER_EXTERNAL.equals(account.getOwnerType())) {
                external = external.add(safe(account.getCashBalance()));
            } else {
                userCash = userCash.add(safe(account.getCashBalance()));
                userFrozen = userFrozen.add(safe(account.getFrozenBalance()));
            }
        }
        BigDecimal sum = external.add(userCash).add(userFrozen);
        List<String> issues = new ArrayList<>();
        if (sum.abs().compareTo(new BigDecimal("0.005")) >= 0) {
            issues.add("全站余额之和不为 0（" + sum.toPlainString() + "），存在单边记账");
        }
        long entryCount = accountEntryMapper.selectCount(null);
        long accountCount = accounts.size();
        if (accountCount > 0 && entryCount == 0) {
            issues.add("存在账户但没有任何分录，账目不可追溯");
        }
        return new ReconcileVO(external, userCash, userFrozen, sum, issues.isEmpty(),
                entryCount, accountCount, LocalDateTime.now(), issues);
    }

    // ------------------------------------------------------------------ 内部工具

    /**
     * 取用户账户，不存在则懒创建（并发下靠 {@code uk_account} 兜底）。
     */
    private Account ensureAccount(Long userId) {
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        Account account = accountMapper.selectOne(Wrappers.<Account>lambdaQuery()
                .eq(Account::getOwnerType, AccountConst.OWNER_USER)
                .eq(Account::getOwnerId, userId));
        if (account != null) {
            return account;
        }
        Account fresh = new Account();
        fresh.setOwnerType(AccountConst.OWNER_USER);
        fresh.setOwnerId(userId);
        fresh.setCashBalance(ZERO_MONEY);
        fresh.setFrozenBalance(ZERO_MONEY);
        fresh.setVersion(0);
        try {
            accountMapper.insert(fresh);
        } catch (DuplicateKeyException e) {
            log.debug("账户已被并发创建：userId={}", userId);
        }
        return Objects.requireNonNull(accountMapper.selectOne(Wrappers.<Account>lambdaQuery()
                .eq(Account::getOwnerType, AccountConst.OWNER_USER)
                .eq(Account::getOwnerId, userId)));
    }

    /**
     * 取外部账户（全站唯一一行）。
     */
    private Account ensureExternalAccount() {
        Long id = accountMapper.selectExternalId();
        if (id != null) {
            return accountMapper.selectById(id);
        }
        Account external = new Account();
        external.setOwnerType(AccountConst.OWNER_EXTERNAL);
        external.setOwnerId(AccountConst.EXTERNAL_OWNER_ID);
        external.setCashBalance(ZERO_MONEY);
        external.setFrozenBalance(ZERO_MONEY);
        external.setVersion(0);
        try {
            accountMapper.insert(external);
        } catch (DuplicateKeyException e) {
            log.debug("外部账户已被并发创建");
        }
        return accountMapper.selectById(Objects.requireNonNull(accountMapper.selectExternalId()));
    }

    /**
     * 写一条分录：入账/出账后的余额快照取自实时读回，不靠心算。
     */
    private void writeEntry(String bizType, Long bizId, String action,
                            Long fromId, String fromBucket, Long toId, String toBucket,
                            BigDecimal amount, int seq, String remark) {
        Account from = accountMapper.selectById(fromId);
        Account to = accountMapper.selectById(toId);
        BigDecimal fromAfter = bucketOf(from, fromBucket);
        BigDecimal toAfter = bucketOf(to, toBucket);
        AccountEntry entry = new AccountEntry();
        entry.setEntryNo(nextEntryNo(seq));
        entry.setBizType(bizType);
        entry.setBizId(bizId == null ? 0L : bizId);
        entry.setAction(action);
        entry.setFromAccountId(fromId);
        entry.setToAccountId(toId);
        entry.setFromBucket(fromBucket);
        entry.setToBucket(toBucket);
        entry.setAmount(amount);
        entry.setFromBalanceAfter(fromAfter);
        entry.setToBalanceAfter(toAfter);
        entry.setRemark(remark);
        accountEntryMapper.insert(entry);
    }

    /**
     * 充值的业务号：充值没有天然单据号，而幂等键 uk_entry_biz 含 from/to 账户，
     * 同一用户第二次充值的组合与第一次完全相同，固定写 0 会直接撞唯一索引。
     * 用一个正数伪随机 64 位值作业务号，碰撞概率可忽略。
     */
    private long nextRechargeBizId() {
        return java.util.UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
    }

    /**
     * 人工调账的业务号：与充值同理（列表里的两笔调账 from/to 完全相同），
     * 业务号固定写 0 时第二次调账必撞 uk_entry_biz。
     */
    private long nextManualBizId() {
        return java.util.UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
    }

    /**
     * 分录号：F + yyyyMMdd + 4 位当日序号 + 2 位组内序号。
     */
    private String nextEntryNo(int seq) {
        String prefix = "F" + LocalDate.now().format(DAY);
        int today = accountEntryMapper.countByPrefix(prefix);
        return prefix + String.format("%04d", today + 1) + String.format("%02d", seq);
    }

    private BigDecimal bucketOf(Account account, String bucket) {
        if (account == null) {
            return ZERO_MONEY;
        }
        return scale2(AccountConst.BUCKET_FROZEN.equals(bucket) ? account.getFrozenBalance() : account.getCashBalance());
    }

    private String nameOf(Account account, Map<Long, User> users) {
        if (account == null) {
            return "";
        }
        if (AccountConst.OWNER_EXTERNAL.equals(account.getOwnerType())) {
            return "系统外部";
        }
        User user = users.get(account.getOwnerId());
        return user == null ? "用户" + account.getOwnerId() : user.getNickname();
    }

    private Map<Long, User> loadUsers(List<Long> userIds) {
        List<Long> ids = userIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) {
            return new HashMap<>();
        }
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
    }

    private WalletVO toWalletVO(Account account) {
        BigDecimal cash = safe(account.getCashBalance());
        BigDecimal frozen = safe(account.getFrozenBalance());
        return new WalletVO(account.getId(), cash, frozen, sum(cash, frozen));
    }

    private WalletEntryVO toWalletEntryVO(AccountEntry entry, Long accountId) {
        boolean incoming = Objects.equals(entry.getToAccountId(), accountId);
        String bucket = incoming ? entry.getToBucket() : entry.getFromBucket();
        BigDecimal balanceAfter = incoming ? entry.getToBalanceAfter() : entry.getFromBalanceAfter();
        String direction;
        if (!Objects.equals(entry.getFromBucket(), entry.getToBucket())) {
            direction = "MOVE";
        } else {
            direction = incoming ? "IN" : "OUT";
        }
        return new WalletEntryVO(entry.getId(), entry.getEntryNo(), entry.getAction(),
                AccountConst.labelOf(entry.getAction()), direction, bucket, entry.getAmount(),
                entry.getBizType(), entry.getBizId(), entry.getRemark(), scale2(balanceAfter),
                entry.getCreatedAt());
    }

    private BigDecimal rechargeMax() {
        SysConfig config = sysConfigMapper.selectOne(Wrappers.<SysConfig>lambdaQuery()
                .eq(SysConfig::getConfigKey, RECHARGE_MAX_KEY));
        if (config == null || config.getConfigValue() == null || config.getConfigValue().isBlank()) {
            return DEFAULT_RECHARGE_MAX;
        }
        try {
            return new BigDecimal(config.getConfigValue().trim());
        } catch (NumberFormatException e) {
            log.warn("wallet.recharge.max 配置值非法（{}），回退默认 {}", config.getConfigValue(),
                    DEFAULT_RECHARGE_MAX);
            return DEFAULT_RECHARGE_MAX;
        }
    }

    private static BigDecimal safe(BigDecimal value) {
        return value == null ? ZERO_MONEY : scale2(value);
    }

    private static BigDecimal scale2(BigDecimal value) {
        return value == null ? ZERO_MONEY : value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal sum(BigDecimal a, BigDecimal b) {
        return scale2(safe(a).add(safe(b)));
    }

    private static int normalizePage(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    private static int normalizeSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return PAGE_SIZE_DEFAULT;
        }
        return Math.min(pageSize, PAGE_SIZE_MAX);
    }

    /**
     * 一次资金去向的临时描述。
     */
    private record Flow(String action, BigDecimal amount, String remark) {
    }
}
