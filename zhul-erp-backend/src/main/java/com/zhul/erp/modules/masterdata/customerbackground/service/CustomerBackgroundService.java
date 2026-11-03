package com.zhul.erp.modules.masterdata.customerbackground.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.security.DataScopeResolver;
import com.zhul.erp.framework.security.PermissionChecker;
import com.zhul.erp.framework.storage.FileStorageService;
import com.zhul.erp.framework.storage.StorageArea;
import com.zhul.erp.framework.storage.UploadRules;
import com.zhul.erp.modules.aitask.dto.AiTaskVO;
import com.zhul.erp.modules.aitask.service.AiTaskService;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.masterdata.customerbackground.dto.BackgroundReportDetailVO;
import com.zhul.erp.modules.masterdata.customerbackground.dto.BackgroundReportVO;
import com.zhul.erp.modules.masterdata.customerbackground.dto.BackgroundSuggestion;
import com.zhul.erp.modules.masterdata.customerbackground.entity.CustomerBackgroundReportDO;
import com.zhul.erp.modules.masterdata.customerbackground.repository.CustomerBackgroundReportMapper;
import com.zhul.erp.modules.masterdata.customerbackground.support.BackgroundAdoption;
import com.zhul.erp.modules.masterdata.customerbackground.support.BackgroundFields;
import com.zhul.erp.modules.masterdata.customerbackground.support.BackgroundInput;
import com.zhul.erp.modules.masterdata.customerbackground.support.BackgroundReportHtml;
import com.zhul.erp.modules.document.support.DocumentConverter;
import com.zhul.erp.modules.masterdata.customerbackground.support.PdfText;
import com.zhul.erp.modules.masterdata.customerprofile.entity.CustomerContactDO;
import com.zhul.erp.modules.masterdata.customerprofile.support.ProfileFacts;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.repository.CustomerMapper;
import com.zhul.erp.modules.masterdata.service.CustomerService;
import com.zhul.erp.modules.system.constants.DocumentType;
import com.zhul.erp.modules.system.entity.UserBasicDO;
import com.zhul.erp.modules.system.repository.UserBasicMapper;
import com.zhul.erp.modules.system.service.DocumentNumberService;
import com.zhul.erp.modules.system.service.LogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.zhul.erp.modules.masterdata.customerbackground.constants.BackgroundConstants.ERR_RECENT;
import static com.zhul.erp.modules.masterdata.customerbackground.constants.BackgroundConstants.ERR_RUNNING;
import static com.zhul.erp.modules.masterdata.customerbackground.constants.BackgroundConstants.MAX_PDF_BYTES;
import static com.zhul.erp.modules.masterdata.customerbackground.constants.BackgroundConstants.MAX_TEXT_CHARS;
import static com.zhul.erp.modules.masterdata.customerbackground.constants.BackgroundConstants.PERMISSION;
import static com.zhul.erp.modules.masterdata.customerbackground.constants.BackgroundConstants.REMIND_HOURS;
import static com.zhul.erp.modules.masterdata.customerbackground.constants.BackgroundConstants.SKILL;
import static com.zhul.erp.modules.masterdata.customerbackground.constants.BackgroundConstants.SOURCE_AI;
import static com.zhul.erp.modules.masterdata.customerbackground.constants.BackgroundConstants.SOURCE_UPLOAD;
import static com.zhul.erp.modules.masterdata.customerbackground.constants.BackgroundConstants.STATUS_DONE;
import static com.zhul.erp.modules.masterdata.customerbackground.constants.BackgroundConstants.STATUS_FAILED;
import static com.zhul.erp.modules.masterdata.customerbackground.constants.BackgroundConstants.STATUS_RUNNING;

