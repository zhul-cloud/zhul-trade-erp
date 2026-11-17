package com.zhul.erp.modules.aitask.service;

import com.zhul.erp.modules.aitask.dto.AiCallConfig;
import com.zhul.erp.modules.aitask.dto.AiConfigVO;
import com.zhul.erp.modules.aitask.dto.AiConnectionTestVO;
import com.zhul.erp.modules.aitask.dto.AiModelListVO;
import com.zhul.erp.modules.aitask.dto.AiSubscriptionStatusVO;
import com.zhul.erp.modules.aitask.dto.SaveAiConfigRequest;
import com.zhul.erp.modules.aitask.dto.SaveAiSettingsRequest;

import java.util.List;

/**
 * 租户 AI 配置：各连接方式的设置、顺序与开关，按顺序自动切换；平台订阅按套餐授权给公司。
 * 见 openspec ai-platform/tenant-ai-settings、ai-platform/connection-failover
 */
public interface AiConfigService {

    /** 当前租户的连接方式卡片（按使用顺序；Key 只返回脱敏值；订阅组只在有权使用时返回） */
    AiConfigVO get();

    /** 保存顺序与开关、以及改过的连接方式设置；apiKey 留空表示沿用该服务商已保存的 Key；保存的方式解除暂停 */
    void save(SaveAiSettingsRequest req);

    /**
     * 当前租户提交 AI 任务用的有序连接方式列表（配置快照，编排服务按顺序使用、出问题时换下一个）：
     * 没有已配置且启用的方式时抛 AiNotConfiguredException；有但都不可用（订阅未登录、暂停中）时抛 AiUnavailableException
     */
    List<AiCallConfig> resolveChainForCurrentTenant();

    /**
     * 当前租户能否提交 AI 任务（规则同 resolveChainForCurrentTenant，但不抛异常）。
     * 业务保存时顺带触发的 AI 任务先用它判断：在外层事务里调用 createAndSubmit 抛出的异常
     * 即使被捕获，也会把整个事务标记为只能回滚，导致业务保存失败。
     */
    boolean isConfiguredForCurrentTenant();

    /** 用这张卡片当前填写的值测试连接，不保存；测试通过时解除这个方式的暂停 */
    AiConnectionTestVO test(SaveAiConfigRequest req);

    /** 用页面当前填写的值获取模型下拉列表 */
    AiModelListVO models(SaveAiConfigRequest req);

    /** 平台订阅状态：平台得到登录状态与登录命令，获授权租户只得到可用与否，其他租户拒绝 */
    AiSubscriptionStatusVO subscriptionStatus();
}
