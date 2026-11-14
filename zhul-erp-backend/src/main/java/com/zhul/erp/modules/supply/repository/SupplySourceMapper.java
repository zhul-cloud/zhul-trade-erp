package com.zhul.erp.modules.supply.repository;

import com.zhul.erp.modules.supply.dto.SourceRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 供应链从其他模块只读取数（design 决策 2、6）：四种自动信号来源、价格证据、主营供应商、询价中的型号。
 * 只经 MyBatis，由租户拦截器追加租户条件；调用方须在租户上下文里。
 */
@Mapper
public interface SupplySourceMapper {

    /** 询盘型号行；询盘已取消或已删除的算来源已取消 */
    @Select("SELECT i.id AS sourceId, ci.id AS docId, ci.inquiry_code AS docNo, i.brand_id AS brandId, i.brand AS brandRaw,"
            + " i.category_id AS categoryId, i.product_id AS productId, i.confirmed_model AS modelRaw, i.model_key AS modelKey,"
            + " i.quantity AS quantity, CAST(ci.inquiry_date AS DATETIME) AS occurredAt,"
            + " (ci.status = 10 OR ci.deleted_at IS NOT NULL OR i.deleted_at IS NOT NULL) AS cancelled"
            + " FROM inquiry_item i JOIN customer_inquiry ci ON ci.id = i.customer_inquiry_id"
            + " WHERE (i.update_time >= #{since} OR ci.update_time >= #{since}) AND ci.inquiry_date >= #{floor}")
    List<SourceRow> inquiryRows(@Param("since") LocalDateTime since, @Param("floor") LocalDateTime floor);

    /** 销售订单型号行；订单取消、删除或整行取消的算来源已取消。品牌型号优先取来源询盘型号行 */
    @Select("SELECT s.id AS sourceId, so.id AS docId, so.so_no AS docNo, i.brand_id AS brandId,"
            + " COALESCE(i.brand, s.brand) AS brandRaw, i.category_id AS categoryId, COALESCE(s.product_id, i.product_id) AS productId,"
            + " s.model AS modelRaw, i.model_key AS modelKey, s.quantity AS quantity,"
            + " COALESCE(CAST(so.sales_date AS DATETIME), so.create_time) AS occurredAt,"
            + " (so.status = 2 OR so.deleted_at IS NOT NULL OR s.deleted_at IS NOT NULL OR s.quantity = 0) AS cancelled"
            + " FROM sales_order_item s JOIN sales_order so ON so.id = s.so_id"
            + " LEFT JOIN inquiry_item i ON i.id = s.inquiry_item_id"
            + " WHERE (s.update_time >= #{since} OR so.update_time >= #{since}) AND so.create_time >= #{floor}")
    List<SourceRow> orderRows(@Param("since") LocalDateTime since, @Param("floor") LocalDateTime floor);

    /** 提交过的无货询价记录；撤回（回到草稿）、待审核、作废、删除的算来源已取消。原因取字典 inquiry_no_stock_reason 的值，名称由调用方翻译 */
    @Select("SELECT q.id AS sourceId, t.id AS docId, t.task_code AS docNo, i.brand_id AS brandId, q.brand AS brandRaw,"
            + " i.category_id AS categoryId, i.product_id AS productId, q.model AS modelRaw, q.model_key AS modelKey,"
            + " i.quantity AS quantity, CAST(q.no_stock_reason AS CHAR) AS reasonCode, q.note AS reasonText,"
            + " q.quoted_at AS occurredAt, (q.status <> 2 OR q.deleted_at IS NOT NULL) AS cancelled"
            + " FROM sourcing_quote q JOIN sourcing_task t ON t.id = q.task_id"
            + " LEFT JOIN inquiry_item i ON i.id = q.inquiry_item_id"
            + " WHERE q.no_stock = 1 AND q.status IN (1, 2, 3, 4) AND q.quoted_at IS NOT NULL"
            + " AND q.update_time >= #{since} AND q.quoted_at >= #{floor}")
    List<SourceRow> stockoutRows(@Param("since") LocalDateTime since, @Param("floor") LocalDateTime floor);

