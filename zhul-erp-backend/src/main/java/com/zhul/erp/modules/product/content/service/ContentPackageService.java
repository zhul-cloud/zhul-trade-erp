package com.zhul.erp.modules.product.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.product.candidate.entity.ProductCandidateDO;
import com.zhul.erp.modules.product.candidate.repository.ProductCandidateMapper;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.constants.ProductErrorCodes;
import com.zhul.erp.modules.product.content.constants.ContentConstants;
import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.product.entity.ProductSeriesDO;
import com.zhul.erp.modules.product.entity.ProductSpecificationDO;
import com.zhul.erp.modules.product.repository.ProductMapper;
import com.zhul.erp.modules.product.repository.ProductSeriesMapper;
import com.zhul.erp.modules.product.repository.ProductSpecificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** 任务包：商品已有信息（生成输入）+ 上传模版，一个商品一个 Markdown，多个打成 zip */
@Service
@RequiredArgsConstructor
public class ContentPackageService {

    private static final Map<Integer, String> LIFECYCLE = Map.of(1, "在产", 2, "现行", 3, "旧款", 4, "已停产",
            5, "停产无替代", 6, "未知");

    private final ProductMapper productMapper;
    private final ProductSeriesMapper seriesMapper;
    private final ProductSpecificationMapper specificationMapper;
    private final ProductCandidateMapper candidateMapper;
    private final ContentTaskService taskService;

    public record Package(String fileName, String contentType, byte[] body) {
    }

    public Package build(List<Long> productIds) {
        Set<Long> ids = new LinkedHashSet<>(productIds == null ? List.of() : productIds);
        ids.remove(null);
        if (ids.isEmpty()) {
            throw BizException.of(ProductErrorCodes.PARAM_INVALID, "请选择商品");
        }
        if (ids.size() > ContentConstants.MAX_PACKAGE_PRODUCTS) {
            throw BizException.of(ProductErrorCodes.PARAM_INVALID,
                    "一次最多下载 " + ContentConstants.MAX_PACKAGE_PRODUCTS + " 个任务包");
        }
        List<ProductDO> products = productMapper.selectList(new LambdaQueryWrapper<ProductDO>()
                .eq(ProductDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .in(ProductDO::getId, ids)
                .isNull(ProductDO::getDeletedAt)
                .orderByAsc(ProductDO::getId));
        if (products.size() != ids.size()) {
            throw BizException.of(ProductErrorCodes.PRODUCT_NOT_FOUND, "商品不存在");
        }
        String template = template();
        Map<Long, String> brands = taskService.brandNames(new HashSet<>(products.stream().map(ProductDO::getBrandId).toList()));
        Map<Long, String> categories = taskService.categoryNames(new HashSet<>(products.stream().map(ProductDO::getCategoryId).toList()));

        taskService.markDownloaded(ids);
        if (products.size() == 1) {
            ProductDO p = products.get(0);
            return new Package(fileName(p), "text/markdown; charset=UTF-8",
                    markdown(p, brands, categories, template).getBytes(StandardCharsets.UTF_8));
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            Set<String> names = new HashSet<>();
            for (ProductDO p : products) {
                String name = fileName(p);
                if (!names.add(name)) {
                    name = name.replace(".md", "-" + p.getId() + ".md");
                }
                zip.putNextEntry(new ZipEntry(name));
                zip.write(markdown(p, brands, categories, template).getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("任务包打包失败", e);
        }
        return new Package("content-tasks-" + products.size() + ".zip", "application/zip", out.toByteArray());
    }

    String markdown(ProductDO p, Map<Long, String> brands, Map<Long, String> categories, String template) {
        String brand = brands.getOrDefault(p.getBrandId(), "");
        StringBuilder sb = new StringBuilder();
        sb.append("---\n")
                .append("brand: ").append(brand).append('\n')
                .append("model: ").append(p.getMpnRaw()).append('\n')
                .append("lang: en          # 改成 zh / en / ru，一种语言一个文件\n")
                .append("---\n\n");
        sb.append("<!-- 商品信息（生成内容的输入，上传时可以保留或删除）\n");
        sb.append("品牌：").append(brand).append('\n');
        sb.append("型号：").append(p.getMpnRaw());
        if (StringUtils.hasText(p.getMpnDisplay()) && !p.getMpnDisplay().equals(p.getMpnRaw())) {
            sb.append("（展示型号 ").append(p.getMpnDisplay()).append('）');
        }
        sb.append('\n');
        line(sb, "名称", p.getProductName());
        line(sb, "品类", categories.get(p.getCategoryId()));
        if (p.getSeriesId() != null) {
            ProductSeriesDO series = seriesMapper.selectById(p.getSeriesId());
            line(sb, "系列", series == null ? null : series.getSeriesName());
        }
        line(sb, "生命周期", LIFECYCLE.get(p.getLifecycleStatus()));
        line(sb, "简短描述", p.getShortDescription());
        line(sb, "规格摘要", p.getSpecSummary());
        List<ProductSpecificationDO> specs = specificationMapper.selectList(new LambdaQueryWrapper<ProductSpecificationDO>()
                .eq(ProductSpecificationDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductSpecificationDO::getProductId, p.getId())
                .eq(ProductSpecificationDO::getLang, "en")
                .isNull(ProductSpecificationDO::getDeletedAt)
                .orderByAsc(ProductSpecificationDO::getSortOrder));
        if (!specs.isEmpty()) {
            sb.append("已有规格：\n");
            for (ProductSpecificationDO s : specs) {
                sb.append("- ").append(s.getSpecLabel()).append(": ").append(s.getSpecValue());
                if (StringUtils.hasText(s.getSpecUnit())) {
                    sb.append(' ').append(s.getSpecUnit());
                }
                sb.append(s.getVerified() != null && s.getVerified() == 1 ? "（已核实）" : "").append('\n');
            }
        }
        List<ProductCandidateDO> candidates = candidateMapper.selectList(new LambdaQueryWrapper<ProductCandidateDO>()
                .select(ProductCandidateDO::getDescription, ProductCandidateDO::getDescriptionEn)
                .eq(ProductCandidateDO::getProductId, p.getId())
                .isNull(ProductCandidateDO::getDeletedAt));
        Set<String> descriptions = new LinkedHashSet<>();
        for (ProductCandidateDO c : candidates) {
            if (StringUtils.hasText(c.getDescription())) {
                descriptions.add(c.getDescription().trim());
            }
            if (StringUtils.hasText(c.getDescriptionEn())) {
                descriptions.add(c.getDescriptionEn().trim());
            }
        }
        if (!descriptions.isEmpty()) {
            sb.append("询盘描述：\n");
            descriptions.forEach(d -> sb.append("- ").append(d.replace("-->", "—>")).append('\n'));
        }
        sb.append("-->\n\n");
        sb.append("# ").append(brand).append(' ').append(p.getMpnRaw()).append("\n\n");
        sb.append(template);
        return sb.toString();
    }

    private static void line(StringBuilder sb, String label, String value) {
        if (StringUtils.hasText(value)) {
            sb.append(label).append('：').append(value.replace("-->", "—>")).append('\n');
        }
    }

    private static String fileName(ProductDO p) {
        return p.getMpnRaw().replaceAll("[\\\\/:*?\"<>|\\s]+", "_") + ".md";
    }

    private static String template() {
        try (InputStream in = new ClassPathResource("product-content/template.md").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("任务包模版读取失败", e);
        }
    }
}
