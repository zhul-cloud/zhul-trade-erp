package com.zhul.erp.modules.aitask.service;

import com.zhul.erp.modules.aitask.dto.AiCallConfig;
import com.zhul.erp.modules.aitask.dto.AiConfigVO;
import com.zhul.erp.modules.aitask.dto.AiConnectionTestVO;
import com.zhul.erp.modules.aitask.dto.AiModelListVO;
import com.zhul.erp.modules.aitask.dto.AiSubscriptionStatusVO;
import com.zhul.erp.modules.aitask.dto.SaveAiConfigRequest;

/** 租户 AI 配置：当前选择 + 各连接方式的设置；平台订阅按套餐授权给公司，见 openspec ai-platform/tenant-ai-settings */
public interface AiConfigService {

    /** 当前租户的配置（Key 只返回脱敏值；平台订阅设置只在有权使用时返回） */
    AiConfigVO get();

    /** 保存当前选择与这一种连接方式的设置；apiKey 留空表示沿用该服务商已保存的 Key */
    void save(SaveAiConfigRequest req);

    /**
     * 当前租户提交 AI 任务用的配置快照：未配置（或 API 方式无 Key）时抛 AiNotConfiguredException；
     * 选用的平台订阅未授权或不可用时抛 AiUnavailableException
     */
    AiCallConfig resolveForCurrentTenant();

    /**
     * 当前租户能否提交 AI 任务（规则同 resolveForCurrentTenant，但不抛异常）。
     * 业务保存时顺带触发的 AI 任务先用它判断：在外层事务里调用 createAndSubmit 抛出的异常
     * 即使被捕获，也会把整个事务标记为只能回滚，导致业务保存失败。
     */
    boolean isConfiguredForCurrentTenant();

    /** 用页面当前填写的值测试连接，不保存 */
    AiConnectionTestVO test(SaveAiConfigRequest req);

    /** 用页面当前填写的值获取模型下拉列表 */
    AiModelListVO models(SaveAiConfigRequest req);

    /** 平台订阅状态：平台得到登录状态与登录命令，获授权租户只得到可用与否，其他租户拒绝 */
    AiSubscriptionStatusVO subscriptionStatus();
}
