package com.zhul.erp.modules.product.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.product.entity.ProductOrderCodeDO;
import com.zhul.erp.modules.product.repository.ProductMapper;
import com.zhul.erp.modules.product.repository.ProductOrderCodeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 商品订货号（物料号、料号、Order No.）：型号是唯一的建档编码，订货号辅助识别。
 * 一个商品可以有多个订货号；同一品牌下一个订货号只属于一个商品。比较键与型号同一归一化规则。
 */
@Component
@RequiredArgsConstructor
public class ProductOrderCodes {

    public static final int CODE_MAX = 64;
    /** 询盘型号、候选上存订货号文本的长度上限 */
    public static final int TEXT_MAX = 255;

    private final ProductOrderCodeMapper mapper;
    private final ProductMapper productMapper;

    /** 拆分订货号文本：逗号、分号、顿号、换行分隔；去空，按比较键去重，保留原文 */
    public static List<String> split(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Map<String, String> byKey = new LinkedHashMap<>();
        for (String part : text.split("[,，;；、\\n\\r]+")) {
            String code = part.trim();
            String key = MpnNormalizer.normalize(code);
            if (!key.isEmpty() && code.length() <= CODE_MAX) {
                byKey.putIfAbsent(key, code);
            }
        }
        return new ArrayList<>(byKey.values());
    }

    /** 合成订货号文本（英文逗号分隔），超长时丢掉放不下的 */
    public static String join(Collection<String> codes) {
        StringBuilder sb = new StringBuilder();
        for (String c : codes) {
            String next = sb.isEmpty() ? c : ", " + c;
            if (sb.length() + next.length() > TEXT_MAX) {
                break;
            }
            sb.append(next);
        }
        return sb.toString();
    }

    /** 两段订货号文本合并去重 */
    public static String merge(String a, String b) {
        List<String> all = new ArrayList<>(split(a));
        all.addAll(split(b));
        return join(split(String.join(",", all)));
    }

    /** 按订货号认商品：品牌下任一订货号的比较键对上就是它 */
    public Long productOf(Long brandId, Collection<String> codes) {
        List<String> keys = keys(codes);
        if (brandId == null || keys.isEmpty()) {
            return null;
        }
        ProductOrderCodeDO hit = mapper.selectOne(active().eq(ProductOrderCodeDO::getBrandId, brandId)
                .in(ProductOrderCodeDO::getCodeKey, keys).last("LIMIT 1"));
        return hit == null ? null : hit.getProductId();
    }

    public List<String> codesOf(Long productId) {
        return codesOf(List.of(productId)).getOrDefault(productId, List.of());
    }

    public Map<Long, List<String>> codesOf(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        return mapper.selectList(active().in(ProductOrderCodeDO::getProductId, productIds).orderByAsc(ProductOrderCodeDO::getId))
                .stream().collect(Collectors.groupingBy(ProductOrderCodeDO::getProductId, LinkedHashMap::new,
                        Collectors.mapping(ProductOrderCodeDO::getCode, Collectors.toList())));
    }

    /** 补上新出现的订货号：这个商品已有的跳过；同品牌已属于其他商品的也跳过（不抢，由人工处理） */
    public void addAll(Long productId, Long brandId, Collection<String> codes) {
        if (productId == null || brandId == null) {
            return;
        }
        for (String code : codes) {
            String key = MpnNormalizer.normalize(code);
            if (key.isEmpty() || owner(brandId, key) != null) {
                continue;
            }
            insert(productId, brandId, code, key);
        }
    }

    /** 编辑商品时整体替换订货号：去掉的软删除；同一品牌下已属于其他商品的报错 */
    public void replace(Long productId, Long brandId, Collection<String> codes) {
        List<String> wanted = split(String.join(",", codes));
        for (String code : wanted) {
            if (code.length() > CODE_MAX) {
                throw new BizException("订货号不能超过 " + CODE_MAX + " 个字符");
            }
            Long owner = owner(brandId, MpnNormalizer.normalize(code));
            if (owner != null && !owner.equals(productId)) {
                throw new BizException("订货号 " + code + " 已属于同品牌的其他商品");
            }
        }
        List<String> keep = wanted.stream().map(MpnNormalizer::normalize).toList();
        List<ProductOrderCodeDO> current = mapper.selectList(active().eq(ProductOrderCodeDO::getProductId, productId));
        LocalDateTime now = LocalDateTime.now();
        for (ProductOrderCodeDO c : current) {
            if (!keep.contains(c.getCodeKey()) || !Objects.equals(c.getBrandId(), brandId)) {
                mapper.update(null, new LambdaUpdateWrapper<ProductOrderCodeDO>().eq(ProductOrderCodeDO::getId, c.getId())
                        .set(ProductOrderCodeDO::getDeletedAt, now));
            }
        }
        List<String> have = current.stream().filter(c -> Objects.equals(c.getBrandId(), brandId)).map(ProductOrderCodeDO::getCodeKey).toList();
        for (String code : wanted) {
            String key = MpnNormalizer.normalize(code);
            if (!have.contains(key)) {
                insert(productId, brandId, code, key);
            }
        }
    }

    /** 关键字按订货号找商品（品牌不限），给各处搜索用 */
    public List<Long> productsMatching(String keyword, int limit) {
        String key = MpnNormalizer.normalize(keyword);
        if (key.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(active().select(ProductOrderCodeDO::getProductId).like(ProductOrderCodeDO::getCodeKey, key)
                        .last("LIMIT " + limit))
                .stream().map(ProductOrderCodeDO::getProductId).distinct().toList();
    }

    /** 关键字按订货号找到的商品型号的比较键（询价记录按型号比较键检索时用） */
    public List<String> modelKeysMatching(String keyword, int limit) {
        List<Long> ids = productsMatching(keyword, limit);
        if (ids.isEmpty()) {
            return List.of();
        }
        return productMapper.selectList(new LambdaQueryWrapper<ProductDO>().select(ProductDO::getMpnNormalized)
                        .in(ProductDO::getId, ids).isNull(ProductDO::getDeletedAt))
                .stream().map(ProductDO::getMpnNormalized).distinct().toList();
    }

    private Long owner(Long brandId, String key) {
        ProductOrderCodeDO hit = mapper.selectOne(active().eq(ProductOrderCodeDO::getBrandId, brandId)
                .eq(ProductOrderCodeDO::getCodeKey, key).last("LIMIT 1"));
        return hit == null ? null : hit.getProductId();
    }

    private void insert(Long productId, Long brandId, String code, String key) {
        ProductOrderCodeDO row = new ProductOrderCodeDO();
        row.setTenantId(ProductConstants.PLATFORM_TENANT_ID);
        row.setProductId(productId);
        row.setBrandId(brandId);
        row.setCode(code.trim());
        row.setCodeKey(key);
        mapper.insert(row);
    }

    private static List<String> keys(Collection<String> codes) {
        return codes == null ? List.of() : codes.stream().map(MpnNormalizer::normalize).filter(k -> !k.isEmpty()).distinct().toList();
    }

    private static LambdaQueryWrapper<ProductOrderCodeDO> active() {
        return new LambdaQueryWrapper<ProductOrderCodeDO>()
                .eq(ProductOrderCodeDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .isNull(ProductOrderCodeDO::getDeletedAt);
    }
}
