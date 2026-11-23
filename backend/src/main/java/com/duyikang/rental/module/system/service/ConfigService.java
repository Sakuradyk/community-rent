package com.duyikang.rental.module.system.service;

import com.duyikang.rental.module.system.vo.AdminConfigVO;

import java.util.Map;

/**
 * 参数配置读写（管理端）。
 *
 * <p>设计要点：**后端不做配置缓存**，每次业务读库 —— 这样"改完立即生效"是自然结果，
 * 不需要 `@RefreshScope`（本项目未引 spring-cloud，见 `PROJECT_RULES.md` §2.1 与 §9）。
 * 代价是每个策略读一次单行表，量级可忽略。</p>
 */
public interface ConfigService {

    /** 三张策略表 + sys_config 全量。 */
    AdminConfigVO load();

    /**
     * 改一张策略表。
     *
     * @param type {@code credit / fee / loss}
     * @param body 只提交要改的字段（字段白名单之外的一律忽略，不做静默补默认值）
     * @return 改完后的该表实体
     */
    Object updatePolicy(String type, Map<String, Object> body);

    /**
     * 改一个 sys_config 键（只允许已存在的键，避免管理端随手造出无消费方的参数）。
     *
     * @param key   配置键
     * @param value 新值（按该键的 valueType 校验）
     */
    AdminConfigVO.SysItem updateSys(String key, String value);
}
