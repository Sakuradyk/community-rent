package com.duyikang.rental.module.order;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.duyikang.rental.common.constant.ErrorCode;
import com.duyikang.rental.common.exception.BizException;
import com.duyikang.rental.module.order.dto.OrderCreateDTO;
import com.duyikang.rental.module.order.entity.OrderOccupancy;
import com.duyikang.rental.module.order.entity.RentalOrder;
import com.duyikang.rental.module.order.mapper.OrderOccupancyMapper;
import com.duyikang.rental.module.order.mapper.RentalOrderMapper;
import com.duyikang.rental.module.order.service.OrderService;
import com.duyikang.rental.module.order.vo.OrderCreatedVO;
import com.duyikang.rental.security.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * K2 并发压测：同一物品、同一时段，50 个线程同时下单。
 *
 * <p><b>为什么单独一个类、且不加 {@code @Transactional}</b>：并发线程各有自己的事务，
 * 外层测试事务无法回滚它们；所以这里用真实提交，再由 {@link #cleanUp} 按 id 区间删掉本次产生的订单。
 * 删除范围是「执行前的最大订单 id 之后」，不碰种子与用户手工下的单。</p>
 *
 * <p>断言口径（{@code PROJECT_RULES.md} §8.3 / §12）：成功恰好 1 单；其余全部 4090；
 * <b>不允许出现 5003（锁失败）或 5000（系统异常）</b>；占用行数 = 订单天数且无重复。</p>
 */
@SpringBootTest
class OrderConcurrencyTest {

    private static final int THREADS = 50;
    /** 物品 4 = 李娜(3) 的二手小说，日租 3.00、押金 20.00、在架 */
    private static final Long ITEM_ID = 4L;
    private static final Long RENTER_ID = 2L;
    private static final String RENTER_NAME = "张伟";

    @Autowired
    private OrderService orderService;
    @Autowired
    private RentalOrderMapper rentalOrderMapper;
    @Autowired
    private OrderOccupancyMapper orderOccupancyMapper;

    private Long maxOrderIdBefore;
    private LocalDate start;
    private LocalDate end;

    @BeforeEach
    void setUp() {
        List<RentalOrder> latest = rentalOrderMapper.selectList(
                Wrappers.<RentalOrder>lambdaQuery().orderByDesc(RentalOrder::getId).last("LIMIT 1"));
        maxOrderIdBefore = latest.isEmpty() ? 0L : latest.get(0).getId();
        start = LocalDate.now().plusDays(40);
        end = start.plusDays(3);
    }

    @AfterEach
    void cleanUp() {
        // 先删占用再删订单（没有物理外键，顺序只是习惯）
        orderOccupancyMapper.delete(Wrappers.<OrderOccupancy>lambdaQuery()
                .eq(OrderOccupancy::getItemId, ITEM_ID)
                .ge(OrderOccupancy::getOccupyDate, start)
                .lt(OrderOccupancy::getOccupyDate, end));
        rentalOrderMapper.delete(Wrappers.<RentalOrder>lambdaQuery()
                .eq(RentalOrder::getItemId, ITEM_ID)
                .gt(RentalOrder::getId, maxOrderIdBefore));
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("下单并发：50 线程同抢同一时段 → 成功 1 单，其余全 4090，无 5003/5000")
    void concurrentCreate_sameSlot_onlyOneSucceeds() throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch fire = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        Queue<Integer> codes = new ConcurrentLinkedQueue<>();
        Queue<String> orderIds = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < THREADS; i++) {
            Thread worker = new Thread(() -> {
                LoginUser user = new LoginUser(RENTER_ID, RENTER_NAME, "USER");
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        user, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
                ready.countDown();
                try {
                    fire.await(10, TimeUnit.SECONDS);
                    OrderCreatedVO created = orderService.create(
                            new OrderCreateDTO(ITEM_ID, start, end, "并发压测"));
                    codes.add(ErrorCode.SUCCESS.getCode());
                    orderIds.add(String.valueOf(created.id()));
                } catch (BizException e) {
                    codes.add(e.getCode());
                } catch (Exception e) {
                    // 任何非业务异常都记成 5000：压测要求这类必须是 0
                    codes.add(ErrorCode.SYSTEM_ERROR.getCode());
                } finally {
                    SecurityContextHolder.clearContext();
                    done.countDown();
                }
            }, "order-worker-" + i);
            worker.start();
        }

        assertThat(ready.await(20, TimeUnit.SECONDS)).isTrue();
        fire.countDown();
        assertThat(done.await(120, TimeUnit.SECONDS)).as("50 个线程应在 120 秒内全部结束").isTrue();

        long success = codes.stream().filter(code -> code == ErrorCode.SUCCESS.getCode()).count();
        long conflict = codes.stream().filter(code -> code == ErrorCode.OCCUPANCY_CONFLICT.getCode()).count();
        long lockFailed = codes.stream().filter(code -> code == ErrorCode.LOCK_ACQUIRE_FAILED.getCode()).count();
        long systemError = codes.stream().filter(code -> code == ErrorCode.SYSTEM_ERROR.getCode()).count();

        assertThat(success).as("同一时段只允许 1 单成功").isEqualTo(1L);
        assertThat(conflict).as("其余必须是 4090").isEqualTo(THREADS - 1L);
        assertThat(lockFailed).as("抢锁失败必须翻译成 4090，不许漏成 5003").isZero();
        assertThat(systemError).as("不允许出现 5000").isZero();
        assertThat(orderIds).hasSize(1);

        Long orderId = Long.valueOf(orderIds.peek());
        List<OrderOccupancy> rows = orderOccupancyMapper.selectList(
                Wrappers.<OrderOccupancy>lambdaQuery().eq(OrderOccupancy::getOrderId, orderId));
        assertThat(rows).as("占用按天展开").hasSize(3);
        assertThat(rows).extracting(OrderOccupancy::getOccupyDate)
                .containsExactlyInAnyOrder(start, start.plusDays(1), start.plusDays(2));

        List<OrderOccupancy> allRows = orderOccupancyMapper.selectList(
                Wrappers.<OrderOccupancy>lambdaQuery()
                        .eq(OrderOccupancy::getItemId, ITEM_ID)
                        .ge(OrderOccupancy::getOccupyDate, start)
                        .lt(OrderOccupancy::getOccupyDate, end));
        assertThat(allRows).as("该时段内占用行数 == 订单天数，无重复").hasSize(3);
        assertThat(allRows.stream().map(OrderOccupancy::getOccupyDate).distinct().count()).isEqualTo(3);
    }
}
