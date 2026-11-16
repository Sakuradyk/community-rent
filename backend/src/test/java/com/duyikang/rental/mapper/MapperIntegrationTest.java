package com.duyikang.rental.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.duyikang.rental.module.appeal.mapper.AppealMapper;
import com.duyikang.rental.module.credit.entity.CreditPolicy;
import com.duyikang.rental.module.credit.mapper.CreditLogMapper;
import com.duyikang.rental.module.credit.mapper.CreditPolicyMapper;
import com.duyikang.rental.module.item.entity.Community;
import com.duyikang.rental.module.item.entity.Item;
import com.duyikang.rental.module.item.mapper.CategoryMapper;
import com.duyikang.rental.module.item.mapper.CommunityMapper;
import com.duyikang.rental.module.item.mapper.ItemImageMapper;
import com.duyikang.rental.module.item.mapper.ItemMapper;
import com.duyikang.rental.module.loss.entity.LossPolicy;
import com.duyikang.rental.module.loss.mapper.LossLogMapper;
import com.duyikang.rental.module.loss.mapper.LossPolicyMapper;
import com.duyikang.rental.module.message.mapper.MessageMapper;
import com.duyikang.rental.module.order.entity.OrderOccupancy;
import com.duyikang.rental.module.order.entity.RentalOrder;
import com.duyikang.rental.module.order.mapper.OrderOccupancyMapper;
import com.duyikang.rental.module.order.mapper.RentalOrderMapper;
import com.duyikang.rental.module.settlement.entity.FeePolicy;
import com.duyikang.rental.module.settlement.mapper.FeeFlowMapper;
import com.duyikang.rental.module.settlement.mapper.FeePolicyMapper;
import com.duyikang.rental.module.settlement.mapper.SettlementMapper;
import com.duyikang.rental.module.system.mapper.SysConfigMapper;
import com.duyikang.rental.module.user.entity.User;
import com.duyikang.rental.module.user.mapper.RoleMapper;
import com.duyikang.rental.module.user.mapper.UserMapper;
import com.duyikang.rental.module.user.mapper.UserRoleMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 19 张表实体 + Mapper 集成测试。
 *
 * <p>连真实 {@code rental} 库（种子数据来自 {@code backend/sql/02-data.sql}），
 * 每个测试方法自带 {@code @Transactional}，结束后自动回滚，不污染库。</p>
 *
 * <p>断言重点不在"能查出来"，而在三件容易悄悄坏掉的事：金额精度有没有被 double 吃掉、
 * 日期有没有按半开区间映射、唯一索引是不是真的在拦重复占用（K2 的最后一道防线）。</p>
 */
@SpringBootTest
@Transactional
@DisplayName("Mapper 层：19 张表的映射与约束")
class MapperIntegrationTest {

