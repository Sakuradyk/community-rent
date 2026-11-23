package com.duyikang.rental.module.system;

import com.duyikang.rental.common.constant.ErrorCode;
import com.duyikang.rental.common.exception.BizException;
import com.duyikang.rental.module.credit.entity.CreditPolicy;
import com.duyikang.rental.module.system.service.ConfigService;
import com.duyikang.rental.module.system.vo.AdminConfigVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 参数配置集成测试（切片 6）。
 *
 * <p>@Transactional 回滚保证不会把测试改的值留在库里（配置是全系统共享的，脏数据会让别的测试崩）。</p>
 */
@SpringBootTest
@Transactional
class ConfigServiceTest {

    private static final String CREDIT = "credit";
    private static final String FEE = "fee";
    private static final String LOSS = "loss";

    @Autowired
    private ConfigService configService;

    @Test
    @DisplayName("读取：三张策略表都在，sys_config 至少 13 项")
    void load_returnsPoliciesAndSysConfig() {
        AdminConfigVO config = configService.load();

        assertThat(config.creditPolicy()).isNotNull();
        assertThat(config.feePolicy()).isNotNull();
        assertThat(config.lossPolicy()).isNotNull();
        assertThat(config.sysConfig()).hasSizeGreaterThanOrEqualTo(13);
        assertThat(config.sysConfig()).allSatisfy(item -> {
            assertThat(item.key()).isNotBlank();
            assertThat(item.value()).isNotNull();
            assertThat(item.valueType()).isNotBlank();
        });
    }

    @Test
    @DisplayName("改信誉策略：只提交 initScore 时其它字段保持原值")
    void updatePolicy_credit_onlyTouchesGivenField() {
        CreditPolicy before = (CreditPolicy) configService.load().creditPolicy();

        Map<String, Object> body = new HashMap<>();
        body.put("initScore", 79);
        CreditPolicy after = (CreditPolicy) configService.updatePolicy(CREDIT, body);

        assertThat(after.getInitScore()).isEqualTo(79);
        assertThat(after.getMaxScore()).isEqualTo(before.getMaxScore());
        assertThat(after.getTierFreeDepositMin()).isEqualTo(before.getTierFreeDepositMin());
    }

    @Test
    @DisplayName("信誉策略越界：initScore 超出 [minScore, maxScore] → 2001")
    void updatePolicy_credit_initScoreOutOfRange_paramError() {
        Map<String, Object> body = new HashMap<>();
        body.put("initScore", 999);

        assertThatThrownBy(() -> configService.updatePolicy(CREDIT, body))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode()));
    }

    @Test
    @DisplayName("损耗策略越界：检修阈值 ≤ 保养阈值 → 2001（0.6 / 0.85 的先后关系不能反）")
    void updatePolicy_loss_thresholdInverted_paramError() {
        Map<String, Object> body = new HashMap<>();
        body.put("thresholdRepair", new BigDecimal("0.5000"));

        assertThatThrownBy(() -> configService.updatePolicy(LOSS, body))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode()));
    }

    @Test
    @DisplayName("未知策略类型 → 2001")
    void updatePolicy_unknownType_paramError() {
        assertThatThrownBy(() -> configService.updatePolicy("unknown", new HashMap<>()))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode()));
    }

    @Test
    @DisplayName("改 sys_config：合法值写回并规范化（true → 1）")
    void updateSys_boolean_normalized() {
        AdminConfigVO.SysItem item = configService.updateSys("community.priority.enabled", "true");

        assertThat(item.key()).isEqualTo("community.priority.enabled");
        assertThat(item.value()).isEqualTo("1");
    }

    @Test
    @DisplayName("改 sys_config：未知键 → 2001（禁止凭空造参数）")
    void updateSys_unknownKey_paramError() {
        assertThatThrownBy(() -> configService.updateSys("foo.bar", "1"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode()));
    }

    @Test
    @DisplayName("改 sys_config：权重越界 1.5 → 2001")
    void updateSys_weightOutOfRange_paramError() {
        assertThatThrownBy(() -> configService.updateSys("recommend.w.cf", "1.5"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode()));
    }

    @Test
    @DisplayName("改 sys_config：INT 越界（top_n = 30）→ 2001")
    void updateSys_intOutOfRange_paramError() {
        assertThatThrownBy(() -> configService.updateSys("recommend.top_n", "30"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode()));
    }

    @Test
    @DisplayName("改 sys_config：cron 非法 → 2001")
    void updateSys_invalidCron_paramError() {
        assertThatThrownBy(() -> configService.updateSys("order.scan.cron", "not-a-cron"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode()));
    }
}
