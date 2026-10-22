package com.zhul.erp.modules.listing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.framework.security.SecurityUtils;
import com.zhul.erp.framework.storage.FileStorageService;
import com.zhul.erp.framework.storage.FileStorageService.FileLink;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.listing.constants.ListingConstants;
import com.zhul.erp.modules.listing.dto.ListingProductVO;
import com.zhul.erp.modules.listing.dto.ListingTaskDetailVO;
import com.zhul.erp.modules.listing.dto.ListingTaskQuery;
import com.zhul.erp.modules.listing.dto.ListingTaskVO;
import com.zhul.erp.modules.listing.dto.PublishRequest;
import com.zhul.erp.modules.listing.dto.ReferenceRequest;
import com.zhul.erp.modules.listing.entity.ListingTaskDO;
import com.zhul.erp.modules.listing.repository.ListingTaskMapper;
import com.zhul.erp.modules.listing.repository.ListingTaskRow;
import com.zhul.erp.modules.media.dto.MediaDetailVO;
import com.zhul.erp.modules.media.dto.MediaItemVO;
import com.zhul.erp.modules.media.dto.MediaRef;
import com.zhul.erp.modules.media.dto.MediaReferenceVO;
import com.zhul.erp.modules.media.entity.MediaItemDO;
import com.zhul.erp.modules.media.repository.MediaItemMapper;
import com.zhul.erp.modules.media.service.MediaIndexer;
import com.zhul.erp.modules.media.service.MediaLibraryService;
import com.zhul.erp.modules.media.service.MediaReferenceService;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.content.service.ContentTaskService;
import com.zhul.erp.modules.product.content.support.MarketingPlatform;
import com.zhul.erp.modules.product.entity.ProductBrandDO;
import com.zhul.erp.modules.product.entity.ProductCategoryDO;
import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.product.repository.ProductBrandMapper;
import com.zhul.erp.modules.product.repository.ProductCategoryMapper;
import com.zhul.erp.modules.product.repository.ProductMapper;
import com.zhul.erp.modules.product.support.MpnNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** 发品任务：列表、详情（复制助手与素材）、参考素材、回填发布结果、已同步、已替换（spec product/listing-task） */
@Service
@RequiredArgsConstructor
public class ListingTaskService {

    private static final List<String> TABS = List.of("pending", "updated", "replace", "published", "cancelled", "all");
    private static final String[] STATUS_NAMES = {"", "待发布", "已发布", "已取消"};
    private static final Map<String, String> PRODUCT_STATUS_NAMES = Map.of("pending", "待发布", "updated", "内容有更新",
            "replace", "待替换素材", "published", "已发布", "cancelled", "已取消");
    private static final Pattern URL = Pattern.compile("^https?://\\S+$", Pattern.CASE_INSENSITIVE);

    private final ListingTaskMapper taskMapper;
    private final ProductMapper productMapper;
    private final ProductBrandMapper brandMapper;
    private final ProductCategoryMapper categoryMapper;
    private final MediaItemMapper mediaItemMapper;
    private final MediaLibraryService mediaLibrary;
    private final MediaReferenceService mediaReferences;
    private final ContentTaskService contentTasks;
    private final FileStorageService files;

    // ================================================================ 列表

    /** 按商品分页（spec「发品任务列表」） */
    public PageResult<ListingProductVO> page(ListingTaskQuery q) {
        String tab = tab(q.getTab());
        Filter f = filter(q);
        int size = q.getPageSize() == null ? 20 : Math.max(1, Math.min(100, q.getPageSize()));
        int page = q.getPage() == null ? 1 : Math.max(1, q.getPage());
        Integer tenantId = TenantContext.getTenantId();
        long total = taskMapper.countProducts(tenantId, tab, f.brandId, f.categoryId, f.keyword, f.mpn);
        if (total == 0) {
            return PageResult.of(0L, List.of());
        }
        List<Long> ids = taskMapper.pageProducts(tenantId, tab, f.brandId, f.categoryId, f.keyword, f.mpn,
                (long) (page - 1) * size, size);
        Map<Long, ListingProductVO> byId = products(ids);
        return PageResult.of(total, ids.stream().map(byId::get).filter(Objects::nonNull).toList());
    }

