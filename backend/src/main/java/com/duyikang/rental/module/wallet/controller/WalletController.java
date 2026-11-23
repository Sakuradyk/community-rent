package com.duyikang.rental.module.wallet.controller;

import com.duyikang.rental.common.result.PageResult;
import com.duyikang.rental.common.result.Result;
import com.duyikang.rental.module.wallet.dto.RechargeDTO;
import com.duyikang.rental.module.wallet.service.AccountService;
import com.duyikang.rental.module.wallet.vo.WalletEntryVO;
import com.duyikang.rental.module.wallet.vo.WalletVO;
import com.duyikang.rental.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 钱包接口（全部需登录，且只能操作自己的账户）。
 *
 * @author duyikang
 */
@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final AccountService accountService;

    /**
     * 我的钱包：可用余额 / 冻结中 / 总额三行。
     *
     * @return 钱包快照
     */
    @GetMapping
    public Result<WalletVO> wallet() {
        return Result.ok(accountService.wallet(CurrentUser.requireId()));
    }

    /**
     * 模拟充值（演示用：点确认即到账，无支付渠道）。
     *
     * @param dto 充值金额
     * @return 充值后的钱包快照
     */
    @PostMapping("/recharge")
    public Result<WalletVO> recharge(@Valid @RequestBody RechargeDTO dto) {
        return Result.ok(accountService.recharge(CurrentUser.requireId(), dto.amount()));
    }

    /**
     * 我的资金流水。
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 分页流水
     */
    @GetMapping("/entries")
    public Result<PageResult<WalletEntryVO>> entries(@RequestParam(required = false) Integer pageNum,
                                                     @RequestParam(required = false) Integer pageSize) {
        return Result.ok(accountService.myEntries(CurrentUser.requireId(), pageNum, pageSize));
    }
}