    /** 未成交报价单的当前版本型号行；报价单不再是未成交（重新打开）或删除的算来源已取消 */
    @Select("SELECT qi.id AS sourceId, qt.id AS docId, qt.quotation_no AS docNo, i.brand_id AS brandId,"
            + " COALESCE(i.brand, qi.brand) AS brandRaw, i.category_id AS categoryId, i.product_id AS productId,"
            + " qi.model AS modelRaw, i.model_key AS modelKey, qi.quantity AS quantity, qt.lost_reason AS reasonCode,"
            + " qt.lost_reason_name AS reasonText, COALESCE(qt.closed_at, qt.update_time) AS occurredAt,"
            + " (qt.status <> 4 OR qt.deleted_at IS NOT NULL OR qi.deleted_at IS NOT NULL) AS cancelled"
            + " FROM quotation_item qi JOIN quotation qt ON qt.id = qi.quotation_id"
            + " LEFT JOIN inquiry_item i ON i.id = qi.inquiry_item_id"
            + " WHERE qi.is_current = 1 AND qt.update_time >= #{since} AND qt.create_time >= #{floor}"
            + " AND (qt.status = 4 OR EXISTS (SELECT 1 FROM sc_demand_signal g WHERE g.source_type = 4 AND g.source_id = qi.id))")
    List<SourceRow> lostRows(@Param("since") LocalDateTime since, @Param("floor") LocalDateTime floor);

    /** 有价证据：已提交、有货、单价大于 0 的询价记录，按型号取 */
    @Select("<script>SELECT q.id AS id, q.model_key AS modelKey, q.brand AS brand, q.supplier_id AS supplierId,"
            + " DATE(q.quoted_at) AS evidenceDate FROM sourcing_quote q"
            + " WHERE q.status = 2 AND q.no_stock = 0 AND q.unit_price &gt; 0 AND q.deleted_at IS NULL AND q.quoted_at IS NOT NULL"
            + " AND q.model_key IN <foreach collection='keys' item='k' open='(' separator=',' close=')'>#{k}</foreach></script>")
    List<Map<String, Object>> quoteEvidence(@Param("keys") List<String> keys);

    /** 有价证据：已下单采购订单的型号行（型号未归一化，调用方归一化后比对） */
    @Select("SELECT p.id AS id, p.model AS model, p.brand AS brand, po.supplier_id AS supplierId, po.order_date AS evidenceDate"
            + " FROM purchase_order_item p JOIN purchase_order po ON po.id = p.po_id"
            + " WHERE po.status = 2 AND po.deleted_at IS NULL AND p.deleted_at IS NULL AND po.order_date IS NOT NULL"
            + " AND p.unit_price > 0")
    List<Map<String, Object>> poEvidence();

    /** 供应商主营产品（品牌 + 品类，品类为空表示全部品类），只算未删除的供应商 */
    @Select("SELECT sps.supplier_id AS supplierId, sps.brand_id AS brandId, sps.category_id AS categoryId"
            + " FROM supplier_product_scope sps JOIN supplier s ON s.id = sps.supplier_id"
            + " WHERE sps.deleted_at IS NULL AND s.deleted_at IS NULL AND sps.brand_id IS NOT NULL")
    List<Map<String, Object>> supplierScopes();

    /** 询价中的型号：待分配、询价中的询价任务里的型号行 */
    @Select("SELECT DISTINCT i.model_key AS modelKey, t.task_code AS taskCode, t.id AS taskId FROM inquiry_item i"
            + " JOIN sourcing_task t ON t.id = i.sourcing_task_id"
            + " WHERE t.status IN (1, 2) AND t.deleted_at IS NULL AND i.deleted_at IS NULL AND i.model_key <> ''")
    List<Map<String, Object>> sourcingModels();