    /** 各页签的商品数 */
    public Map<String, Long> counts(ListingTaskQuery q) {
        Filter f = filter(q);
        Map<String, Object> row = taskMapper.productCounts(TenantContext.getTenantId(), f.brandId, f.categoryId, f.keyword, f.mpn);
        Map<String, Long> out = new LinkedHashMap<>();
        for (String t : TABS) {
            Object v = row == null ? null : row.get(t);
            out.put(t, v instanceof Number n ? n.longValue() : 0L);
        }
        return out;
    }

    /** 一个商品的各平台任务（商品发品详情页头） */
    public ListingProductVO product(Long productId) {
        ListingProductVO vo = products(List.of(productId)).get(productId);
        if (vo == null) {
            throw new BizException("这个商品没有发品任务");
        }
        return vo;
    }

    private Map<Long, ListingProductVO> products(List<Long> productIds) {
        Map<Long, ListingProductVO> out = new LinkedHashMap<>();
        if (productIds.isEmpty()) {
            return out;
        }
        List<ListingTaskRow> rows = new ArrayList<>(taskMapper.rowsOfProducts(TenantContext.getTenantId(), productIds));
        rows.sort(Comparator.comparing((ListingTaskRow r) -> platformOrder(r.getPlatform())));
        for (ListingTaskVO t : toVos(rows)) {
            ListingProductVO p = out.computeIfAbsent(t.getProductId(), id -> {
                ListingProductVO v = new ListingProductVO();
                v.setProductId(id);
                v.setBrand(t.getBrand());
                v.setModel(t.getModel());
                v.setProductName(t.getProductName());
                v.setCategory(t.getCategory());
                return v;
            });
            p.getTasks().add(t);
            if (p.getCreateTime() == null || t.getCreateTime() != null && t.getCreateTime().isBefore(p.getCreateTime())) {
                p.setCreateTime(t.getCreateTime());
                p.setCreateBy(t.getCreateBy());
            }
            if (p.getUpdateTime() == null || t.getUpdateTime() != null && t.getUpdateTime().isAfter(p.getUpdateTime())) {
                p.setUpdateTime(t.getUpdateTime());
                p.setUpdateBy(t.getUpdateBy());
            }
        }
        out.values().forEach(ListingTaskService::summarize);
        return out;
    }

    /** 商品状态：待发布 → 内容有更新 → 待替换素材 → 已发布 → 已取消，取第一个满足的 */
    private static void summarize(ListingProductVO p) {
        List<ListingTaskVO> ts = p.getTasks();
        String status;
        if (ts.stream().anyMatch(t -> t.getStatus() == ListingConstants.STATUS_PENDING)) {
            status = "pending";
        } else if (ts.stream().anyMatch(t -> Boolean.TRUE.equals(t.getContentUpdated()))) {
            status = "updated";
        } else if (ts.stream().anyMatch(t -> Boolean.TRUE.equals(t.getNeedReplace()))) {
            status = "replace";
        } else if (ts.stream().anyMatch(t -> t.getStatus() == ListingConstants.STATUS_PUBLISHED)) {
            status = "published";
        } else {
            status = "cancelled";
        }
        p.setStatus(status);
        p.setStatusName(PRODUCT_STATUS_NAMES.get(status));
        p.setNeedMedia(ts.stream().anyMatch(t -> Boolean.TRUE.equals(t.getNeedMedia())));
    }

    private static int platformOrder(String code) {
        MarketingPlatform p = MarketingPlatform.of(code);
        return p == null ? Integer.MAX_VALUE : p.ordinal();
    }

    private record Filter(Long brandId, Long categoryId, String keyword, String mpn) {
    }

    private static Filter filter(ListingTaskQuery q) {
        String keyword = StringUtils.hasText(q.getKeyword()) ? q.getKeyword().trim() : null;
        String mpn = keyword == null ? null : MpnNormalizer.normalize(keyword);
        return new Filter(q.getBrandId(), q.getCategoryId(), keyword, StringUtils.hasText(mpn) ? mpn : null);
    }

    private static String tab(String tab) {
        if (!StringUtils.hasText(tab)) {
            return "pending";
        }
        if (!TABS.contains(tab)) {
            throw new BizException("页签无效");
        }
        return tab;
    }