/**
 * 客户背调（spec crm/customer-background）：发起 AI 背调、上传报告、报告查询、详情、删除。
 * 查看报告的范围与客户相同；结果由 {@link BackgroundResultService} 在 AI 任务结束后保存。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerBackgroundService {

    static final String NOT_FOUND = "背调报告不存在或已删除";

    private final CustomerService customerService;
    private final CustomerMapper customerMapper;
    private final CustomerBackgroundReportMapper reportMapper;
    private final BackgroundInput input;
    private final BackgroundAdoption adoption;
    private final ProfileFacts facts;
    private final AiTaskService aiTaskService;
    private final DocumentNumberService documentNumberService;
    private final FileStorageService fileStorageService;
    private final DocumentConverter converter;
    private final UserBasicMapper userBasicMapper;
    private final CurrentUserResolver currentUser;
    private final DataScopeResolver dataScopeResolver;
    private final PermissionChecker perm;
    private final LogService logService;
    private final ObjectMapper objectMapper;

    // ---------------------------------------------------------------- 发起与上传

    /**
     * 发起 AI 背调。事务边界：锁客户行 → 检查背调中与 24 小时提醒 → 建 AI 任务与报告，一起提交；
     * 任务在事务提交后才发给编排服务（AiTaskService），AI 未配置时抛出提示、不生成报告。
     */
    @Transactional(rollbackFor = Exception.class)
    public BackgroundReportVO research(Long customerId, boolean confirmed) {
        CustomerDO c = lockCustomer(customerId);
        if (!confirmed) {
            remindIfRecent(c.getId());
        }
        Long me = currentUser.resolve();
        AiTaskVO task = aiTaskService.createAndSubmit(SKILL, json(input.research(c)), me);
        CustomerBackgroundReportDO r = newReport(c, SOURCE_AI, task.getId(), me);
        reportMapper.insert(r);
        log.info("发起客户背调，customerId={}, reportNo={}, aiTaskId={}", c.getId(), r.getReportNo(), task.getId());
        operateLog(c, "发起 AI 背调", r);
        return toVo(reportMapper.selectById(r.getId()));
    }

    /**
     * 上传背调报告：PDF（≤10MB，按文件头校验，抽取文字，超过 60000 字截断并提示）或粘贴文字（≤60000 字），二选一。
     * AI 只从文字里提取结论与字段建议，不联网；报告正文为上传的文字，PDF 原件保存在私有存储。
     * 事务边界同发起背调；原件在 AI 任务建好后才写入存储，事务回滚时最多留下一个无引用的文件。
     */
    @Transactional(rollbackFor = Exception.class)
    public BackgroundReportVO upload(Long customerId, MultipartFile file, String text) {
        boolean hasFile = file != null && !file.isEmpty();
        boolean hasText = StringUtils.hasText(text);
        if (hasFile == hasText) {
            throw new BizException("请上传一个 PDF，或粘贴报告文字");
        }
        String reportText;
        String notice = null;
        if (hasFile) {
            UploadRules.check(file, Set.of("pdf"), MAX_PDF_BYTES, "只支持 PDF 文件");
            reportText = PdfText.extract(bytes(file));
            if (reportText.length() > MAX_TEXT_CHARS) {
                reportText = reportText.substring(0, MAX_TEXT_CHARS);
                notice = "报告文字超过 " + MAX_TEXT_CHARS + " 字，只提取了前 " + MAX_TEXT_CHARS + " 字";
            }
        } else {
            reportText = text.strip();
            if (reportText.length() > MAX_TEXT_CHARS) {
                throw new BizException("报告文字不能超过 " + MAX_TEXT_CHARS + " 字");
            }
        }
        CustomerDO c = lockCustomer(customerId);
        Long me = currentUser.resolve();
        AiTaskVO task = aiTaskService.createAndSubmit(SKILL, json(input.extract(reportText)), me);
        CustomerBackgroundReportDO r = newReport(c, SOURCE_UPLOAD, task.getId(), me);
        r.setReportMarkdown(reportText);
        if (hasFile) {
            FileStorageService.StoredFile stored = fileStorageService.storeValidated(StorageArea.CUSTOMER, c.getTenantId(), file, "pdf");
            r.setUploadFileKey(stored.fileKey());
            r.setUploadFileName(stored.fileName());
        }
        reportMapper.insert(r);
        log.info("上传背调报告，customerId={}, reportNo={}, aiTaskId={}", c.getId(), r.getReportNo(), task.getId());
        operateLog(c, "上传背调报告", r);
        BackgroundReportVO vo = toVo(reportMapper.selectById(r.getId()));
        vo.setNotice(notice);
        return vo;
    }

    /** 锁客户行后检查：同一客户同时只能一份背调中（两个人同时发起时，后一个看到的是「背调中」） */
    private CustomerDO lockCustomer(Long customerId) {
        CustomerDO c = customerService.requireInScope(customerId);
        customerMapper.lockById(c.getId());
        if (reportMapper.selectCount(active(c.getId()).eq(CustomerBackgroundReportDO::getStatus, STATUS_RUNNING)) > 0) {
            throw BizException.of(ERR_RUNNING, "这个客户正在背调中，完成后再发起");
        }
        return c;
    }

    private CustomerBackgroundReportDO newReport(CustomerDO c, int source, Long aiTaskId, Long me) {
        CustomerBackgroundReportDO r = new CustomerBackgroundReportDO();
        r.setTenantId(c.getTenantId());
        r.setReportNo(documentNumberService.next(DocumentType.BG));
        r.setCustomerId(c.getId());
        r.setSource(source);
        r.setStatus(STATUS_RUNNING);
        r.setAiTaskId(aiTaskId);
        r.setRequestedBy(me == null ? 0L : me);
        return r;
    }

    /** 24 小时内已有背调（背调中或已完成）时提醒，确认后才发起 */
    private void remindIfRecent(Long customerId) {
        CustomerBackgroundReportDO last = reportMapper.selectOne(active(customerId)
                .ne(CustomerBackgroundReportDO::getStatus, STATUS_FAILED)
                .ge(CustomerBackgroundReportDO::getCreateTime, LocalDateTime.now().minusHours(REMIND_HOURS))
                .orderByDesc(CustomerBackgroundReportDO::getCreateTime)
                .last("LIMIT 1"));
        if (last == null) {
            return;
        }
        long minutes = Math.max(0, Duration.between(last.getCreateTime(), LocalDateTime.now()).toMinutes());
        String ago = minutes < 60 ? "不到 1 小时前" : " " + (minutes / 60) + " 小时前";
        String who = nameOf(last.getRequestedBy());
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("reportId", last.getId());
        detail.put("reportNo", last.getReportNo());
        detail.put("priority", last.getPriority());
        detail.put("score", last.getScore());
        detail.put("inputTokens", last.getInputTokens());
        detail.put("outputTokens", last.getOutputTokens());
        detail.put("webSearches", last.getWebSearches());
        detail.put("hoursAgo", minutes / 60);
        detail.put("requestedByName", who);
        detail.put("createTime", last.getCreateTime());
        throw BizException.of(ERR_RECENT, "这个客户" + ago + "由" + who + "背调过，仍要再背调吗？", detail);
    }

    // ---------------------------------------------------------------- 查询、详情、删除

    /** 历次报告，按时间倒序 */
    public List<BackgroundReportVO> list(Long customerId) {
        CustomerDO c = customerService.requireInScope(customerId);
        List<CustomerBackgroundReportDO> rows = reportMapper.selectList(active(c.getId())
                .select(CustomerBackgroundReportDO.class, f -> !"report_markdown".equals(f.getColumn()))
                .orderByDesc(CustomerBackgroundReportDO::getCreateTime)
                .orderByDesc(CustomerBackgroundReportDO::getId));
        Map<Long, String> names = names(rows.stream().map(CustomerBackgroundReportDO::getRequestedBy).collect(Collectors.toSet()));
        List<BackgroundReportVO> out = new ArrayList<>(rows.size());
        for (CustomerBackgroundReportDO r : rows) {
            BackgroundReportVO vo = new BackgroundReportVO();
            copy(r, vo, names);
            out.add(vo);
        }
        return out;
    }

    public BackgroundReportDetailVO detail(Long customerId, Long reportId) {
        CustomerBackgroundReportDO r = require(customerId, reportId);
        BackgroundReportDetailVO vo = new BackgroundReportDetailVO();
        copy(r, vo, names(Set.of(r.getRequestedBy())));
        vo.setReportMarkdown(r.getReportMarkdown() == null ? "" : r.getReportMarkdown());
        vo.setSuggestions(withCurrent(r));
        vo.setModel(r.getModel());
        vo.setInputTokens(r.getInputTokens());
        vo.setOutputTokens(r.getOutputTokens());
        vo.setWebSearches(r.getWebSearches());
        vo.setDurationSeconds(r.getDurationSeconds());
        vo.setUploadFileName(StringUtils.hasText(r.getUploadFileKey()) ? r.getUploadFileName() : null);
        vo.setCanDelete(canDelete(r));
        return vo;
    }

    /** 字段建议附上中文名、档案当前值、能否采纳与默认勾选（当前值为空、能采纳的待处理建议默认勾选） */
    private List<BackgroundSuggestion> withCurrent(CustomerBackgroundReportDO r) {
        List<BackgroundSuggestion> list = suggestions(r);
        if (list.isEmpty()) {
            return list;
        }
        CustomerDO c = customerService.requireInScope(r.getCustomerId());
        List<CustomerContactDO> contacts = facts.contacts(c.getId());
        Map<Long, String> names = names(list.stream().map(BackgroundSuggestion::getOperatorId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        for (BackgroundSuggestion s : list) {
            s.setLabel(BackgroundFields.label(s.getField()));
            s.setCurrentValue(adoption.current(c, contacts, s));
            if (s.getOperatorId() != null) {
                s.setOperatorName(names.get(s.getOperatorId()));
            }
            if (BackgroundSuggestion.ADOPTED.equals(s.getState())) {
                s.setDefaultChecked(false);
                continue;
            }
            BackgroundAdoption.Resolved v = adoption.resolve(s, contacts);
            s.setUnavailableReason(v.reason());
            s.setDefaultChecked(BackgroundSuggestion.PENDING.equals(s.getState()) && v.ok() && s.getCurrentValue() == null);
        }
        return list;
    }

    /** 导出的 PDF：文件名「{客户名称}_{报告编号}.pdf」 */
    public record ExportedPdf(String fileName, byte[] body) {
    }

    /** 导出 PDF：核心结论、正文与字段采纳情况；HTML 交给 LibreOffice 转换，与 PI、报价单共用转换队列 */
    public ExportedPdf exportPdf(Long customerId, Long reportId) {
        BackgroundReportDetailVO vo = detail(customerId, reportId);
        if (vo.getStatus() != STATUS_DONE) {
            throw new BizException("报告还没有完成，不能导出");
        }
        CustomerDO c = customerService.requireInScope(customerId);
        String html = BackgroundReportHtml.render(c.getName(), vo);
        byte[] pdf = converter.htmlToPdf(html.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String name = (c.getName() + "_" + vo.getReportNo()).replaceAll("[\\\\/:*?\"<>|]+", "_").strip() + ".pdf";
        return new ExportedPdf(name, pdf);
    }

    /** 上传的 PDF 原件：私有存储的临时下载链接 */
    public FileStorageService.FileLink file(Long customerId, Long reportId) {
        CustomerBackgroundReportDO r = require(customerId, reportId);
        if (!StringUtils.hasText(r.getUploadFileKey())) {
            throw new BizException("这份报告没有上传的原件");
        }
        String storage = fileStorageService.locate(StorageArea.CUSTOMER, r.getUploadFileKey(), r.getTenantId());
        return fileStorageService.link(storage, r.getUploadFileKey(), r.getUploadFileName(), true);
    }

    /**
     * 软删除。发起人可以删除自己的报告；其他人需要「客户背调」权限，且数据范围包含发起人（管理者）。
     * 已采纳进档案的字段不受影响，来源仍指向这份报告。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long customerId, Long reportId) {
        CustomerBackgroundReportDO r = require(customerId, reportId);
        if (!canDelete(r)) {
            throw new BizException("只有发起人或管理者可以删除这份报告");
        }
        Long me = currentUser.resolve();
        reportMapper.update(null, new LambdaUpdateWrapper<CustomerBackgroundReportDO>()
                .eq(CustomerBackgroundReportDO::getId, r.getId())
                .isNull(CustomerBackgroundReportDO::getDeletedAt)
                .set(CustomerBackgroundReportDO::getDeletedAt, LocalDateTime.now())
                .set(CustomerBackgroundReportDO::getDeletedBy, me == null ? 0L : me)
                .set(CustomerBackgroundReportDO::getUpdateTime, LocalDateTime.now()));
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("报告编号", r.getReportNo());
        before.put("customerId", r.getCustomerId());
        logService.recordOperateLog("客户管理", "删除背调报告", before, null);
    }

    private boolean canDelete(CustomerBackgroundReportDO r) {
        Long me = currentUser.resolve();
        if (me != null && Objects.equals(me, r.getRequestedBy())) {
            return true;
        }
        return perm.has(PERMISSION) && dataScopeResolver.current().canSee(r.getRequestedBy());
    }

    /** 报告属于这个客户、客户在数据范围内、报告未删除 */
    CustomerBackgroundReportDO require(Long customerId, Long reportId) {
        CustomerDO c = customerService.requireInScope(customerId);
        CustomerBackgroundReportDO r = reportMapper.selectById(reportId);
        if (r == null || r.getDeletedAt() != null || !Objects.equals(r.getCustomerId(), c.getId())
                || !Objects.equals(r.getTenantId(), c.getTenantId())) {
            throw new BizException(NOT_FOUND);
        }
        return r;
    }

    // ---------------------------------------------------------------- 组装

    private static LambdaQueryWrapper<CustomerBackgroundReportDO> active(Long customerId) {
        return new LambdaQueryWrapper<CustomerBackgroundReportDO>()
                .eq(CustomerBackgroundReportDO::getCustomerId, customerId)
                .isNull(CustomerBackgroundReportDO::getDeletedAt);
    }

    List<BackgroundSuggestion> suggestions(CustomerBackgroundReportDO r) {
        if (!StringUtils.hasText(r.getSuggestions())) {
            return List.of();
        }
        try {
            return objectMapper.readValue(r.getSuggestions(), new TypeReference<List<BackgroundSuggestion>>() { });
        } catch (JsonProcessingException e) {
            throw new BizException("背调报告的字段建议无法读取", e);
        }
    }

    private void copy(CustomerBackgroundReportDO r, BackgroundReportVO vo, Map<Long, String> names) {
        vo.setId(r.getId());
        vo.setReportNo(r.getReportNo());
        vo.setCustomerId(r.getCustomerId());
        vo.setSource(r.getSource());
        vo.setStatus(r.getStatus());
        vo.setFailReason(r.getFailReason());
        vo.setPriority(r.getPriority());
        vo.setScore(r.getScore());
        vo.setMainConflict(r.getMainConflict());
        vo.setFirstAction(r.getFirstAction());
        vo.setIdentityNote(r.getIdentityNote());
        vo.setSupplyNote(r.getSupplyNote());
        vo.setKeyFinding(r.getKeyFinding());
        vo.setObstacle(r.getObstacle());
        vo.setSuggestionCount(suggestions(r).size());
        vo.setRequestedBy(r.getRequestedBy());
        vo.setRequestedByName(names.getOrDefault(r.getRequestedBy(), nameOf(0L)));
        vo.setCreateTime(r.getCreateTime());
        vo.setCompletedAt(r.getCompletedAt());
    }

    private BackgroundReportVO toVo(CustomerBackgroundReportDO r) {
        BackgroundReportVO vo = new BackgroundReportVO();
        copy(r, vo, names(Set.of(r.getRequestedBy())));
        return vo;
    }

    private Map<Long, String> names(Set<Long> ids) {
        Set<Long> real = new HashSet<>(ids);
        real.remove(null);
        real.remove(0L);
        Map<Long, String> out = new HashMap<>();
        if (!real.isEmpty()) {
            for (UserBasicDO u : userBasicMapper.selectBatchIds(real)) {
                out.put(Long.valueOf(u.getId()), u.getName());
            }
        }
        real.forEach(id -> out.putIfAbsent(id, "已删除的用户"));
        return out;
    }

    private String nameOf(Long userId) {
        if (userId == null || userId == 0) {
            return "系统";
        }
        return names(Set.of(userId)).get(userId);
    }

    private void operateLog(CustomerDO c, String operation, CustomerBackgroundReportDO r) {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("customerCode", c.getCustomerCode());
        after.put("报告编号", r.getReportNo());
        logService.recordOperateLog("客户管理", operation, null, after);
    }

    private static byte[] bytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("读取上传文件失败", e);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BizException("背调输入组装失败", e);
        }
    }
}
