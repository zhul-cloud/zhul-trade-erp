package com.zhul.erp.modules.sales.service;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.sales.dto.AddPiItemsRequest;
import com.zhul.erp.modules.sales.dto.CreatePiRequest;
import com.zhul.erp.modules.sales.dto.PiCandidateCustomerVO;
import com.zhul.erp.modules.sales.dto.PiCandidateQuotationVO;
import com.zhul.erp.modules.sales.dto.PartyOptionVO;
import com.zhul.erp.modules.sales.dto.PiListVO;
import com.zhul.erp.modules.sales.dto.PiPageQuery;
import com.zhul.erp.modules.sales.dto.PiStatsVO;
import com.zhul.erp.modules.sales.dto.PiVO;
import com.zhul.erp.modules.sales.dto.ReceiptVO;
import com.zhul.erp.modules.sales.dto.SavePiRequest;

import java.util.List;

/** PI：新建（按报价单 / 跨报价单挑型号）、编辑、版本、发送、作废与查看 */
public interface PiService {

    List<PiCandidateCustomerVO> candidateCustomers(String keyword);

    /** 客户可开 PI 的报价单（已发送 / 部分成交），按发送时间从新到旧 */
    List<PiCandidateQuotationVO> candidateQuotations(Long customerId);

    /** 单张报价单的型号（报价单详情「开 PI」） */
    PiCandidateQuotationVO candidateQuotation(Long quotationId);

    PiVO create(CreatePiRequest req);

    PiVO addItems(Long id, AddPiItemsRequest req);

    PiVO save(Long id, SavePiRequest req);

    PiVO markSent(Long id, int channel);

    /** 已发送的 PI 生成新版本草稿 */
    PiVO revise(Long id);

    /** 放弃未发送的新版本 */
    PiVO abandon(Long id);

    PiVO voidPi(Long id);

    void delete(Long id);

    /** versionNo 为空时看编辑中的版本，没有时看当前有效版本 */
    PiVO detail(Long id, Integer versionNo);

    PageResult<PiListVO> page(PiPageQuery query);

    /** PI 客户的发票抬头与收货人，供改选买方 / 收货人 */
    List<PartyOptionVO> parties(Long id);

    /** PI 的收款记录（订单详情也展示这一组） */
    List<ReceiptVO> receipts(Long piId);

    PiStatsVO stats();

    /** 报价单详情：由它开出的 PI */
    List<PiListVO> byQuotation(Long quotationId);
}