    private List<ListingTaskVO> toVos(List<ListingTaskRow> rows) {
        Map<Long, ProductDO> products = rows.isEmpty() ? Map.of() : productMapper.selectBatchIds(
                        rows.stream().map(ListingTaskDO::getProductId).distinct().toList())
                .stream().collect(Collectors.toMap(ProductDO::getId, Function.identity()));
        Map<Long, String> brands = names(products.values().stream().map(ProductDO::getBrandId), brandMapper::selectBatchIds,
                ProductBrandDO::getId, ProductBrandDO::getBrandName);
        Map<Long, String> categories = names(products.values().stream().map(ProductDO::getCategoryId), categoryMapper::selectBatchIds,
                ProductCategoryDO::getId, c -> StringUtils.hasText(c.getCategoryNameZh()) ? c.getCategoryNameZh() : c.getCategoryName());
        Map<String, int[]> counts = mediaCounts(rows.stream().map(ListingTaskDO::getAssetKey).collect(Collectors.toSet()));
        List<ListingTaskVO> out = new ArrayList<>(rows.size());
        for (ListingTaskRow r : rows) {
            ListingTaskVO vo = base(r, products.get(r.getProductId()), brands, categories);
            vo.setNeedMedia(Boolean.TRUE.equals(r.getNeedMedia()));
            vo.setContentUpdated(Boolean.TRUE.equals(r.getContentUpdated()));
            vo.setNeedReplace(Boolean.TRUE.equals(r.getNeedReplace()));
            int[] c = counts.getOrDefault(r.getAssetKey(), new int[2]);
            vo.setImageCount(c[0]);
            vo.setVideoCount(c[1]);
            out.add(vo);
        }
        return out;
    }