    @Autowired private UserMapper userMapper;
    @Autowired private RoleMapper roleMapper;
    @Autowired private UserRoleMapper userRoleMapper;
    @Autowired private CategoryMapper categoryMapper;
    @Autowired private CommunityMapper communityMapper;
    @Autowired private ItemMapper itemMapper;
    @Autowired private ItemImageMapper itemImageMapper;
    @Autowired private RentalOrderMapper rentalOrderMapper;
    @Autowired private OrderOccupancyMapper orderOccupancyMapper;
    @Autowired private SettlementMapper settlementMapper;
    @Autowired private FeeFlowMapper feeFlowMapper;
    @Autowired private FeePolicyMapper feePolicyMapper;
    @Autowired private CreditLogMapper creditLogMapper;
    @Autowired private CreditPolicyMapper creditPolicyMapper;
    @Autowired private LossLogMapper lossLogMapper;
    @Autowired private LossPolicyMapper lossPolicyMapper;
    @Autowired private AppealMapper appealMapper;
    @Autowired private MessageMapper messageMapper;
    @Autowired private SysConfigMapper sysConfigMapper;

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("selectCount_19张表_与种子数据行数一致")
    void selectCount_allTables_equalsSeedData() {
        assertThat(userMapper.selectCount(null)).isEqualTo(4L);
        assertThat(roleMapper.selectCount(null)).isEqualTo(2L);
        assertThat(userRoleMapper.selectCount(null)).isEqualTo(5L);
        assertThat(communityMapper.selectCount(null)).isEqualTo(6L);
        assertThat(categoryMapper.selectCount(null)).isEqualTo(11L);
        assertThat(itemMapper.selectCount(null)).isEqualTo(7L);
        assertThat(itemImageMapper.selectCount(null)).isEqualTo(13L);
        assertThat(rentalOrderMapper.selectCount(null)).isEqualTo(3L);
        assertThat(orderOccupancyMapper.selectCount(null)).isEqualTo(3L);
        assertThat(settlementMapper.selectCount(null)).isEqualTo(1L);
        assertThat(feeFlowMapper.selectCount(null)).isEqualTo(4L);
        assertThat(creditLogMapper.selectCount(null)).isEqualTo(2L);
        assertThat(lossLogMapper.selectCount(null)).isEqualTo(1L);
        // 无申诉种子数据：0 行同样证明映射正常（"0 行"和"表名写错"是两回事）
        assertThat(appealMapper.selectCount(null)).isEqualTo(0L);
        assertThat(messageMapper.selectCount(null)).isEqualTo(2L);
        assertThat(creditPolicyMapper.selectCount(null)).isEqualTo(1L);
        assertThat(feePolicyMapper.selectCount(null)).isEqualTo(1L);
        assertThat(lossPolicyMapper.selectCount(null)).isEqualTo(1L);
        assertThat(sysConfigMapper.selectCount(null)).isEqualTo(9L);
    }

