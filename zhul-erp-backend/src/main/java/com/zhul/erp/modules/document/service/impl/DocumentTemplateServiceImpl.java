package com.zhul.erp.modules.document.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.storage.PrivateFileStorage;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.document.constants.DocTypes;
import com.zhul.erp.modules.document.dto.SaveTextTemplateRequest;
import com.zhul.erp.modules.document.dto.TemplateFile;
import com.zhul.erp.modules.document.dto.TemplateTypeVO;
import com.zhul.erp.modules.document.dto.TemplateVersionVO;
import com.zhul.erp.modules.document.entity.DocumentTemplateDO;
import com.zhul.erp.modules.document.entity.DocumentTemplateVersionDO;
import com.zhul.erp.modules.document.repository.DocumentTemplateMapper;
import com.zhul.erp.modules.document.repository.DocumentTemplateVersionMapper;
import com.zhul.erp.modules.document.service.DocumentTemplateService;
import com.zhul.erp.modules.document.support.DocumentConverter;
import com.zhul.erp.modules.document.support.SampleData;
import com.zhul.erp.modules.document.support.TemplateProblem;
import com.zhul.erp.modules.document.support.TextTemplateEngine;
import com.zhul.erp.modules.document.support.XlsxRenderer;
import com.zhul.erp.modules.document.support.XlsxTemplateInspector;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.system.service.LogService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DocumentTemplateServiceImpl implements DocumentTemplateService {

    private static final String MENU = "单据模版";
    private static final String XLSX_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final DocumentTemplateMapper templateMapper;
    private final DocumentTemplateVersionMapper versionMapper;
    private final PrivateFileStorage fileStorage;
    private final DocumentConverter converter;
    private final CurrentUserResolver currentUser;
    private final InquiryLookups lookups;
    private final LogService logService;

    @Override
    public List<TemplateTypeVO> types() {
        List<TemplateTypeVO> list = new ArrayList<>();
        for (int type : DocTypes.ALL) {
            List<DocumentTemplateVersionDO> versions = visibleVersions(type);
            DocumentTemplateVersionDO def = defaultVersion(type, versions);
            TemplateTypeVO vo = new TemplateTypeVO();
            vo.setDocType(type);
            vo.setName(DocTypes.NAMES.get(type));
            vo.setGeneratable(DocTypes.GENERATABLE.contains(type));
            vo.setDefaultVersionNo(def == null ? null : def.getVersionNo());
            vo.setVersionCount(versions.size());
            list.add(vo);
        }
        return list;
    }

    @Override
    public List<TemplateVersionVO> versions(int docType) {
        requireType(docType);
        List<DocumentTemplateVersionDO> versions = visibleVersions(docType);
        DocumentTemplateVersionDO def = defaultVersion(docType, versions);
        DocumentTemplateDO own = ownTemplate(docType);
        Map<Long, String> names = lookups.userNames(versions.stream().map(DocumentTemplateVersionDO::getUploadedBy).toList());
        return versions.stream()
                .sorted(Comparator.comparing(DocumentTemplateVersionDO::getVersionNo).reversed())
                .map(v -> toVo(v, def, own, names))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TemplateVersionVO upload(int docType, MultipartFile file, String note) {
        requireType(docType);
        if (DocTypes.isText(docType)) {
            throw new BizException("文字报价模版请直接编辑文本，不需要上传文件");
        }
        String cleanNote = requireNote(note);
        byte[] bytes;
        try {
            bytes = file == null ? null : file.getBytes();
        } catch (IOException e) {
            throw new BizException("读取上传文件失败，请重新上传", e);
        }
        if (bytes == null || bytes.length == 0) {
            throw new BizException("请选择要上传的模版文件");
        }
        if (bytes.length > DocTypes.MAX_BYTES) {
            throw new BizException("模版文件不能超过 5MB");
        }
        String ext = PrivateFileStorage.detect(java.util.Arrays.copyOf(bytes, Math.min(bytes.length, 64)), file.getOriginalFilename());
        if (!"xlsx".equals(ext)) {
            throw new BizException("模版文件需要是 Excel（xlsx）");
        }
        List<TemplateProblem> problems = inspect(docType, bytes);
        if (!problems.isEmpty()) {
            throw BizException.of("TEMPLATE_INVALID", "模版有 " + problems.size() + " 处问题，修改后重新上传",
                    problems.stream().map(TemplateProblem::toString).toList());
        }
        PrivateFileStorage.StoredFile stored = fileStorage.store(DocTypes.MODULE, tenantId(), file, Set.of("xlsx"),
                DocTypes.MAX_BYTES, "模版文件需要是 Excel（xlsx）");
        DocumentTemplateVersionDO v = newVersion(docType, cleanNote);
        v.setFileKey(stored.fileKey());
        v.setFileName(stored.fileName());
        versionMapper.insert(v);
        logService.recordOperateLog(MENU, "上传模版版本", null,
                Map.of("docType", DocTypes.NAMES.get(docType), "version", "V" + v.getVersionNo(), "note", cleanNote));
        TemplateVersionVO vo = toVo(v, null, ownTemplate(docType), lookups.userNames(List.of(v.getUploadedBy())));
        vo.setWarnings(warnings(docType, bytes));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TemplateVersionVO saveText(SaveTextTemplateRequest req) {
        List<TemplateProblem> problems = TextTemplateEngine.validate(req.getContent());
        if (!problems.isEmpty()) {
            throw BizException.of("TEMPLATE_INVALID", "模版有 " + problems.size() + " 处问题，修改后再保存",
                    problems.stream().map(TemplateProblem::toString).toList());
        }
        DocumentTemplateVersionDO v = newVersion(DocTypes.TEXT_QUOTE, requireNote(req.getNote()));
        v.setContent(req.getContent());
        versionMapper.insert(v);
        logService.recordOperateLog(MENU, "保存模版版本", null,
                Map.of("docType", DocTypes.NAMES.get(DocTypes.TEXT_QUOTE), "version", "V" + v.getVersionNo(), "note", v.getNote()));
        return toVo(v, null, ownTemplate(DocTypes.TEXT_QUOTE), lookups.userNames(List.of(v.getUploadedBy())));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long versionId) {
        DocumentTemplateVersionDO v = visibleVersion(versionId);
        DocumentTemplateDO own = ensureOwnTemplate(v.getDocType());
        if (!enabled(v, own)) {
            throw new BizException("这个版本已停用，请先启用再设为默认");
        }
        DocumentTemplateVersionDO before = defaultVersion(v.getDocType(), visibleVersions(v.getDocType()));
        own.setDefaultVersionId(v.getId());
        templateMapper.updateById(own);
        logService.recordOperateLog(MENU, "设为默认版本",
                Map.of("docType", DocTypes.NAMES.get(v.getDocType()), "version", before == null ? "无" : "V" + before.getVersionNo()),
                Map.of("docType", DocTypes.NAMES.get(v.getDocType()), "version", "V" + v.getVersionNo()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setEnabled(Long versionId, boolean enabled) {
        DocumentTemplateVersionDO v = visibleVersion(versionId);
        DocumentTemplateDO own = ensureOwnTemplate(v.getDocType());
        DocumentTemplateVersionDO def = defaultVersion(v.getDocType(), visibleVersions(v.getDocType()));
        if (!enabled && def != null && def.getId().equals(v.getId())) {
            throw new BizException("这是当前默认版本，请先把其他版本设为默认");
        }
        if (isBuiltin(v)) {
            own.setBuiltinDisabled(enabled ? 0 : 1);
            templateMapper.updateById(own);
        } else {
            v.setStatus(enabled ? 1 : 0);
            versionMapper.updateById(v);
        }
        logService.recordOperateLog(MENU, enabled ? "启用模版版本" : "停用模版版本", null,
                Map.of("docType", DocTypes.NAMES.get(v.getDocType()), "version", "V" + v.getVersionNo()));
    }

    @Override
    public TemplateFile download(Long versionId) {
        DocumentTemplateVersionDO v = visibleVersion(versionId);
        if (DocTypes.isText(v.getDocType())) {
            return new TemplateFile("文字报价模版-V" + v.getVersionNo() + ".txt", "text/plain;charset=UTF-8",
                    v.getContent() == null ? new byte[0] : v.getContent().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return new TemplateFile(downloadName(v, "xlsx"), XLSX_TYPE, fileOf(v));
    }

    @Override
    public TemplateFile previewExport(Long versionId, String format) {
        DocumentTemplateVersionDO v = visibleVersion(versionId);
        if (!DocTypes.GENERATABLE.contains(v.getDocType())) {
            throw new BizException(DocTypes.NAMES.get(v.getDocType()) + " 暂时不能生成预览，可以下载模版查看");
        }
        if (DocTypes.isText(v.getDocType())) {
            String text = TextTemplateEngine.render(v.getContent(), SampleData.quotation());
            return new TemplateFile("文字报价预览-V" + v.getVersionNo() + ".txt", "text/plain;charset=UTF-8",
                    text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        byte[] xlsx = switch (v.getDocType()) {
            case DocTypes.PI -> XlsxRenderer.render(fileOf(v), DocTypes.PI, SampleData.pi());
            case DocTypes.CI -> XlsxRenderer.render(fileOf(v), DocTypes.CI, SampleData.ci());
            case DocTypes.PL -> XlsxRenderer.render(fileOf(v), DocTypes.PL, SampleData.pl());
            default -> XlsxRenderer.render(fileOf(v), SampleData.quotation());
        };
        if ("pdf".equalsIgnoreCase(format)) {
            return new TemplateFile(downloadName(v, "pdf").replace("模版", "预览"), "application/pdf", converter.toPdf(xlsx));
        }
        return new TemplateFile(downloadName(v, "xlsx").replace("模版", "预览"), XLSX_TYPE, xlsx);
    }

    @Override
    public Resolved resolveDefault(int docType) {
        DocumentTemplateVersionDO def = defaultVersion(docType, visibleVersions(docType));
        if (def == null) {
            throw new BizException("还没有可用的" + DocTypes.NAMES.get(docType) + "模版，请联系管理员上传");
        }
        if (DocTypes.isText(docType)) {
            return new Resolved(def.getId(), def.getVersionNo(), null, def.getContent());
        }
        return new Resolved(def.getId(), def.getVersionNo(), fileOf(def), null);
    }

    // ---------------------------------------------------------------- 内部

    private List<TemplateProblem> inspect(int docType, byte[] bytes) {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            return XlsxTemplateInspector.inspect(wb, docType, true).problems();
        } catch (IOException | RuntimeException e) {
            return List.of(new TemplateProblem("", "文件无法打开，请确认是有效的 Excel（xlsx）文件"));
        }
    }

    private static List<String> warnings(int docType, byte[] bytes) {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            return XlsxTemplateInspector.hasPageNumber(wb) ? List.of()
                    : List.of("页眉页脚里没有页码，多页单据无法显示「第几页 / 共几页」，可在 Excel 页面设置里加上");
        } catch (IOException e) {
            return List.of();
        }
    }

    /** 平台内置版本 + 本租户上传的版本 */
    private List<DocumentTemplateVersionDO> visibleVersions(int docType) {
        int tenant = tenantId();
        return versionMapper.selectList(new LambdaQueryWrapper<DocumentTemplateVersionDO>()
                .eq(DocumentTemplateVersionDO::getDocType, docType)
                .and(w -> w.eq(DocumentTemplateVersionDO::getTenantId, tenant)
                        .or(x -> x.eq(DocumentTemplateVersionDO::getTenantId, DocTypes.PLATFORM_TENANT)
                                .eq(DocumentTemplateVersionDO::getBuiltin, 1))));
    }

    private DocumentTemplateVersionDO visibleVersion(Long versionId) {
        DocumentTemplateVersionDO v = versionId == null ? null : versionMapper.selectById(versionId);
        if (v == null || !(Objects.equals(v.getTenantId(), tenantId())
                || (Objects.equals(v.getTenantId(), DocTypes.PLATFORM_TENANT) && Objects.equals(v.getBuiltin(), 1)))) {
            throw new BizException("模版版本不存在");
        }
        return v;
    }

    /** 租户指定了默认版本用它；否则用平台内置版本（平台设置的默认） */
    private DocumentTemplateVersionDO defaultVersion(int docType, List<DocumentTemplateVersionDO> versions) {
        DocumentTemplateDO own = ownTemplate(docType);
        Long id = own != null && own.getDefaultVersionId() != null ? own.getDefaultVersionId() : platformDefaultId(docType);
        return versions.stream().filter(v -> v.getId().equals(id)).findFirst().orElse(null);
    }

    private Long platformDefaultId(int docType) {
        DocumentTemplateDO platform = templateMapper.selectOne(new LambdaQueryWrapper<DocumentTemplateDO>()
                .eq(DocumentTemplateDO::getTenantId, DocTypes.PLATFORM_TENANT)
                .eq(DocumentTemplateDO::getDocType, docType));
        return platform == null ? null : platform.getDefaultVersionId();
    }

    private DocumentTemplateDO ownTemplate(int docType) {
        if (tenantId() == DocTypes.PLATFORM_TENANT) {
            return null;
        }
        return templateMapper.selectOne(new LambdaQueryWrapper<DocumentTemplateDO>()
                .eq(DocumentTemplateDO::getTenantId, tenantId())
                .eq(DocumentTemplateDO::getDocType, docType));
    }

    private DocumentTemplateDO ensureOwnTemplate(int docType) {
        if (tenantId() == DocTypes.PLATFORM_TENANT) {
            throw new BizException("平台内置模版不能修改，请用租户账号维护本公司模版");
        }
        DocumentTemplateDO own = ownTemplate(docType);
        if (own != null) {
            return own;
        }
        own = new DocumentTemplateDO();
        own.setTenantId(tenantId());
        own.setDocType(docType);
        own.setDefaultVersionId(platformDefaultId(docType));
        own.setBuiltinDisabled(0);
        own.setStatus(1);
        templateMapper.insert(own);
        return own;
    }

    private DocumentTemplateVersionDO newVersion(int docType, String note) {
        ensureOwnTemplate(docType);
        int maxNo = versionMapper.selectList(new LambdaQueryWrapper<DocumentTemplateVersionDO>()
                        .select(DocumentTemplateVersionDO::getVersionNo)
                        .eq(DocumentTemplateVersionDO::getTenantId, tenantId())
                        .eq(DocumentTemplateVersionDO::getDocType, docType))
                .stream().mapToInt(DocumentTemplateVersionDO::getVersionNo).max().orElse(1);
        DocumentTemplateVersionDO v = new DocumentTemplateVersionDO();
        v.setTenantId(tenantId());
        v.setDocType(docType);
        v.setVersionNo(maxNo + 1);
        v.setNote(note);
        v.setFileKey("");
        v.setFileName("");
        v.setUploadedBy(currentUser.resolve() == null ? 0L : currentUser.resolve());
        v.setBuiltin(0);
        v.setStatus(1);
        return v;
    }

    private boolean enabled(DocumentTemplateVersionDO v, DocumentTemplateDO own) {
        if (isBuiltin(v)) {
            return own == null || !Objects.equals(own.getBuiltinDisabled(), 1);
        }
        return Objects.equals(v.getStatus(), 1);
    }

    private static boolean isBuiltin(DocumentTemplateVersionDO v) {
        return Objects.equals(v.getBuiltin(), 1);
    }

    private byte[] fileOf(DocumentTemplateVersionDO v) {
        String key = v.getFileKey();
        try {
            if (key != null && key.startsWith(DocTypes.CLASSPATH_PREFIX)) {
                try (InputStream in = new ClassPathResource(key.substring(DocTypes.CLASSPATH_PREFIX.length())).getInputStream()) {
                    return in.readAllBytes();
                }
            }
            return Files.readAllBytes(fileStorage.resolveOwned(DocTypes.MODULE, key, v.getTenantId()));
        } catch (IOException e) {
            throw new BizException("模版文件不存在，请重新上传", e);
        }
    }

    private static String downloadName(DocumentTemplateVersionDO v, String ext) {
        String type = DocTypes.NAMES.get(v.getDocType()).split(" ")[0];
        return type + "模版-V" + v.getVersionNo() + "." + ext;
    }

    private TemplateVersionVO toVo(DocumentTemplateVersionDO v, DocumentTemplateVersionDO def, DocumentTemplateDO own,
                                   Map<Long, String> names) {
        TemplateVersionVO vo = new TemplateVersionVO();
        vo.setId(v.getId());
        vo.setDocType(v.getDocType());
        vo.setVersionNo(v.getVersionNo());
        vo.setNote(v.getNote());
        vo.setFileName(v.getFileName());
        vo.setContent(v.getContent());
        vo.setBuiltin(isBuiltin(v));
        vo.setEnabled(enabled(v, own));
        vo.setIsDefault(def != null && def.getId().equals(v.getId()));
        vo.setUploadedByName(isBuiltin(v) ? "系统" : names.get(v.getUploadedBy()));
        vo.setCreateTime(v.getCreateTime());
        return vo;
    }

    private static String requireNote(String note) {
        String n = note == null ? "" : note.trim();
        if (n.isEmpty()) {
            throw new BizException("请填写版本说明");
        }
        if (n.length() > 200) {
            throw new BizException("版本说明不能超过 200 字");
        }
        return n;
    }

    private static void requireType(int docType) {
        if (!DocTypes.ALL.contains(docType)) {
            throw new BizException("单据类型不存在");
        }
    }

    private static int tenantId() {
        Integer t = TenantContext.getTenantId();
        return t == null ? 0 : t;
    }
}
