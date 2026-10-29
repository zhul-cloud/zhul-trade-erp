package com.zhul.erp.modules.system.migration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.modules.system.constants.DocumentType;
import com.zhul.erp.modules.system.migration.entity.MigrationIdMapDO;
import com.zhul.erp.modules.system.migration.repository.MigrationIdMapMapper;
import com.zhul.erp.modules.system.service.DocumentNumberService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一次迁移执行的上下文：目标租户、批次、干跑标记、新旧编号对照与计数。
 * 新旧编号对照按「源表 + 旧编号 + 目标表」查找，已有有效对照的源记录在重复执行时跳过。
 */
public class MigrationRun {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    final int tenantId;
    final long batchId;
    final boolean dryRun;
    final Map<String, JsonNode> bundle;
    final String attachmentDir;
    private final MigrationIdMapMapper idMapMapper;
    private final DocumentNumberService numbers;
    /** 源表|旧编号|目标表 → 目标ID（含本次新建的） */
    private final Map<String, Long> ids = new HashMap<>();
    /** 阶段 → 数据类别 → { created, skipped, failed } */
    final Map<String, Map<String, int[]>> stats = new LinkedHashMap<>();
    /** 姓名 → 用户ID、用户名 */
    final Map<String, Long> userIds = new HashMap<>();
    final Map<String, String> usernames = new HashMap<>();
    /** 本次涉及的询盘型号行（新建或已有），候选入池阶段按它们重算 */
    final List<Long> inquiryItemIds = new java.util.ArrayList<>();
    private int dryNo;

    MigrationRun(int tenantId, long batchId, boolean dryRun, Map<String, JsonNode> bundle, String attachmentDir,
                 MigrationIdMapMapper idMapMapper, DocumentNumberService numbers) {
        this.tenantId = tenantId;
        this.batchId = batchId;
        this.dryRun = dryRun;
        this.bundle = bundle;
        this.attachmentDir = attachmentDir;
        this.idMapMapper = idMapMapper;
        this.numbers = numbers;
        for (MigrationIdMapDO m : idMapMapper.selectList(new LambdaQueryWrapper<MigrationIdMapDO>()
                .eq(MigrationIdMapDO::getTenantId, tenantId)
                .eq(MigrationIdMapDO::getStatus, 1))) {
            ids.put(key(m.getSourceTable(), m.getSourceCode(), m.getTargetTable()), m.getTargetId());
        }
    }

    private static String key(String sourceTable, String code, String targetTable) {
        return sourceTable + "|" + code + "|" + targetTable;
    }

    /** 已迁移的目标ID；没有返回 null */
    Long mapped(String sourceTable, String code, String targetTable) {
        return code == null ? null : ids.get(key(sourceTable, code, targetTable));
    }

    /** 记下新旧编号对照（干跑也记在内存里，供后续阶段引用；数据库里的对照随事务回滚） */
    void record(String sourceTable, String code, String targetTable, Long targetId) {
        ids.put(key(sourceTable, code, targetTable), targetId);
        MigrationIdMapDO m = new MigrationIdMapDO();
        m.setTenantId(tenantId);
        m.setBatchId(batchId);
        m.setSourceTable(sourceTable);
        m.setSourceCode(code.length() > 64 ? code.substring(0, 64) : code);
        m.setTargetTable(targetTable);
        m.setTargetId(targetId);
        m.setStatus(1);
        idMapMapper.insert(m);
    }

    void created(String stage, String kind) {
        counter(stage, kind)[0]++;
    }

    void created(String stage, String kind, int n) {
        counter(stage, kind)[0] += n;
    }

    void skipped(String stage, String kind) {
        counter(stage, kind)[1]++;
    }

    void failed(String stage, String kind) {
        counter(stage, kind)[2]++;
    }

    private int[] counter(String stage, String kind) {
        return stats.computeIfAbsent(stage, s -> new LinkedHashMap<>()).computeIfAbsent(kind, k -> new int[3]);
    }

    /** 按业务日期取号；干跑不推进编号序列（编号序列在独立事务里，不随干跑回滚） */
    String nextNo(DocumentType type, LocalDate bizDate) {
        if (dryRun) {
            return "DRY" + type.name() + (++dryNo);
        }
        return numbers.next(type, bizDate);
    }

    List<JsonNode> list(String name) {
        JsonNode n = bundle.get(name);
        return n == null || !n.isArray() ? List.of() : java.util.stream.StreamSupport.stream(n.spliterator(), false).toList();
    }

    Long user(String name) {
        return name == null ? null : userIds.get(name.trim());
    }

    /** 创建人：对应到的用户名，对不上用原姓名（create_by 最长 32） */
    String username(String name) {
        if (name == null || name.isBlank()) {
            return "migration";
        }
        String u = usernames.getOrDefault(name.trim(), name.trim());
        return u.length() > 32 ? u.substring(0, 32) : u;
    }

    static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? "" : v.asText();
    }

    static String textOrNull(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() || v.asText().isEmpty() ? null : v.asText();
    }

    static LocalDate date(JsonNode n, String field) {
        String s = textOrNull(n, field);
        return s == null ? null : LocalDate.parse(s.substring(0, 10));
    }

    static LocalDateTime time(JsonNode n, String field) {
        String s = textOrNull(n, field);
        return s == null ? null : LocalDateTime.parse(s.length() == 10 ? s + " 00:00:00" : s, DT);
    }

    static java.math.BigDecimal money(JsonNode n, String field) {
        String s = textOrNull(n, field);
        return s == null ? null : new java.math.BigDecimal(s).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    static Integer integer(JsonNode n, String field, Integer def) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? def : v.asInt();
    }

    static String cut(String s, int max) {
        return s == null ? "" : s.length() > max ? s.substring(0, max) : s;
    }
}
