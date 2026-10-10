package com.zhul.erp.modules.system.repository;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

/** 单据编号当日流水：插入或递增（行锁）→ 读回，须在同一事务内调用 */
@Mapper
public interface DocumentSequenceMapper {

    /** 当天第一次插入 last_no=1，否则加一；两种情况都持有该行的排他锁直到事务结束 */
    @Insert("INSERT INTO document_sequence (tenant_id, doc_type, biz_date, last_no) VALUES (#{tenantId}, #{docType}, #{bizDate}, 1) "
            + "ON DUPLICATE KEY UPDATE last_no = last_no + 1, update_time = NOW()")
    int increment(@Param("tenantId") int tenantId, @Param("docType") String docType, @Param("bizDate") LocalDate bizDate);

    @Select("SELECT last_no FROM document_sequence WHERE tenant_id = #{tenantId} AND doc_type = #{docType} AND biz_date = #{bizDate}")
    Integer current(@Param("tenantId") int tenantId, @Param("docType") String docType, @Param("bizDate") LocalDate bizDate);
}