    @Test
    @DisplayName("社区引用_user与item_映射为communityId")
    void communityId_mapped_bothSides() {
        // 物品 1 属于阳光花园（id=1），用户 4 属于绿洲家园（id=2）
        assertThat(itemMapper.selectById(1L).getCommunityId()).isEqualTo(1L);
        assertThat(userMapper.selectById(4L).getCommunityId()).isEqualTo(2L);
        // 停用的小区（紫竹园 id=6）依然能被实体读出来：停用只影响下拉，不影响历史引用
        Community zizhu = communityMapper.selectById(6L);
        assertThat(zizhu).isNotNull();
        assertThat(zizhu.getEnabled()).isZero();
        assertThat(zizhu.getDistrict()).isEqualTo("城北街道");
    }
    @Test
    @DisplayName("金额字段_读取_DECIMAL精度与标度不丢")
    void moneyFields_read_keepScale() {
        Item item = itemMapper.selectById(1L);
        assertThat(item).isNotNull();
        assertThat(item.getDailyPrice()).isEqualByComparingTo("25.00");
        assertThat(item.getDailyPrice().scale()).isEqualTo(2);
        assertThat(item.getDeposit()).isEqualByComparingTo("100.00");
        assertThat(item.getDeposit().scale()).isEqualTo(2);
        // overdue_rate / loss_value 是 DECIMAL(6,4)：4 位小数必须原样保留
        assertThat(item.getOverdueRate()).isEqualByComparingTo("0.5000");
        assertThat(item.getOverdueRate().scale()).isEqualTo(4);
        assertThat(item.getLossValue()).isEqualByComparingTo("0.1200");
        assertThat(item.getLossValue().scale()).isEqualTo(4);

        Item second = itemMapper.selectById(2L);
        assertThat(second.getDailyPrice()).isEqualByComparingTo("60.00");
        assertThat(second.getDeposit()).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("订单_日期与快照金额_映射正确")
    void order_dateAndSnapshot_mapped() {
        RentalOrder order = rentalOrderMapper.selectById(1L);
        assertThat(order).isNotNull();
        assertThat(order.getOrderNo()).isEqualTo("R202610050001");
        assertThat(order.getStartDate()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(order.getEndDate()).isEqualTo(LocalDate.of(2026, 10, 8));
        // 半开区间 [start, end)：3 天，不是 4 天
        assertThat(order.getRentDays()).isEqualTo(3);
        assertThat(order.getStatus()).isEqualTo("COMPLETED");
        assertThat(order.getDailyPrice()).isEqualByComparingTo("25.00");
        assertThat(order.getRentAmount()).isEqualByComparingTo("75.00");
        assertThat(order.getOwnerId()).isEqualTo(2L);
        assertThat(order.getRenterId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("占用表_按物品查询_逐日展开为三天")
    void occupancy_byItem_expandedPerDay() {
        List<OrderOccupancy> rows = orderOccupancyMapper.selectList(
                new QueryWrapper<OrderOccupancy>()
                        .eq("item_id", 2L)
                        .orderByAsc("occupy_date"));

        assertThat(rows).hasSize(3);
        assertThat(rows).extracting(OrderOccupancy::getOccupyDate).containsExactly(
                LocalDate.of(2026, 11, 20),
                LocalDate.of(2026, 11, 21),
                LocalDate.of(2026, 11, 22));
        // 三行同属一笔订单：若改成一单一行，唯一索引永远撞不上，K2 等于没有
        assertThat(rows).extracting(OrderOccupancy::getOrderId).containsOnly(2L);
    }

    @Test
    @DisplayName("重复写入同日占用_抛DuplicateKeyException")
    void insertDuplicateOccupancy_throwsDuplicateKey() {
        OrderOccupancy conflict = new OrderOccupancy();
        conflict.setItemId(2L);
        conflict.setOrderId(2L);
        conflict.setOccupyDate(LocalDate.of(2026, 11, 20));

        assertThatThrownBy(() -> orderOccupancyMapper.insert(conflict))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("uk_occupancy");
    }

    @Test
    @DisplayName("逻辑删除_查询已过滤_物理行仍在")
    void logicDelete_filteredInQuery_butRowRemains() {
        userMapper.deleteById(2L);

        assertThat(userMapper.selectById(2L)).isNull();
        // 绕过逻辑删除直接查库：行还在，deleted 被置 1
        Integer deleted = jdbcTemplate.queryForObject(
                "SELECT deleted FROM `user` WHERE id = 2", Integer.class);
        assertThat(deleted).isEqualTo(1);
    }

    @Test
    @DisplayName("分页插件_已注册_总数与页数正确")
    void selectPage_pluginRegistered_totalAndPages() {
        IPage<User> page = userMapper.selectPage(new Page<>(1, 2), null);

        assertThat(page.getTotal()).isEqualTo(4L);
        assertThat(page.getPages()).isEqualTo(2L);
        assertThat(page.getRecords()).hasSize(2);
    }

    @Test
    @DisplayName("策略单行表_按固定id读取_费率与阈值正确")
    void policyTables_selectById_keyValues() {
        FeePolicy fee = feePolicyMapper.selectById(1);
        assertThat(fee).isNotNull();
        assertThat(fee.getOverdueTier1Days()).isEqualTo(3);
        assertThat(fee.getOverdueTier1Rate()).isEqualByComparingTo("0.5000");
        assertThat(fee.getOverdueTier2Rate()).isEqualByComparingTo("1.0000");
        assertThat(fee.getDailyPriceMax()).isEqualByComparingTo("500.00");

        CreditPolicy credit = creditPolicyMapper.selectById(1);
        assertThat(credit).isNotNull();
        assertThat(credit.getInitScore()).isEqualTo(80);
        assertThat(credit.getMinScore()).isEqualTo(0);
        assertThat(credit.getMaxScore()).isEqualTo(100);
        assertThat(credit.getPenaltyOverdueLight()).isEqualTo(-5);
        assertThat(credit.getTierSuspendBelow()).isEqualTo(40);
        assertThat(credit.getTierDepositUpRatio()).isEqualByComparingTo("1.50");

        LossPolicy loss = lossPolicyMapper.selectById(1);
        assertThat(loss).isNotNull();
        assertThat(loss.getThresholdMaintain()).isEqualByComparingTo("0.6000");
        assertThat(loss.getThresholdRepair()).isEqualByComparingTo("0.8500");
        assertThat(loss.getReturnCoefDamaged()).isEqualByComparingTo("3.0000");
    }
}
