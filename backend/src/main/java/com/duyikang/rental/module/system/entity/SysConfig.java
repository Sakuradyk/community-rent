package com.duyikang.rental.module.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统参数实体（键值）。放异构零散参数：推荐权重/衰减半衰期/热度窗口/锁 TTL/扫描 cron。值统一按字符串存，按 valueType 解析。
 */
@Data
@TableName("sys_config")
public class SysConfig {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 配置键，如 recommend.w.cf
     */
    private String configKey;

    /**
     * 配置值，统一按字符串存，按 valueType 解析
     */
    private String configValue;

    /**
     * STRING / INT / DECIMAL / CRON
     */
    private String valueType;

    /**
     * 分组：recommend / order / sys
     */
    private String configGroup;

    /**
     * 说明
     */
    private String description;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