    private static <T> Map<Long, String> names(Stream<Long> ids, Function<Collection<Long>, List<T>> load,
                                               Function<T, Long> id, Function<T, String> name) {
        List<Long> list = ids.filter(Objects::nonNull).distinct().toList();
        if (list.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> out = new HashMap<>();
        load.apply(list).forEach(t -> out.put(id.apply(t), name.apply(t)));
        return out;
    }

    /** 型号键 → [图片数, 视频数]（用于发品的） */
    private Map<String, int[]> mediaCounts(Set<String> keys) {
        Map<String, int[]> out = new HashMap<>();
        if (keys.isEmpty()) {
            return out;
        }
        mediaItemMapper.selectList(new LambdaQueryWrapper<MediaItemDO>()
                        .select(MediaItemDO::getAssetKey, MediaItemDO::getKind)
                        .eq(MediaItemDO::getTenantId, TenantContext.getTenantId())
                        .in(MediaItemDO::getAssetKey, keys)
                        .eq(MediaItemDO::getForListing, 1)
                        .in(MediaItemDO::getKind, 1, 2)
                        .isNull(MediaItemDO::getDeletedAt))
                .forEach(i -> out.computeIfAbsent(i.getAssetKey(), k -> new int[2])[i.getKind() == 1 ? 0 : 1]++);
        return out;
    }

    private static ListingTaskVO base(ListingTaskDO t, ProductDO p, Map<Long, String> brands, Map<Long, String> categories) {
        ListingTaskVO vo = new ListingTaskVO();
        vo.setId(t.getId());
        vo.setProductId(t.getProductId());
        if (p != null) {
            vo.setBrand(brands.get(p.getBrandId()));
            vo.setModel(p.getMpnRaw());
            vo.setProductName(p.getProductName());
            vo.setCategory(categories.get(p.getCategoryId()));
        }
        vo.setPlatform(t.getPlatform());
        MarketingPlatform platform = MarketingPlatform.of(t.getPlatform());
        vo.setPlatformLabel(platform == null ? t.getPlatform() : platform.label());
        vo.setStatus(t.getStatus());
        vo.setStatusName(STATUS_NAMES[t.getStatus()]);
        vo.setCancelReason(t.getCancelReason());
        vo.setRefBrand(blankToNull(t.getRefBrand()));
        vo.setRefModel(blankToNull(t.getRefModel()));
        vo.setRefBy(blankToNull(t.getRefBy()));
        vo.setRefTime(t.getRefTime());
        vo.setListingUrl(blankToNull(t.getListingUrl()));
        vo.setPlatformItemId(blankToNull(t.getPlatformItemId()));
        vo.setNote(blankToNull(t.getNote()));
        vo.setPublishedBy(blankToNull(t.getPublishedBy()));
        vo.setPublishedAt(t.getPublishedAt());
        vo.setSyncedBy(blankToNull(t.getSyncedBy()));
        vo.setSyncedAt(t.getSyncedAt());
        vo.setReplacedBy(blankToNull(t.getReplacedBy()));
        vo.setReplacedAt(t.getReplacedAt());
        vo.setCreateTime(t.getCreateTime());
        vo.setCreateBy(t.getCreateBy());
        vo.setUpdateTime(t.getUpdateTime());
        vo.setUpdateBy(t.getUpdateBy());
        return vo;
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s : null;
    }

    // ================================================================ 详情

    public ListingTaskDetailVO detail(Long id) {
        ListingTaskDO t = find(id);
        ListingTaskDetailVO vo = new ListingTaskDetailVO();
        ListingTaskVO task = toVos(List.of(row(t))).get(0);
        vo.setTask(task);
        vo.setContent(contentTasks.productContent(t.getProductId()));
        if (task.getModel() != null) {
            vo.setOwnMedia(media(task.getBrand(), task.getModel(), true));
        }
        if (StringUtils.hasText(t.getRefModel())) {
            vo.setReferenceMedia(media(t.getRefBrand(), t.getRefModel(), false));
        }
        return vo;
    }

    /** 一个型号的图片和视频：本型号取用于发品的，参考型号取可发客户的 */
    private List<MediaItemVO> media(String brand, String model, boolean forListing) {
        MediaDetailVO d = mediaLibrary.detail(brand == null ? "" : brand, model);
        List<MediaItemVO> out = new ArrayList<>();
        for (MediaDetailVO.Group g : d.getGroups()) {
            for (MediaItemVO i : g.getItems()) {
                boolean media = Objects.equals(i.getKind(), 1) || Objects.equals(i.getKind(), 2);
                boolean pick = forListing ? Boolean.TRUE.equals(i.getForListing()) : Boolean.TRUE.equals(i.getCanSend());
                if (media && pick) {
                    out.add(i);
                }
            }
        }
        return out;
    }

    /** 预览或下载链接：只能取这个任务显示的素材 */
    public List<FileLink> links(Long id, List<MediaRef> refs, boolean download) {
        ListingTaskDetailVO d = detail(id);
        Set<String> allowed = new HashSet<>();
        for (List<MediaItemVO> list : List.of(d.getOwnMedia(), d.getReferenceMedia())) {
            list.forEach(i -> allowed.add(i.getItemId() != null ? "i" + i.getItemId() : "o" + i.getOfficialTable() + i.getOfficialId()));
        }
        for (MediaRef r : refs) {
            String k = r.getItemId() != null ? "i" + r.getItemId() : "o" + r.getOfficialTable() + r.getOfficialId();
            if (!allowed.contains(k)) {
                throw new BizException("素材不属于这个发品任务");
            }
        }
        return mediaLibrary.links(refs, download);
    }

    /** 这个任务型号的参考素材（查找顺序与素材库一致） */
    public MediaReferenceVO references(Long id) {
        ProductDO p = product(find(id));
        return mediaReferences.references(brandName(p), p.getMpnRaw());
    }

    /** 参考型号的可发客户图片和视频（选用前预览）；只限这个任务的参考列表 */
    public List<MediaItemVO> referenceMedia(Long id, String brand, String model) {
        requireReference(find(id), brand, model);
        return media(brand, model, false);
    }

    /** 参考型号素材的预览或下载链接 */
    public List<FileLink> referenceLinks(Long id, String brand, String model, List<MediaRef> refs, boolean download) {
        List<MediaItemVO> items = referenceMedia(id, brand, model);
        Set<String> allowed = new HashSet<>();
        items.forEach(i -> allowed.add(i.getItemId() != null ? "i" + i.getItemId() : "o" + i.getOfficialTable() + i.getOfficialId()));
        for (MediaRef r : refs) {
            String k = r.getItemId() != null ? "i" + r.getItemId() : "o" + r.getOfficialTable() + r.getOfficialId();
            if (!allowed.contains(k)) {
                throw new BizException("素材不属于这个参考型号");
            }
        }
        return mediaLibrary.links(refs, download);
    }

    /** 参考素材打包：zip 文件名、要打进包的文件（原文件名已去重）、说明（外链与超出上限的） */
    public record ZipPlan(String fileName, List<ZipFile> files, List<String> notes) {
    }

    public record ZipFile(String name, String storage, String key) {
    }

    /** 参考型号的可发客户图片和视频打包（最多 50 个）；先算好清单，校验失败时按普通错误返回 */
    public ZipPlan referenceZip(Long id, String brand, String model) {
        List<MediaItemVO> items = referenceMedia(id, brand, model);
        if (items.isEmpty()) {
            throw new BizException("这个型号没有可发客户的图片和视频");
        }
        return zipPlan(items, brand, model, "参考素材-");
    }

    /**
     * 这个任务显示的素材打包：reference = 已选的参考素材（可发客户），否则本型号的发品素材。
     * 与详情页显示的一致，所以已选参考型号不再在参考列表里时也能下载。
     */
    public ZipPlan taskZip(Long id, boolean reference) {
        ListingTaskDetailVO d = detail(id);
        ListingTaskDO t = find(id);
        List<MediaItemVO> items = reference ? d.getReferenceMedia() : d.getOwnMedia();
        if (items == null || items.isEmpty()) {
            throw new BizException(reference ? "还没有选参考素材，或参考型号没有可发客户的图片和视频" : "这个型号还没有发品素材");
        }
        String brand = reference ? t.getRefBrand() : d.getTask().getBrand();
        String model = reference ? t.getRefModel() : d.getTask().getModel();
        return zipPlan(items, brand, model, reference ? "参考素材-" : "发品素材-");
    }

    private ZipPlan zipPlan(List<MediaItemVO> items, String brand, String model, String prefix) {
        List<ZipFile> files = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (MediaItemVO i : items) {
            MediaRef ref = new MediaRef();
            ref.setItemId(i.getItemId());
            ref.setOfficialTable(i.getOfficialTable());
            ref.setOfficialId(i.getOfficialId());
            ref.setBrand(brand);
            ref.setModel(model);
            MediaLibraryService.FileSource src = mediaLibrary.source(ref);
            if (src.externalUrl() != null) {
                notes.add("外部链接的官方素材，没有打包：" + src.externalUrl());
                continue;
            }
            if (files.size() >= ListingConstants.MAX_ZIP_FILES) {
                notes.add("超过 " + ListingConstants.MAX_ZIP_FILES + " 个文件，没有打包：" + src.fileName());
                continue;
            }
            files.add(new ZipFile(uniqueName(safeName(src.fileName()), used), src.storage(), src.key()));
        }
        return new ZipPlan(safeName(prefix + (brand == null ? "" : brand.trim()) + "-" + model.trim()) + ".zip", files, notes);
    }

    /** 按清单边读边写 zip（图片视频本身已压缩，不再压缩，省 CPU） */
    public void writeZip(ZipPlan plan, OutputStream out) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            zip.setLevel(Deflater.NO_COMPRESSION);
            for (ZipFile f : plan.files()) {
                zip.putNextEntry(new ZipEntry(f.name()));
                try (InputStream in = files.open(f.storage(), f.key())) {
                    in.transferTo(zip);
                }
                zip.closeEntry();
            }
            if (!plan.notes().isEmpty()) {
                zip.putNextEntry(new ZipEntry("说明.txt"));
                zip.write(String.join("\n", plan.notes()).getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
    }

    private static String safeName(String name) {
        String n = name == null ? "" : name.replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]", "_").strip();
        return n.isEmpty() ? "file" : n;
    }

    /** 重名时在扩展名前加「 (2)」「 (3)」… */
    private static String uniqueName(String name, Set<String> used) {
        if (used.add(name.toLowerCase(Locale.ROOT))) {
            return name;
        }
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int n = 2; ; n++) {
            String candidate = base + " (" + n + ")" + ext;
            if (used.add(candidate.toLowerCase(Locale.ROOT))) {
                return candidate;
            }
        }
    }