    /** 某供应商对某型号最近一条有价询价记录 */
    @Select("SELECT q.unit_price AS unitPrice, q.currency_code AS currencyCode, q.tax_included AS taxIncluded,"
            + " q.item_condition AS itemCondition, q.lead_time AS leadTime, DATE(q.quoted_at) AS evidenceDate"
            + " FROM sourcing_quote q WHERE q.supplier_id = #{supplierId} AND q.model_key = #{modelKey}"
            + " AND q.status = 2 AND q.no_stock = 0 AND q.unit_price > 0 AND q.deleted_at IS NULL"
            + " ORDER BY q.quoted_at DESC, q.id DESC LIMIT 1")
    Map<String, Object> latestQuote(@Param("supplierId") Long supplierId, @Param("modelKey") String modelKey);

    /** 采购订单行证据详情 */
    @Select("SELECT p.unit_price AS unitPrice, po.currency_code AS currencyCode, po.tax_included AS taxIncluded,"
            + " po.order_date AS evidenceDate, po.supplier_id AS supplierId, po.po_no AS docNo, po.id AS docId"
            + " FROM purchase_order_item p JOIN purchase_order po ON po.id = p.po_id WHERE p.id = #{id}")
    Map<String, Object> poLine(@Param("id") Long id);

    /** 询价记录证据详情 */
    @Select("SELECT q.unit_price AS unitPrice, q.currency_code AS currencyCode, q.tax_included AS taxIncluded,"
            + " q.item_condition AS itemCondition, q.lead_time AS leadTime, DATE(q.quoted_at) AS evidenceDate,"
            + " q.supplier_id AS supplierId, q.shop_name AS shopName, t.task_code AS docNo, t.id AS docId"
            + " FROM sourcing_quote q JOIN sourcing_task t ON t.id = q.task_id WHERE q.id = #{id}")
    Map<String, Object> quoteLine(@Param("id") Long id);

    /** 某供应商已下单的采购订单行（型号未归一化） */
    @Select("SELECT p.id AS id, p.model AS model, p.unit_price AS unitPrice, po.currency_code AS currencyCode,"
            + " po.tax_included AS taxIncluded, po.order_date AS evidenceDate FROM purchase_order_item p"
            + " JOIN purchase_order po ON po.id = p.po_id WHERE po.supplier_id = #{supplierId} AND po.status = 2"
            + " AND po.deleted_at IS NULL AND p.deleted_at IS NULL AND p.unit_price > 0 AND po.order_date IS NOT NULL"
            + " ORDER BY po.order_date DESC, p.id DESC")
    List<Map<String, Object>> poBySupplier(@Param("supplierId") Long supplierId);

    /** 询价联动：询价记录及其任务、型号行 */
    @Select("<script>SELECT q.id AS id, q.status AS status, q.no_stock AS noStock, q.unit_price AS unitPrice,"
            + " q.currency_code AS currencyCode, q.lead_time AS leadTime, q.note AS note, q.no_stock_reason AS noStockReason,"
            + " q.model AS model, q.model_key AS modelKey, q.brand AS brand, q.supplier_id AS supplierId, q.shop_name AS shopName,"
            + " q.channel AS channel, q.quoted_by AS quotedBy, q.quoted_at AS quotedAt, q.deleted_at AS deletedAt,"
            + " t.id AS taskId, t.task_code AS taskCode, i.brand_id AS brandId, i.category_id AS categoryId,"
            + " i.product_id AS productId, i.quantity AS quantity"
            + " FROM sourcing_quote q JOIN sourcing_task t ON t.id = q.task_id LEFT JOIN inquiry_item i ON i.id = q.inquiry_item_id"
            + " WHERE q.id IN <foreach collection='ids' item='i' open='(' separator=',' close=')'>#{i}</foreach></script>")
    List<Map<String, Object>> quotesForLinkage(@Param("ids") java.util.Collection<Long> ids);

    /** 补登用：这段时间内提交或撤回过的询价记录 ID */
    @Select("SELECT q.id FROM sourcing_quote q WHERE q.quoted_at IS NOT NULL AND q.update_time >= #{since}"
            + " AND q.status IN (1, 2)")
    List<Long> recentQuoteIds(@Param("since") LocalDateTime since);
}
