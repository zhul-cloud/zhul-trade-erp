package com.zhul.erp.modules.website.service;

import org.springframework.context.ApplicationEventPublisher;
import com.zhul.erp.modules.website.support.SiteContentChanged;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.ResultCode;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.framework.tenant.TenantScope;
import com.zhul.erp.modules.listing.service.ListingTaskSync;
import com.zhul.erp.modules.product.entity.ProductCategoryDO;
import com.zhul.erp.modules.product.repository.ProductCategoryMapper;
import com.zhul.erp.modules.website.constants.WebsiteConstants;
import com.zhul.erp.modules.website.dto.CategoryMapRequest;
import com.zhul.erp.modules.website.dto.CategoryMapVO;
import com.zhul.erp.modules.website.dto.CheckItemVO;
import com.zhul.erp.modules.website.dto.SecretVO;
import com.zhul.erp.modules.website.dto.SiteLanguage;
import com.zhul.erp.modules.website.dto.SiteRequest;
import com.zhul.erp.modules.website.dto.SiteScope;
import com.zhul.erp.modules.website.dto.SiteVO;
import com.zhul.erp.modules.website.entity.WebsiteCategoryMapDO;
import com.zhul.erp.modules.website.entity.WebsiteSiteDO;
import com.zhul.erp.modules.website.repository.WebsiteCategoryMapMapper;
import com.zhul.erp.modules.website.repository.WebsiteSiteMapper;
import com.zhul.erp.modules.website.support.CategoryResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 站点（spec product/website-site）：新建、修改、停用、检测连接、品类对照。
 * 网址全平台唯一，校验用 TenantScope.platform 跨公司查询；凭据加密保存，只返回是否已配置与末四位。
 * 事务边界：保存站点、凭据，以及随之重新对齐的发品任务（新建、启用停用、商品范围变化）在同一事务；检测连接只读，不开事务（同步调用发布服务，可能持续数分钟）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebsiteSiteService {

    private static final Pattern URL = Pattern.compile("^https://[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+(:\\d{1,5})?$");
    private static final Pattern PREFIX = Pattern.compile("^(/[a-z]{2,5})?$");
    private static final Pattern REPO = Pattern.compile("^https://[^\\s@]+$");
    private static final Pattern BRANCH = Pattern.compile("^[\\w./-]{1,100}$");
    private static final Pattern HOST_OR_USER = Pattern.compile("^[\\w.-]{1,100}$");
    private static final Pattern DEPLOY_PATH = Pattern.compile("^[^\\s;&|`$]{1,300}$");
    private static final Pattern SITE_CATEGORY = Pattern.compile("^[a-z0-9][a-z0-9_-]{0,63}$");
    private static final BigDecimal MIN_FACTOR = new BigDecimal("0.5");
    private static final BigDecimal MAX_FACTOR = BigDecimal.TEN;
    private static final BigDecimal DEFAULT_FACTOR = new BigDecimal("1.05");
    private static final Map<String, String> CHECK_NAMES = Map.of("repo", "代码仓库", "adapter", "适配脚本",
            "deploy", "部署服务器", "url", "网址");
    private static final List<String> CHECK_ORDER = List.of("repo", "adapter", "deploy", "url");

    private final WebsiteSiteMapper siteMapper;
    private final WebsiteCategoryMapMapper categoryMapMapper;
    private final ProductCategoryMapper categoryMapper;
    private final SiteSecrets secrets;
    private final SitePublisherClient publisher;
    private final ObjectMapper objectMapper;
    private final ListingTaskSync listingSync;
    private final ApplicationEventPublisher events;

    // ================================================================ 查询

    /** 本公司的站点，按更新时间倒序（站点数量少，不分页） */
    public List<SiteVO> list() {
        List<SiteVO> out = new ArrayList<>();
        for (WebsiteSiteDO s : siteMapper.selectList(new LambdaQueryWrapper<WebsiteSiteDO>()
                .isNull(WebsiteSiteDO::getDeletedAt).orderByDesc(WebsiteSiteDO::getUpdateTime))) {
            out.add(toVO(s));
        }
        return out;
    }

    public SiteVO detail(Long id) {
        return toVO(get(id));
    }

    /** 本公司的站点；其他公司的或已删除的视为不存在（租户拦截器已限定本公司） */
    public WebsiteSiteDO get(Long id) {
        WebsiteSiteDO s = id == null ? null : siteMapper.selectById(id);
        if (s == null || s.getDeletedAt() != null) {
            throw new BizException(ResultCode.NOT_FOUND, "站点不存在");
        }
        return s;
    }

    // ================================================================ 保存

    @Transactional(rollbackFor = Exception.class)
    public SiteVO create(SiteRequest req) {
        WebsiteSiteDO s = new WebsiteSiteDO();
        s.setTenantId(TenantContext.getTenantId());
        s.setTakeoverStatus(WebsiteConstants.TAKEOVER_PENDING);
        s.setStatus(WebsiteConstants.STATUS_ENABLED);
        s.setCurrencyCode(WebsiteConstants.CURRENCY);
        apply(s, req, null);
        siteMapper.insert(s);
        saveSecrets(s, req);
        listingSync.syncAll(s.getTenantId());
        log.info("新建站点，siteId={}，url={}", s.getId(), s.getUrl());
        return toVO(s);
    }

    @Transactional(rollbackFor = Exception.class)
    public SiteVO update(Long id, SiteRequest req) {
        WebsiteSiteDO s = get(id);
        String scopeBefore = s.getProductScope();
        apply(s, req, id);
        siteMapper.updateById(s);
        saveSecrets(s, req);
        if (!Objects.equals(scopeBefore, s.getProductScope())) {
            listingSync.syncAll(s.getTenantId());
        }
        log.info("修改站点，siteId={}", id);
        // 非正品名单、链接规则等改了：这个站点已发布的商品重新判断「内容有更新」
        events.publishEvent(SiteContentChanged.site(id));
        return toVO(get(id));
    }

    /** 启用或停用；停用后不能发布，已发布的商品留在站点上 */
    @Transactional(rollbackFor = Exception.class)
    public SiteVO setStatus(Long id, Integer status) {
        if (!Objects.equals(status, WebsiteConstants.STATUS_ENABLED) && !Objects.equals(status, WebsiteConstants.STATUS_DISABLED)) {
            throw new BizException("状态不正确");
        }
        WebsiteSiteDO s = get(id);
        boolean changed = !Objects.equals(s.getStatus(), status);
        s.setStatus(status);
        siteMapper.updateById(s);
        if (changed) {
            listingSync.syncAll(s.getTenantId());
        }
        log.info("站点{}，siteId={}", status == WebsiteConstants.STATUS_ENABLED ? "启用" : "停用", id);
        return toVO(get(id));
    }

    /** 把请求校验后写入实体；凭据另存。selfId 为修改时的站点 id */
    private void apply(WebsiteSiteDO s, SiteRequest req, Long selfId) {
        if (req == null) {
            throw new BizException("缺少站点信息");
        }
        String name = trim(req.getName());
        if (name.isEmpty() || name.length() > WebsiteConstants.MAX_NAME) {
            throw new BizException("名称必填，最长 " + WebsiteConstants.MAX_NAME + " 字");
        }
        if (siteMapper.selectCount(new LambdaQueryWrapper<WebsiteSiteDO>().eq(WebsiteSiteDO::getName, name)
                .isNull(WebsiteSiteDO::getDeletedAt).ne(selfId != null, WebsiteSiteDO::getId, selfId)) > 0) {
            throw new BizException("本公司已有同名站点");
        }
        String url = normalizeUrl(req.getUrl());
        // 网址全平台唯一：跨公司查询，含已删除的（唯一索引不区分）
        Long taken = TenantScope.platform(() -> siteMapper.selectCount(new LambdaQueryWrapper<WebsiteSiteDO>()
                .eq(WebsiteSiteDO::getUrl, url).ne(selfId != null, WebsiteSiteDO::getId, selfId)));
        if (taken > 0) {
            throw new BizException("这个网址已被其他站点使用");
        }
        List<SiteLanguage> languages = languages(req.getLanguages());
        String template = trim(req.getPathTemplate());
        if (!template.startsWith("/") || template.indexOf("{slug}") < 0 || template.indexOf("{slug}") != template.lastIndexOf("{slug}")
                || template.length() > 200 || template.contains(" ")) {
            throw new BizException("商品页地址模板要以 / 开头，并且包含一次 {slug}，如 /products/{slug}/");
        }
        Integer mode = req.getPublishMode() == null ? WebsiteConstants.MODE_AUTO : req.getPublishMode();
        if (mode != WebsiteConstants.MODE_AUTO && mode != WebsiteConstants.MODE_COPY) {
            throw new BizException("发布方式不正确");
        }
        BigDecimal factor = req.getPriceFactor() == null ? DEFAULT_FACTOR : req.getPriceFactor();
        if (factor.compareTo(MIN_FACTOR) < 0 || factor.compareTo(MAX_FACTOR) > 0 || factor.stripTrailingZeros().scale() > 2) {
            throw new BizException("售价系数在 0.5 到 10 之间，最多两位小数");
        }
        boolean needPurge = Boolean.TRUE.equals(req.getNeedPurge());
        String purgeNote = trim(req.getPurgeNote());
        if (needPurge && purgeNote.isEmpty()) {
            throw new BizException("需要人工清缓存时，请填写清缓存的操作说明");
        }
        if (purgeNote.length() > WebsiteConstants.MAX_PURGE_NOTE) {
            throw new BizException("清缓存说明最长 " + WebsiteConstants.MAX_PURGE_NOTE + " 字");
        }

        s.setName(name);
        s.setUrl(url);
        s.setLanguages(json(languages));
        s.setPathTemplate(template);
        s.setPublishMode(mode);
        s.setProductScope(json(scope(req.getProductScope())));
        s.setNonGenuineBrands(json(ids(req.getNonGenuineBrandIds())));
        s.setPriceFactor(factor.setScale(2, RoundingMode.HALF_UP));
        s.setNeedPurge(needPurge ? 1 : 0);
        s.setPurgeNote(needPurge ? purgeNote : "");
        if (mode == WebsiteConstants.MODE_AUTO) {
            applyAuto(s, req, selfId);
        } else {
            // 只复制：仓库与部署配置不使用，保留已填的值，切回自动发布时不用重填
            s.setRepoBranch(StringUtils.hasText(s.getRepoBranch()) ? s.getRepoBranch() : "main");
            s.setDeployPort(s.getDeployPort() == null ? 22 : s.getDeployPort());
            nullToEmpty(s);
        }
    }

    private void applyAuto(WebsiteSiteDO s, SiteRequest req, Long selfId) {
        String repo = trim(req.getRepoUrl());
        if (!REPO.matcher(repo).matches() || repo.length() > 300) {
            throw new BizException("仓库地址要以 https:// 开头，不要带账号或令牌");
        }
        String branch = trim(req.getRepoBranch()).isEmpty() ? "main" : trim(req.getRepoBranch());
        if (!BRANCH.matcher(branch).matches()) {
            throw new BizException("分支名不正确");
        }
        String host = trim(req.getDeployHost());
        String user = trim(req.getDeployUser());
        String path = trim(req.getDeployPath());
        int port = req.getDeployPort() == null ? 22 : req.getDeployPort();
        if (!HOST_OR_USER.matcher(host).matches()) {
            throw new BizException("部署主机不正确");
        }
        if (port < 1 || port > 65535) {
            throw new BizException("部署端口在 1 到 65535 之间");
        }
        if (!HOST_OR_USER.matcher(user).matches()) {
            throw new BizException("部署用户不正确");
        }
        if (!DEPLOY_PATH.matcher(path).matches()) {
            throw new BizException("部署目录不正确，不能包含空格和特殊符号");
        }
        boolean hasToken = StringUtils.hasText(req.getRepoToken())
                || (selfId != null && secrets.configured(selfId, WebsiteConstants.SECRET_REPO_TOKEN));
        boolean hasKey = StringUtils.hasText(req.getDeployKey())
                || (selfId != null && secrets.configured(selfId, WebsiteConstants.SECRET_DEPLOY_KEY));
        if (!hasToken) {
            throw new BizException("自动发布需要填写仓库访问令牌");
        }
        if (!hasKey) {
            throw new BizException("自动发布需要填写部署私钥");
        }
        s.setRepoUrl(repo);
        s.setRepoBranch(branch);
        s.setDeployHost(host);
        s.setDeployPort(port);
        s.setDeployUser(user);
        s.setDeployPath(path);
    }

    private void saveSecrets(WebsiteSiteDO s, SiteRequest req) {
        int tenantId = s.getTenantId();
        if (StringUtils.hasText(req.getRepoToken())) {
            checkSecretLength(req.getRepoToken());
            secrets.save(tenantId, s.getId(), WebsiteConstants.SECRET_REPO_TOKEN, req.getRepoToken());
        }
        if (StringUtils.hasText(req.getDeployKey())) {
            checkSecretLength(req.getDeployKey());
            String key = req.getDeployKey().strip();
            if (!key.startsWith("-----BEGIN") || !key.contains("PRIVATE KEY")) {
                throw new BizException("部署私钥格式不对，请粘贴完整的私钥（以 -----BEGIN 开头）");
            }
            if (key.contains("ENCRYPTED") || key.contains("Proc-Type: 4,ENCRYPTED")) {
                throw new BizException("部署私钥带密码，发布服务无法使用，请换一把不带密码的私钥");
            }
            secrets.save(tenantId, s.getId(), WebsiteConstants.SECRET_DEPLOY_KEY, key);
        }
        if (Boolean.TRUE.equals(req.getClearIndexNowKey())) {
            secrets.remove(s.getId(), WebsiteConstants.SECRET_INDEXNOW_KEY);
        } else if (StringUtils.hasText(req.getIndexNowKey())) {
            checkSecretLength(req.getIndexNowKey());
            secrets.save(tenantId, s.getId(), WebsiteConstants.SECRET_INDEXNOW_KEY, req.getIndexNowKey());
        }
    }

    private static void checkSecretLength(String v) {
        if (v.length() > WebsiteConstants.MAX_SECRET) {
            throw new BizException("凭据太长");
        }
    }

    // ================================================================ 检测连接

    /** 通过发布服务检查仓库、适配脚本、部署服务器、网址四项；不改变站点和线上内容 */
    public List<CheckItemVO> check(Long id) {
        WebsiteSiteDO s = get(id);
        if (s.getPublishMode() != WebsiteConstants.MODE_AUTO) {
            throw new BizException("「只复制」的站点不需要检测连接");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("site", publisherSite(s));
        body.put("secrets", publisherSecrets(s.getId()));
        SitePublisherClient.Reply reply = publisher.check(body);
        if (reply.status() != 200 || reply.body() == null) {
            throw new BizException(StringUtils.hasText(reply.detail()) ? "检测失败：" + reply.detail() : "发布服务返回 " + reply.status());
        }
        Map<String, CheckItemVO> got = new HashMap<>(8);
        for (JsonNode n : reply.body().path("items")) {
            String key = n.path("key").asText();
            got.put(key, new CheckItemVO(key, CHECK_NAMES.getOrDefault(key, key), n.path("ok").asBoolean(), n.path("message").asText("")));
        }
        List<CheckItemVO> out = new ArrayList<>(CHECK_ORDER.size());
        for (String key : CHECK_ORDER) {
            out.add(got.getOrDefault(key, new CheckItemVO(key, CHECK_NAMES.get(key), false, "没有检测结果")));
        }
        return out;
    }

    /** 发给发布服务的站点信息；code 用网址的主机名（全平台唯一），作为发布服务里的工作目录名 */
    public Map<String, Object> publisherSite(WebsiteSiteDO s) {
        Map<String, Object> deploy = new LinkedHashMap<>();
        deploy.put("host", s.getDeployHost());
        deploy.put("port", s.getDeployPort());
        deploy.put("user", s.getDeployUser());
        deploy.put("path", s.getDeployPath());
        Map<String, Object> site = new LinkedHashMap<>();
        site.put("code", siteCode(s.getUrl()));
        site.put("repoUrl", s.getRepoUrl());
        site.put("branch", s.getRepoBranch());
        site.put("url", s.getUrl());
        site.put("deploy", deploy);
        return site;
    }

    /** 解密后的凭据，键名与发布服务一致；只放进请求体，不记录 */
    public Map<String, String> publisherSecrets(Long siteId) {
        Map<String, String> plain = secrets.plain(siteId);
        Map<String, String> out = new LinkedHashMap<>();
        putIfPresent(out, "repoToken", plain.get(WebsiteConstants.SECRET_REPO_TOKEN));
        putIfPresent(out, "deployKey", plain.get(WebsiteConstants.SECRET_DEPLOY_KEY));
        putIfPresent(out, "indexNowKey", plain.get(WebsiteConstants.SECRET_INDEXNOW_KEY));
        return out;
    }

    static String siteCode(String url) {
        return url.replaceFirst("^https://", "").replaceFirst(":\\d+$", "");
    }

    // ================================================================ 品类对照

    /** 一级品类及其二级品类，带本站的对照 */
    public List<CategoryMapVO> categoryMap(Long siteId) {
        get(siteId);
        Map<Long, String> map = mapping(siteId);
        List<ProductCategoryDO> all = categoryMapper.selectList(new LambdaQueryWrapper<ProductCategoryDO>()
                .eq(ProductCategoryDO::getStatus, 1).isNull(ProductCategoryDO::getDeletedAt));
        all.sort(Comparator.comparing((ProductCategoryDO c) -> c.getSortOrder() == null ? 0 : c.getSortOrder())
                .thenComparing(ProductCategoryDO::getId));
        Map<Long, List<CategoryMapVO>> children = new HashMap<>(32);
        for (ProductCategoryDO c : all) {
            if (c.getParentId() != null) {
                children.computeIfAbsent(c.getParentId(), k -> new ArrayList<>())
                        .add(new CategoryMapVO(c.getId(), displayName(c), map.getOrDefault(c.getId(), ""), List.of()));
            }
        }
        List<CategoryMapVO> out = new ArrayList<>();
        for (ProductCategoryDO c : all) {
            if (c.getParentId() == null) {
                out.add(new CategoryMapVO(c.getId(), displayName(c), map.getOrDefault(c.getId(), ""),
                        children.getOrDefault(c.getId(), List.of())));
            }
        }
        return out;
    }

    /** 整体替换品类对照；空的不保存（二级为空时用一级的） */
    @Transactional(rollbackFor = Exception.class)
    public List<CategoryMapVO> saveCategoryMap(Long siteId, CategoryMapRequest req) {
        WebsiteSiteDO s = get(siteId);
        Map<Long, String> wanted = new LinkedHashMap<>();
        Set<Long> known = new HashSet<>();
        for (ProductCategoryDO c : categoryMapper.selectList(new LambdaQueryWrapper<ProductCategoryDO>()
                .isNull(ProductCategoryDO::getDeletedAt))) {
            known.add(c.getId());
        }
        for (CategoryMapRequest.Item it : req == null || req.items() == null ? List.<CategoryMapRequest.Item>of() : req.items()) {
            if (it == null || it.categoryId() == null || !known.contains(it.categoryId())) {
                throw new BizException("品类不存在");
            }
            String code = trim(it.siteCategory()).toLowerCase(Locale.ROOT);
            if (code.isEmpty()) {
                continue;
            }
            if (!SITE_CATEGORY.matcher(code).matches()) {
                throw new BizException("站点品类「" + code + "」不正确：只能用小写字母、数字、- 和 _");
            }
            wanted.put(it.categoryId(), code);
        }
        Map<Long, WebsiteCategoryMapDO> existing = new HashMap<>(64);
        for (WebsiteCategoryMapDO m : categoryMapMapper.selectList(new LambdaQueryWrapper<WebsiteCategoryMapDO>()
                .eq(WebsiteCategoryMapDO::getSiteId, siteId))) {
            existing.put(m.getCategoryId(), m);
        }
        for (Map.Entry<Long, WebsiteCategoryMapDO> e : existing.entrySet()) {
            if (!wanted.containsKey(e.getKey())) {
                // 对照表是站点的配置，不是业务单据，去掉的对照直接删除
                categoryMapMapper.deleteById(e.getValue().getId());
            }
        }
        for (Map.Entry<Long, String> e : wanted.entrySet()) {
            WebsiteCategoryMapDO m = existing.get(e.getKey());
            if (m == null) {
                m = new WebsiteCategoryMapDO();
                m.setTenantId(s.getTenantId());
                m.setSiteId(siteId);
                m.setCategoryId(e.getKey());
                m.setSiteCategory(e.getValue());
                categoryMapMapper.insert(m);
            } else if (!e.getValue().equals(m.getSiteCategory())) {
                m.setSiteCategory(e.getValue());
                categoryMapMapper.updateById(m);
            }
        }
        log.info("保存品类对照，siteId={}，条数={}", siteId, wanted.size());
        events.publishEvent(SiteContentChanged.site(siteId));
        return categoryMap(siteId);
    }

    /** 商品品类对应的站点品类：二级没有对照时用一级的；都没有返回 null */
    public String siteCategory(Long siteId, Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        ProductCategoryDO c = categoryMapper.selectById(categoryId);
        return CategoryResolver.resolve(mapping(siteId), categoryId, c == null ? null : c.getParentId());
    }

    public Map<Long, String> mapping(Long siteId) {
        Map<Long, String> map = new HashMap<>(64);
        for (WebsiteCategoryMapDO m : categoryMapMapper.selectList(new LambdaQueryWrapper<WebsiteCategoryMapDO>()
                .eq(WebsiteCategoryMapDO::getSiteId, siteId))) {
            map.put(m.getCategoryId(), m.getSiteCategory());
        }
        return map;
    }

    // ================================================================ 转换

    public List<SiteLanguage> languagesOf(WebsiteSiteDO s) {
        return read(s.getLanguages(), new TypeReference<List<SiteLanguage>>() { }, List.of());
    }

    public SiteScope scopeOf(WebsiteSiteDO s) {
        return read(s.getProductScope(), new TypeReference<SiteScope>() { }, new SiteScope(List.of(), List.of()));
    }

    public List<Long> nonGenuineOf(WebsiteSiteDO s) {
        return read(s.getNonGenuineBrands(), new TypeReference<List<Long>>() { }, List.of());
    }

    private SiteVO toVO(WebsiteSiteDO s) {
        SiteVO v = new SiteVO();
        v.setId(s.getId());
        v.setName(s.getName());
        v.setUrl(s.getUrl());
        v.setLanguages(languagesOf(s));
        v.setPathTemplate(s.getPathTemplate());
        v.setPublishMode(s.getPublishMode());
        v.setPublishModeName(s.getPublishMode() == WebsiteConstants.MODE_AUTO ? "自动发布" : "只复制");
        v.setRepoUrl(s.getRepoUrl());
        v.setRepoBranch(s.getRepoBranch());
        v.setDeployHost(s.getDeployHost());
        v.setDeployPort(s.getDeployPort());
        v.setDeployUser(s.getDeployUser());
        v.setDeployPath(s.getDeployPath());
        v.setProductScope(scopeOf(s));
        v.setNonGenuineBrandIds(nonGenuineOf(s));
        v.setPriceFactor(s.getPriceFactor());
        v.setCurrencyCode(s.getCurrencyCode());
        v.setNeedPurge(Integer.valueOf(1).equals(s.getNeedPurge()));
        v.setPurgeNote(s.getPurgeNote());
        v.setTakeoverStatus(s.getTakeoverStatus());
        v.setTakeoverStatusName(s.getTakeoverStatus() == WebsiteConstants.TAKEOVER_DONE ? "以 ERP 为准" : "接管中");
        v.setTakeoverBy(s.getTakeoverBy());
        v.setTakeoverAt(s.getTakeoverAt());
        v.setStatus(s.getStatus());
        Map<String, SecretVO> sec = secrets.status(s.getId());
        v.setRepoToken(sec.getOrDefault(WebsiteConstants.SECRET_REPO_TOKEN, SecretVO.NONE));
        v.setDeployKey(sec.getOrDefault(WebsiteConstants.SECRET_DEPLOY_KEY, SecretVO.NONE));
        v.setIndexNowKey(sec.getOrDefault(WebsiteConstants.SECRET_INDEXNOW_KEY, SecretVO.NONE));
        v.setCreateTime(s.getCreateTime());
        v.setCreateBy(s.getCreateBy());
        v.setUpdateTime(s.getUpdateTime());
        v.setUpdateBy(s.getUpdateBy());
        return v;
    }

    // ================================================================ 校验工具

    /** 网址：小写、去掉结尾斜杠，必须 https:// 开头且不带路径 */
    static String normalizeUrl(String raw) {
        String url = trim(raw).toLowerCase(Locale.ROOT).replaceAll("/+$", "");
        if (url.length() > WebsiteConstants.MAX_URL || !URL.matcher(url).matches()) {
            throw new BizException("网址要以 https:// 开头，不带路径，如 https://fouwell.com");
        }
        return url;
    }

    static List<SiteLanguage> languages(List<SiteLanguage> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new BizException("至少选择一种语言");
        }
        List<SiteLanguage> out = new ArrayList<>(raw.size());
        Set<String> codes = new HashSet<>();
        Set<String> prefixes = new HashSet<>();
        for (SiteLanguage l : raw) {
            String code = l == null ? "" : trim(l.code()).toLowerCase(Locale.ROOT);
            if (!WebsiteConstants.LANGUAGES.contains(code)) {
                throw new BizException("语言只能选英文或俄文");
            }
            String prefix = trim(l.prefix()).replaceAll("/+$", "");
            if (!PREFIX.matcher(prefix).matches()) {
                throw new BizException("语言地址前缀为空或 /xx 形式，如 /ru");
            }
            if (!codes.add(code)) {
                throw new BizException("语言不能重复");
            }
            if (!prefixes.add(prefix)) {
                throw new BizException("不同语言的地址前缀不能相同");
            }
            out.add(new SiteLanguage(code, prefix));
        }
        return out;
    }

    private static SiteScope scope(SiteScope raw) {
        return raw == null ? new SiteScope(List.of(), List.of()) : new SiteScope(ids(raw.brandIds()), ids(raw.categoryIds()));
    }

    private static List<Long> ids(List<Long> raw) {
        List<Long> out = new ArrayList<>();
        for (Long id : raw == null ? List.<Long>of() : raw) {
            if (id == null || id <= 0) {
                throw new BizException("品牌或品类不正确");
            }
            if (!out.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    private static void nullToEmpty(WebsiteSiteDO s) {
        s.setRepoUrl(s.getRepoUrl() == null ? "" : s.getRepoUrl());
        s.setDeployHost(s.getDeployHost() == null ? "" : s.getDeployHost());
        s.setDeployUser(s.getDeployUser() == null ? "" : s.getDeployUser());
        s.setDeployPath(s.getDeployPath() == null ? "" : s.getDeployPath());
    }

    private static String displayName(ProductCategoryDO c) {
        return StringUtils.hasText(c.getCategoryNameZh()) ? c.getCategoryNameZh() : c.getCategoryName();
    }

    private static void putIfPresent(Map<String, String> m, String k, String v) {
        if (StringUtils.hasText(v)) {
            m.put(k, v);
        }
    }

    private static String trim(String v) {
        return v == null ? "" : v.strip();
    }

    private String json(Object v) {
        try {
            return objectMapper.writeValueAsString(v);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("站点配置序列化失败", e);
        }
    }

    private <T> T read(String text, TypeReference<T> type, T fallback) {
        if (!StringUtils.hasText(text)) {
            return fallback;
        }
        try {
            return objectMapper.readValue(text, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("站点配置解析失败", e);
        }
    }
}