    /** 型号必须在这个任务型号的参考列表里 */
    private void requireReference(ListingTaskDO t, String brand, String model) {
        if (!StringUtils.hasText(model)) {
            throw new BizException("请指定参考型号");
        }
        ProductDO p = product(t);
        String key = MediaIndexer.key(brand, model);
        boolean listed = mediaReferences.references(brandName(p), p.getMpnRaw()).getModels().stream()
                .anyMatch(m -> MediaIndexer.key(m.getBrand(), m.getModel()).equals(key));
        if (!listed) {
            throw new BizException("只能从这个型号的参考素材里选择");
        }
    }

    // ================================================================ 操作

    @Transactional(rollbackFor = Exception.class)
    public ListingTaskDetailVO setReference(Long id, ReferenceRequest req) {
        ListingTaskDO t = lock(id);
        if (t.getStatus() != ListingConstants.STATUS_PENDING) {
            throw new BizException("只有待发布的任务可以选择参考素材");
        }
        LambdaUpdateWrapper<ListingTaskDO> w = new LambdaUpdateWrapper<ListingTaskDO>().eq(ListingTaskDO::getId, id);
        String user = SecurityUtils.getCurrentUsername();
        LocalDateTime now = LocalDateTime.now();
        if (req == null || !StringUtils.hasText(req.getModel())) {
            w.set(ListingTaskDO::getRefBrand, "").set(ListingTaskDO::getRefModel, "")
                    .set(ListingTaskDO::getRefBy, "").set(ListingTaskDO::getRefTime, null);
        } else {
            if (hasOwn(id)) {
                throw new BizException("本型号已有发品素材，不需要参考素材");
            }
            requireReference(t, req.getBrand(), req.getModel());
            w.set(ListingTaskDO::getRefBrand, req.getBrand() == null ? "" : req.getBrand().trim())
                    .set(ListingTaskDO::getRefModel, req.getModel().trim())
                    .set(ListingTaskDO::getRefBy, user).set(ListingTaskDO::getRefTime, now);
        }
        taskMapper.update(null, w.set(ListingTaskDO::getUpdateTime, now).set(ListingTaskDO::getUpdateBy, user));
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public ListingTaskDetailVO publish(Long id, PublishRequest req) {
        ListingTaskDO t = lock(id);
        if (t.getStatus() != ListingConstants.STATUS_PENDING) {
            throw new BizException(t.getStatus() == ListingConstants.STATUS_PUBLISHED ? "任务已发布" : "任务已取消，不能发布");
        }
        Map<String, Object> flags = taskMapper.flags(id);
        if (bool(flags.get("need_media"))) {
            throw new BizException("还没有发品素材，先补素材或选择参考素材");
        }
        String user = SecurityUtils.getCurrentUsername();
        LocalDateTime now = LocalDateTime.now();
        taskMapper.update(null, record(new LambdaUpdateWrapper<ListingTaskDO>().eq(ListingTaskDO::getId, id), t, req)
                .set(ListingTaskDO::getStatus, ListingConstants.STATUS_PUBLISHED)
                .set(ListingTaskDO::getPublishedBy, user)
                .set(ListingTaskDO::getPublishedAt, now)
                .set(ListingTaskDO::getContentAt, time(flags.get("current_content_at")))
                .set(ListingTaskDO::getUpdateTime, now)
                .set(ListingTaskDO::getUpdateBy, user));
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public ListingTaskDetailVO updateRecord(Long id, PublishRequest req) {
        ListingTaskDO t = lock(id);
        if (t.getStatus() != ListingConstants.STATUS_PUBLISHED) {
            throw new BizException("只有已发布的任务可以修改发布记录");
        }
        taskMapper.update(null, record(new LambdaUpdateWrapper<ListingTaskDO>().eq(ListingTaskDO::getId, id), t, req)
                .set(ListingTaskDO::getUpdateTime, LocalDateTime.now())
                .set(ListingTaskDO::getUpdateBy, SecurityUtils.getCurrentUsername()));
        return detail(id);
    }

    /** 平台链接、商品编号（只有阿里国际站、中国制造网）、备注 */
    private static LambdaUpdateWrapper<ListingTaskDO> record(LambdaUpdateWrapper<ListingTaskDO> w, ListingTaskDO t, PublishRequest req) {
        String url = req.getListingUrl() == null ? "" : req.getListingUrl().trim();
        if (!URL.matcher(url).matches()) {
            throw new BizException("平台链接要以 http:// 或 https:// 开头");
        }
        if (url.length() > ListingConstants.MAX_URL) {
            throw new BizException("平台链接最长 500 个字符");
        }
        boolean market = MarketingPlatform.ALIBABA.code().equals(t.getPlatform()) || MarketingPlatform.MIC.code().equals(t.getPlatform());
        String itemId = market && req.getPlatformItemId() != null ? req.getPlatformItemId().trim() : "";
        String note = req.getNote() == null ? "" : req.getNote().trim();
        if (itemId.length() > ListingConstants.MAX_ITEM_ID || note.length() > ListingConstants.MAX_NOTE) {
            throw new BizException("平台商品编号最长 64 个字符，备注最长 300 个字符");
        }
        return w.set(ListingTaskDO::getListingUrl, url).set(ListingTaskDO::getPlatformItemId, itemId).set(ListingTaskDO::getNote, note);
    }

    @Transactional(rollbackFor = Exception.class)
    public ListingTaskDetailVO markSynced(Long id) {
        lock(id);
        Map<String, Object> flags = taskMapper.flags(id);
        if (!bool(flags.get("content_updated"))) {
            throw new BizException("没有需要同步的更新");
        }
        String user = SecurityUtils.getCurrentUsername();
        LocalDateTime now = LocalDateTime.now();
        taskMapper.update(null, new LambdaUpdateWrapper<ListingTaskDO>().eq(ListingTaskDO::getId, id)
                .set(ListingTaskDO::getContentAt, time(flags.get("current_content_at")))
                .set(ListingTaskDO::getSyncedBy, user)
                .set(ListingTaskDO::getSyncedAt, now)
                .set(ListingTaskDO::getUpdateTime, now)
                .set(ListingTaskDO::getUpdateBy, user));
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public ListingTaskDetailVO markReplaced(Long id) {
        lock(id);
        if (!bool(taskMapper.flags(id).get("need_replace"))) {
            throw new BizException("没有需要替换的素材");
        }
        String user = SecurityUtils.getCurrentUsername();
        LocalDateTime now = LocalDateTime.now();
        taskMapper.update(null, new LambdaUpdateWrapper<ListingTaskDO>().eq(ListingTaskDO::getId, id)
                .set(ListingTaskDO::getRefBrand, "")
                .set(ListingTaskDO::getRefModel, "")
                .set(ListingTaskDO::getReplacedBy, user)
                .set(ListingTaskDO::getReplacedAt, now)
                .set(ListingTaskDO::getUpdateTime, now)
                .set(ListingTaskDO::getUpdateBy, user));
        return detail(id);
    }

    // ================================================================ 工具

    private ListingTaskDO find(Long id) {
        ListingTaskDO t = taskMapper.selectById(id);
        if (t == null || t.getDeletedAt() != null || !Objects.equals(t.getTenantId(), TenantContext.getTenantId())) {
            throw new BizException("发品任务不存在");
        }
        return t;
    }

    private ListingTaskDO lock(Long id) {
        if (taskMapper.lock(id, TenantContext.getTenantId()) == null) {
            throw new BizException("发品任务不存在");
        }
        return taskMapper.selectById(id);
    }

    private ListingTaskRow row(ListingTaskDO t) {
        Map<String, Object> flags = taskMapper.flags(t.getId());
        ListingTaskRow r = new ListingTaskRow();
        BeanUtils.copyProperties(t, r);
        r.setNeedMedia(bool(flags.get("need_media")));
        r.setContentUpdated(bool(flags.get("content_updated")));
        r.setNeedReplace(bool(flags.get("need_replace")));
        return r;
    }

    private boolean hasOwn(Long id) {
        return bool(taskMapper.flags(id).get("has_own"));
    }

    private ProductDO product(ListingTaskDO t) {
        ProductDO p = productMapper.selectById(t.getProductId());
        if (p == null || p.getDeletedAt() != null || !Objects.equals(p.getTenantId(), ProductConstants.PLATFORM_TENANT_ID)) {
            throw new BizException("商品不存在");
        }
        return p;
    }

    private String brandName(ProductDO p) {
        ProductBrandDO b = p.getBrandId() == null ? null : brandMapper.selectById(p.getBrandId());
        return b == null ? "" : b.getBrandName();
    }

    private static boolean bool(Object o) {
        return o instanceof Number n ? n.intValue() == 1 : Boolean.TRUE.equals(o);
    }

    private static LocalDateTime time(Object o) {
        if (o instanceof LocalDateTime t) {
            return t;
        }
        return o instanceof Timestamp ts ? ts.toLocalDateTime() : null;
    }
}
