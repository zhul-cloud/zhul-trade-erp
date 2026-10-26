package com.zhul.erp.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.ResultCode;
import com.zhul.erp.common.utils.AesUtils;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.system.dto.AppearanceVO;
import com.zhul.erp.modules.system.dto.ConfigGroupCountVO;
import com.zhul.erp.modules.system.dto.ConfigVO;
import com.zhul.erp.modules.system.dto.SaveConfigRequest;
import com.zhul.erp.modules.system.entity.SysConfigDO;
import com.zhul.erp.modules.system.repository.SysConfigMapper;
import com.zhul.erp.modules.system.service.ConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import com.zhul.erp.framework.storage.StorageArea;
import com.zhul.erp.framework.storage.FileStorageService;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigServiceImpl implements ConfigService {

    private static final String MASK = "****";
    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;
    /**
     * 外观：平台统一（tenant_id=0），登录页在鉴权前读取，不按租户区分。
     * 租户看不到这些键，也不能修改（否则会改掉所有租户的外观）。
     */
    private static final List<String> APPEARANCE_KEYS = List.of(
            "sys.site.name", "sys.site.subtitle", "sys.site.logo", "sys.login.background", "sys.login.footer");
    private static final Set<String> GLOBAL_APPEARANCE_KEYS = Set.copyOf(APPEARANCE_KEYS);
    /** 外观配置为空时的默认值，与迁移 V1.3.14 写入的默认值一致 */
    private static final String DEFAULT_SITE_NAME = "烛龙ERP";
    private static final String DEFAULT_SITE_SUBTITLE = "烛龙外贸业财一体化中台";
    private static final String DEFAULT_LOGIN_FOOTER = "© 2026 烛龙科技";
    /** 自定义配置可选的分组代码（与前端 groupLabel.ts 一致）；内置配置的分组由迁移写入模块代码 */
    private static final Set<String> CUSTOM_GROUPS = Set.of("basic", "security", "mail", "custom");
    private static final Map<String, String> ALLOWED_IMAGE_TYPES = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/webp", "webp",
            "image/gif", "gif",
            "image/svg+xml", "svg"
    );

    private final SysConfigMapper sysConfigMapper;
    private final AesUtils aesUtils;
    private final ObjectMapper objectMapper;
    private final FileStorageService fileStorage;


    @Override
    public PageResult<ConfigVO> listAll(Integer page, Integer pageSize, String keyword, String group) {
        List<SysConfigDO> merged = loadMergedActive();

        if (StringUtils.hasText(keyword)) {
            String kw = keyword.toLowerCase();
            merged = merged.stream()
                    .filter(c -> c.getConfigKey().toLowerCase().contains(kw)
                            || c.getConfigName().toLowerCase().contains(kw))
                    .collect(Collectors.toList());
        }
        if (StringUtils.hasText(group)) {
            merged = merged.stream().filter(c -> group.equals(c.getConfigGroup())).collect(Collectors.toList());
        }
        merged.sort((a, b) -> {
            int cmp = a.getConfigGroup().compareTo(b.getConfigGroup());
            return cmp != 0 ? cmp : b.getUpdateTime().compareTo(a.getUpdateTime());
        });

        long total = merged.size();
        int from = Math.min((page - 1) * pageSize, merged.size());
        int to = Math.min(from + pageSize, merged.size());
        List<ConfigVO> records = toVoList(merged.subList(from, to));
        return PageResult.of(total, records);
    }

    @Override
    public List<ConfigGroupCountVO> groupCounts() {
        List<SysConfigDO> merged = loadMergedActive();
        Map<String, Long> counts = merged.stream()
                .collect(Collectors.groupingBy(SysConfigDO::getConfigGroup, LinkedHashMap::new, Collectors.counting()));
        List<ConfigGroupCountVO> result = new ArrayList<>();
        for (Map.Entry<String, Long> e : counts.entrySet()) {
            result.add(new ConfigGroupCountVO(e.getKey(), e.getValue()));
        }
        return result;
    }

    private List<SysConfigDO> loadMergedActive() {
        int tenantId = effectiveTenantId();
        List<SysConfigDO> all = sysConfigMapper.selectList(
                new LambdaQueryWrapper<SysConfigDO>()
                        .isNull(SysConfigDO::getDeletedAt)
                        .and(w -> w.eq(SysConfigDO::getTenantId, tenantId).or().eq(SysConfigDO::getTenantId, 0)));

        // 模板（tenant_id=0）优先放入，租户专属覆盖行后放入以覆盖同 key 的模板值
        Map<String, SysConfigDO> merged = new LinkedHashMap<>();
        all.stream().filter(c -> c.getTenantId() == 0).forEach(c -> merged.put(c.getConfigKey(), c));
        all.stream().filter(c -> c.getTenantId() != 0).forEach(c -> merged.put(c.getConfigKey(), c));
        if (tenantId != 0) {
            // 外观由平台统一管理，租户的列表与分组计数里不出现
            GLOBAL_APPEARANCE_KEYS.forEach(merged::remove);
        }
        return new ArrayList<>(merged.values());
    }

    private int effectiveTenantId() {
        Integer tenantId = TenantContext.getTenantId();
        return tenantId != null ? tenantId : 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(SaveConfigRequest req) {
        if (req.getConfigKey().startsWith("sys.")) {
            throw new BizException("sys. 为系统保留前缀，自定义配置请使用其他前缀");
        }
        if (!CUSTOM_GROUPS.contains(req.getConfigGroup())) {
            throw new BizException("配置分组不合法，只能选择基础、安全、邮件或其他");
        }
        int tenantId = effectiveTenantId();
        long dup = sysConfigMapper.selectCount(new LambdaQueryWrapper<SysConfigDO>()
                .isNull(SysConfigDO::getDeletedAt)
                .eq(SysConfigDO::getTenantId, tenantId)
                .eq(SysConfigDO::getConfigKey, req.getConfigKey()));
        if (dup > 0) {
            throw new BizException("配置键已存在");
        }
        validateValueByType(req.getConfigType(), req.getConfigValue());

        SysConfigDO config = new SysConfigDO();
        config.setTenantId(tenantId);
        config.setConfigKey(req.getConfigKey());
        config.setConfigName(req.getConfigName());
        config.setConfigType(req.getConfigType());
        config.setConfigGroup(req.getConfigGroup());
        config.setIsBuiltin(0);
        config.setIsEncrypted(req.getIsEncrypted() != null && req.getIsEncrypted() == 1 ? 1 : 0);
        config.setRemark(req.getRemark());
        config.setConfigValue(config.getIsEncrypted() == 1 ? aesUtils.encrypt(req.getConfigValue()) : req.getConfigValue());
        sysConfigMapper.insert(config);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateValue(Integer id, String value) {
        SysConfigDO config = sysConfigMapper.selectById(id);
        if (config == null || config.getDeletedAt() != null) {
            throw new BizException("配置不存在");
        }

        int tenantId = effectiveTenantId();
        boolean appearance = GLOBAL_APPEARANCE_KEYS.contains(config.getConfigKey());
        if (appearance && tenantId != 0) {
            throw new BizException(ResultCode.FORBIDDEN, "外观由平台统一管理");
        }

        if (config.getIsEncrypted() == 1 && !StringUtils.hasText(value)) {
            // 加密配置留空视为不修改
            return;
        }
        // 外观的图片地址（Logo、登录页背景）允许清空，清空后显示默认样式
        if (!(appearance && !StringUtils.hasText(value))) {
            validateValueByType(config.getConfigType(), value);
        }
        String storedValue = config.getIsEncrypted() == 1 ? aesUtils.encrypt(value) : (value == null ? "" : value);

        if (config.getTenantId() == 0 && tenantId != 0) {
            // 租户编辑内置模板：形成租户独立副本，模板本身不受影响
            SysConfigDO override = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfigDO>()
                    .isNull(SysConfigDO::getDeletedAt)
                    .eq(SysConfigDO::getTenantId, tenantId)
                    .eq(SysConfigDO::getConfigKey, config.getConfigKey())
                    .last("LIMIT 1"));
            if (override != null) {
                override.setConfigValue(storedValue);
                sysConfigMapper.updateById(override);
            } else {
                SysConfigDO copy = new SysConfigDO();
                copy.setTenantId(tenantId);
                copy.setConfigKey(config.getConfigKey());
                copy.setConfigName(config.getConfigName());
                copy.setConfigType(config.getConfigType());
                copy.setConfigGroup(config.getConfigGroup());
                copy.setIsBuiltin(1);
                copy.setIsEncrypted(config.getIsEncrypted());
                copy.setRemark(config.getRemark());
                copy.setConfigValue(storedValue);
                sysConfigMapper.insert(copy);
            }
            return;
        }

        config.setConfigValue(storedValue);
        sysConfigMapper.updateById(config);
    }

    private void validateValueByType(String type, String value) {
        if (type == null) return;
        switch (type) {
            case "INTEGER":
                try {
                    Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    throw new BizException("配置值必须为整数");
                }
                break;
            case "BOOLEAN":
                if (!"true".equals(value) && !"false".equals(value)) {
                    throw new BizException("配置值必须为 true 或 false");
                }
                break;
            case "JSON":
                try {
                    objectMapper.readTree(value);
                } catch (Exception e) {
                    throw new BizException("JSON 格式不合法");
                }
                break;
            case "URL":
                if (value == null || !value.matches("^(https?://|/).*")) {
                    throw new BizException("请输入合法的 URL 地址");
                }
                break;
            case "STRING":
            default:
                if (value != null && value.length() > 4096) {
                    throw new BizException("配置值最长4096字符");
                }
                break;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Integer id) {
        SysConfigDO config = sysConfigMapper.selectById(id);
        if (config == null || config.getDeletedAt() != null) {
            throw new BizException("配置不存在");
        }
        if (config.getIsBuiltin() != null && config.getIsBuiltin() == 1) {
            throw new BizException("系统内置配置不可删除");
        }
        config.setDeletedAt(LocalDateTime.now());
        sysConfigMapper.updateById(config);
    }

    @Override
    public String uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择要上传的图片文件");
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new BizException("图片大小不能超过5MB");
        }
        String contentType = file.getContentType();
        String ext = ALLOWED_IMAGE_TYPES.get(contentType);
        if (ext == null) {
            throw new BizException("仅支持 PNG/JPEG/WEBP/GIF/SVG 格式的图片");
        }

        Integer tenantId = TenantContext.getTenantId();
        FileStorageService.StoredFile stored = fileStorage.storeValidated(StorageArea.SYSTEM, tenantId == null ? 0 : tenantId, file, ext);
        return fileStorage.publicUrl(stored.storage(), stored.fileKey());
    }

    @Override
    public AppearanceVO getPublicAppearance() {
        // 登录页在鉴权前访问，暂不支持按租户子域名解析（见登录PRD默认假设1，架构待定），
        // 统一读取平台级模板（tenant_id=0）配置
        List<SysConfigDO> rows = sysConfigMapper.selectList(new LambdaQueryWrapper<SysConfigDO>()
                .eq(SysConfigDO::getTenantId, 0)
                .in(SysConfigDO::getConfigKey, APPEARANCE_KEYS)
                .isNull(SysConfigDO::getDeletedAt));
        Map<String, String> map = new LinkedHashMap<>();
        for (SysConfigDO row : rows) {
            if (StringUtils.hasText(row.getConfigValue())) {
                map.put(row.getConfigKey(), row.getConfigValue().trim());
            }
        }

        AppearanceVO vo = new AppearanceVO();
        vo.setSiteName(map.getOrDefault("sys.site.name", DEFAULT_SITE_NAME));
        vo.setSiteSubtitle(map.getOrDefault("sys.site.subtitle", DEFAULT_SITE_SUBTITLE));
        vo.setLogoUrl(map.get("sys.site.logo"));
        vo.setLoginBackgroundUrl(map.get("sys.login.background"));
        vo.setLoginFooter(map.getOrDefault("sys.login.footer", DEFAULT_LOGIN_FOOTER));
        return vo;
    }

    private List<ConfigVO> toVoList(List<SysConfigDO> list) {
        List<ConfigVO> result = new ArrayList<>(list.size());
        for (SysConfigDO config : list) {
            ConfigVO vo = new ConfigVO();
            vo.setId(config.getId());
            vo.setConfigKey(config.getConfigKey());
            vo.setConfigName(config.getConfigName());
            vo.setConfigValue(config.getIsEncrypted() != null && config.getIsEncrypted() == 1
                    ? MASK : config.getConfigValue());
            vo.setConfigType(config.getConfigType());
            vo.setIsBuiltin(config.getIsBuiltin());
            vo.setIsEncrypted(config.getIsEncrypted());
            vo.setConfigGroup(config.getConfigGroup());
            vo.setRemark(config.getRemark());
            vo.setCreateBy(config.getCreateBy());
            vo.setCreateTime(config.getCreateTime());
            vo.setUpdateBy(config.getUpdateBy());
            vo.setUpdateTime(config.getUpdateTime());
            result.add(vo);
        }
        return result;
    }
}
