package com.zhul.erp.modules.video.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.product.dto.ProductMatchVO;
import com.zhul.erp.modules.product.dto.ProductOptionVO;
import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.product.repository.ProductMapper;
import com.zhul.erp.modules.product.service.ProductLookupService;
import com.zhul.erp.modules.product.support.MpnNormalizer;
import com.zhul.erp.modules.video.dto.FolderMatchVO;
import com.zhul.erp.modules.video.entity.VideoTaskDO;
import com.zhul.erp.modules.video.repository.VideoTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 批量上传的文件夹匹配（spec「批量上传」）：子文件夹名去掉首尾空格、不区分大小写按型号匹配本公司可选用的商品；
 * 写成「品牌_型号」时按品牌加型号匹配；只按型号匹配到多个商品时为「有多个商品」，由运营选择。
 */
@Service
@RequiredArgsConstructor
public class VideoFolderMatcher {

    public static final String MATCHED = "matched";
    public static final String MULTIPLE = "multiple";
    public static final String NONE = "none";

    private final ProductLookupService productLookup;
    private final VideoTaskMapper taskMapper;
    private final ProductMapper productMapper;

    public List<FolderMatchVO> match(List<String> folders) {
        List<FolderMatchVO> out = new ArrayList<>(folders.size());
        for (String raw : folders) {
            FolderMatchVO vo = new FolderMatchVO();
            vo.setFolder(raw == null ? "" : raw);
            List<ProductOptionVO> products = products(raw == null ? "" : raw.trim());
            vo.setProducts(products);
            vo.setResult(products.isEmpty() ? NONE : products.size() == 1 ? MATCHED : MULTIPLE);
            out.add(vo);
        }
        Set<Long> matched = out.stream().filter(v -> MATCHED.equals(v.getResult())).map(v -> v.getProducts().get(0).getId())
                .collect(Collectors.toSet());
        Set<Long> withTask = matched.isEmpty() ? Set.of() : taskMapper.selectList(new LambdaQueryWrapper<VideoTaskDO>()
                        .select(VideoTaskDO::getProductId)
                        .eq(VideoTaskDO::getTenantId, TenantContext.getTenantId())
                        .in(VideoTaskDO::getProductId, matched)
                        .isNull(VideoTaskDO::getDeletedAt)).stream()
                .map(VideoTaskDO::getProductId).collect(Collectors.toSet());
        out.stream().filter(v -> MATCHED.equals(v.getResult()))
                .forEach(v -> v.setHasTask(withTask.contains(v.getProducts().get(0).getId())));
        return out;
    }

    /** 这个名称对应的商品：先按「品牌_型号」，再按整个名称作型号 */
    List<ProductOptionVO> products(String name) {
        if (name.isEmpty()) {
            return List.of();
        }
        int sep = name.indexOf('_');
        if (sep > 0 && sep < name.length() - 1) {
            ProductMatchVO m = productLookup.match(name.substring(0, sep).trim(), name.substring(sep + 1).trim());
            if (m.getExact() != null) {
                return List.of(m.getExact());
            }
        }
        return sameModel(name);
    }

    /** 归一化型号完全相同的商品（按商品去重） */
    private List<ProductOptionVO> sameModel(String model) {
        String n = MpnNormalizer.normalize(model);
        if (n.isEmpty()) {
            return List.of();
        }
        ProductMatchVO m = productLookup.match(null, model);
        if (m.getCandidates().isEmpty()) {
            return List.of();
        }
        Set<Long> same = productMapper.selectList(new LambdaQueryWrapper<ProductDO>()
                        .select(ProductDO::getId)
                        .in(ProductDO::getId, m.getCandidates().stream().map(ProductOptionVO::getId).toList())
                        .eq(ProductDO::getMpnNormalized, n)).stream()
                .map(ProductDO::getId).collect(Collectors.toSet());
        Map<Long, ProductOptionVO> out = new LinkedHashMap<>();
        for (ProductOptionVO o : m.getCandidates()) {
            if (same.contains(o.getId())) {
                out.putIfAbsent(o.getId(), o);
            }
        }
        return new ArrayList<>(out.values());
    }
}
