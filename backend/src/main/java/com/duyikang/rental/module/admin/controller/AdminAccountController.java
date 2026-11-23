package com.duyikang.rental.module.admin.controller;

import com.duyikang.rental.common.result.PageResult;
import com.duyikang.rental.common.result.Result;
import com.duyikang.rental.module.wallet.dto.AdminAdjustDTO;
import com.duyikang.rental.module.wallet.service.AccountService;
import com.duyikang.rental.module.wallet.vo.AdminAccountVO;
import com.duyikang.rental.module.wallet.vo.AdminEntryVO;
import com.duyikang.rental.module.wallet.vo.ReconcileVO;
import com.duyikang.rental.module.wallet.vo.WalletVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端账务接口：账户列表、全局分录、手工调整、对账自检。
 *
 * <p>路径在 {@code /api/admin/**} 下，由 SecurityConfig 统一要求 ADMIN 角色。</p>
 *
 * @author duyikang
 */
@RestController
@RequestMapping("/api/admin/accounts")
@RequiredArgsConstructor
public class AdminAccountController {

    private final AccountService accountService;

    /**
     * 账户列表（可按昵称/手机号过滤）。
     *
     * @param keyword  关键字
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 分页账户
     */
    @GetMapping
    public Result<PageResult<AdminAccountVO>> accounts(@RequestParam(required = false) String keyword,
                                                       @RequestParam(required = false) Integer pageNum,
                                                       @RequestParam(required = false) Integer pageSize) {
        return Result.ok(accountService.adminAccounts(keyword, pageNum, pageSize));
    }

    /**
     * 全局分录（跨账户资金流向，按业务类型可过滤）。
     *
     * @param bizType  业务类型
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 分页分录
     */
    @GetMapping("/entries")
    public Result<PageResult<AdminEntryVO>> entries(@RequestParam(required = false) String bizType,
                                                    @RequestParam(required = false) Integer pageNum,
                                                    @RequestParam(required = false) Integer pageSize) {
        return Result.ok(accountService.adminEntries(bizType, pageNum, pageSize));
    }

    /**
     * 手工调整某个用户的可用余额。
     *
     * @param userId 用户 id
     * @param dto    金额与理由
     * @return 调整后的钱包快照
     */
    @PostMapping("/{userId}/adjust")
    public Result<WalletVO> adjust(@PathVariable Long userId, @Valid @RequestBody AdminAdjustDTO dto) {
        return Result.ok(accountService.adminAdjust(userId, dto.amount(), dto.remark()));
    }

    /**
     * 对账自检：全站余额之和是否守恒。
     *
     * @return 对账结果
     */
    @GetMapping("/reconcile")
    public Result<ReconcileVO> reconcile() {
        return Result.ok(accountService.reconcile());
    }
}
