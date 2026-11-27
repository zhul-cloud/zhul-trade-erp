package com.zhul.erp.modules.video.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.product.support.PlatformScopeGuard;
import com.zhul.erp.modules.system.entity.SysConfigDO;
import com.zhul.erp.modules.system.repository.SysConfigMapper;
import com.zhul.erp.modules.video.constants.VideoConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 夜间生成时段：全服务器一个设置（平台模板 tenant_id = 0），只有平台账号能改 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VideoSettings {

    private final SysConfigMapper sysConfigMapper;
    private final PlatformScopeGuard platform;

    public VideoWindow window() {
        SysConfigDO row = row();
        try {
            return VideoWindow.parse(row == null ? VideoConstants.DEFAULT_WINDOW : row.getConfigValue());
        } catch (BizException e) {
            log.warn("视频生成时段设置无效，按默认 {} 处理：{}", VideoConstants.DEFAULT_WINDOW, row.getConfigValue());
            return VideoWindow.parse(VideoConstants.DEFAULT_WINDOW);
        }
    }

    public VideoWindow saveWindow(String text) {
        if (!platform.isPlatform()) {
            throw new BizException("只有平台账号能修改生成时段");
        }
        VideoWindow w = VideoWindow.parse(text);
        SysConfigDO row = row();
        if (row == null) {
            row = new SysConfigDO();
            row.setTenantId(0);
            row.setConfigKey(VideoConstants.CONFIG_WINDOW);
            row.setConfigName("视频夜间生成时段");
            row.setConfigValue(w.text());
            row.setConfigType("STRING");
            row.setIsBuiltin(1);
            row.setIsEncrypted(0);
            row.setConfigGroup("video");
            row.setRemark("只在这个时段内开始生成视频，可以跨零点（北京时间）；只有平台账号能修改");
            sysConfigMapper.insert(row);
        } else {
            row.setConfigValue(w.text());
            sysConfigMapper.updateById(row);
        }
        log.info("视频生成时段改为 {}", w.text());
        return w;
    }

    private SysConfigDO row() {
        return sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfigDO>()
                .eq(SysConfigDO::getTenantId, 0)
                .eq(SysConfigDO::getConfigKey, VideoConstants.CONFIG_WINDOW)
                .isNull(SysConfigDO::getDeletedAt)
                .last("LIMIT 1"));
    }
}
