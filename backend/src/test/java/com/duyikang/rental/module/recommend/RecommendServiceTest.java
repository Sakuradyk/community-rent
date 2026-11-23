package com.duyikang.rental.module.recommend;

import com.duyikang.rental.common.constant.ErrorCode;
import com.duyikang.rental.common.exception.BizException;
import com.duyikang.rental.module.item.entity.Item;
import com.duyikang.rental.module.item.mapper.ItemMapper;
import com.duyikang.rental.module.recommend.service.RecommendService;
import com.duyikang.rental.module.recommend.vo.RecommendItemVO;
import com.duyikang.rental.module.recommend.vo.RecommendResultVO;
import com.duyikang.rental.security.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 推荐服务集成测试（切片 6，M5）。
 *
 * <p>断言的都是"可解释 + 不越权"这两件事，不锁死具体分数（权重可配置，锁分数会让改配置就把测试改崩）。</p>
 */
@SpringBootTest
@Transactional
class RecommendServiceTest {

    private static final Long ZHANG_WEI = 2L;
    private static final String ZHANG_WEI_NAME = "张伟";
    private static final String ROLE_USER = "USER";

    private static final Set<String> HISTORICAL_REASONS = Set.of("CF", "CONTENT", "HEAT");
    private static final String COLD_START = "COLD_START";

    @Autowired
    private RecommendService recommendService;

    @Autowired
    private ItemMapper itemMapper;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("匿名访问：走冷启动，理由统一为 COLD_START，且不超过 limit")
    void recommend_anonymous_isColdStart() {
        RecommendResultVO result = recommendService.recommend(4);

        assertThat(result.coldStart()).isTrue();
        assertThat(result.limit()).isEqualTo(4);
        assertThat(result.list()).hasSizeLessThanOrEqualTo(4);
        assertThat(result.list()).isNotEmpty();
        assertThat(result.list()).allSatisfy(item -> {
            assertThat(item.reasonCode()).isEqualTo(COLD_START);
            assertThat(item.reason()).isNotBlank();
        });
        // 权重三件套必须回传，前端要展示"当前算法参数"，也是可解释性的一部分
        assertThat(result.weights()).isNotNull();
        assertThat(result.weights().cf()).isNotNull();
        assertThat(result.weights().content()).isNotNull();
        assertThat(result.weights().heat()).isNotNull();
    }

    @Test
    @DisplayName("有历史行为：不再冷启动，理由来自 CF/内容/热度，且排除自己发布的物品")
    void recommend_withHistory_hasReasonAndExcludesOwnItems() {
        loginAs(ZHANG_WEI, ZHANG_WEI_NAME, ROLE_USER);

        RecommendResultVO result = recommendService.recommend(6);

        assertThat(result.coldStart()).isFalse();
        assertThat(result.list()).isNotEmpty();
        assertThat(result.list()).allSatisfy(item ->
                assertThat(HISTORICAL_REASONS).contains(item.reasonCode()));

        List<Item> ownItems = itemMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<Item>lambdaQuery()
                        .eq(Item::getOwnerId, ZHANG_WEI));
        Set<Long> ownIds = ownItems.stream().map(Item::getId).collect(java.util.stream.Collectors.toSet());
        assertThat(result.list()).allSatisfy(item -> assertThat(ownIds).doesNotContain(Long.valueOf(item.id())));
    }

    @Test
    @DisplayName("排序稳定：score 非递增，同分时 rentCount 更大者在前")
    void recommend_list_sortedByScoreDesc() {
        loginAs(ZHANG_WEI, ZHANG_WEI_NAME, ROLE_USER);

        List<RecommendItemVO> list = recommendService.recommend(10).list();

        for (int i = 1; i < list.size(); i++) {
            var prev = list.get(i - 1);
            var curr = list.get(i);
            assertThat(prev.score()).isGreaterThanOrEqualTo(curr.score());
            if (prev.score().compareTo(curr.score()) == 0) {
                assertThat(prev.rentCount()).isGreaterThanOrEqualTo(curr.rentCount());
            }
        }
    }

    @Test
    @DisplayName("limit 越界（0 / 21）→ 2001")
    void recommend_limitOutOfRange_throwsParamError() {
        assertThatThrownBy(() -> recommendService.recommend(0))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode()));
        assertThatThrownBy(() -> recommendService.recommend(21))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode()));
    }

    private void loginAs(Long userId, String nickname, String role) {
        LoginUser user = new LoginUser(userId, nickname, role);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}
