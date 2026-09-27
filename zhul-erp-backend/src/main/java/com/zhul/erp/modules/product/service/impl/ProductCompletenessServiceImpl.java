package com.zhul.erp.modules.product.service.impl;

import com.zhul.erp.modules.product.dto.CompletenessVO;
import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.product.repository.ModuleHit;
import com.zhul.erp.modules.product.repository.ProductCompletenessMapper;
import com.zhul.erp.modules.product.service.ProductCompletenessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProductCompletenessServiceImpl implements ProductCompletenessService {

    static final String BASIC = "basic";

    /** 模块的固定顺序，与 PRD 2.14 一致 */
    static final List<String> MODULES = List.of(BASIC, "media", "specifications", "logistics", "customs",
            "referencePrice", "relationships", "documents", "applications", "faq");

    private final ProductCompletenessMapper completenessMapper;

    @Override
    public Map<Long, CompletenessVO> compute(Collection<ProductDO> products) {
        if (products.isEmpty()) {
            return Map.of();
        }
        Map<Long, Set<String>> doneByProduct = new HashMap<>(products.size() * 2);
        for (ModuleHit hit : completenessMapper.findModuleHits(products.stream().map(ProductDO::getId).toList())) {
            doneByProduct.computeIfAbsent(hit.getProductId(), k -> new HashSet<>()).add(hit.getModuleKey());
        }
        Map<Long, CompletenessVO> result = new HashMap<>(products.size() * 2);
        for (ProductDO product : products) {
            Set<String> done = doneByProduct.getOrDefault(product.getId(), new HashSet<>());
            // 基本信息：品牌、品类、型号已填
            if (basicDone(product)) {
                done.add(BASIC);
            }
            result.put(product.getId(), toVO(done));
        }
        return result;
    }

    private static boolean basicDone(ProductDO product) {
        return product.getBrandId() != null && product.getBrandId() > 0
                && product.getCategoryId() != null && product.getCategoryId() > 0
                && StringUtils.hasText(product.getMpnRaw());
    }

    private static CompletenessVO toVO(Set<String> done) {
        List<CompletenessVO.Module> modules = new ArrayList<>(MODULES.size());
        List<String> missing = new ArrayList<>(MODULES.size());
        for (String key : MODULES) {
            CompletenessVO.Module module = new CompletenessVO.Module();
            module.setKey(key);
            module.setDone(done.contains(key));
            modules.add(module);
            if (!done.contains(key)) {
                missing.add(key);
            }
        }
        CompletenessVO vo = new CompletenessVO();
        vo.setTotal(MODULES.size());
        vo.setDone(MODULES.size() - missing.size());
        vo.setModules(modules);
        vo.setMissing(missing);
        return vo;
    }
}
