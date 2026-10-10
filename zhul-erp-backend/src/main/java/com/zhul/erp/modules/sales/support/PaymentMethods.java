package com.zhul.erp.modules.sales.support;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.system.dto.DictItemVO;
import com.zhul.erp.modules.system.service.DictItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

/** 付款方式字典（payment_method）：码值、名称与线上 / 线下 */
@Component
@RequiredArgsConstructor
public class PaymentMethods {

    private final DictItemService dictItemService;

    public record Method(String code, String name, int channel) {
        public boolean online() {
            return channel == SalesConstants.CHANNEL_ONLINE;
        }
    }

    private List<DictItemVO> enabled() {
        return dictItemService.listByDictType(SalesConstants.DICT_PAYMENT_METHOD).stream()
                .filter(d -> Objects.equals(d.getStatus(), 1)).toList();
    }

    private static Method of(DictItemVO d) {
        return new Method(d.getItemCode(), d.getItemName(),
                SalesConstants.METHOD_ONLINE.equalsIgnoreCase(d.getItemValue()) ? SalesConstants.CHANNEL_ONLINE : SalesConstants.CHANNEL_OFFLINE);
    }

    /** 线下付款方式；不传时取默认的线下项（没有默认项时取第一项） */
    public Method offline(String code) {
        if (!StringUtils.hasText(code)) {
            List<DictItemVO> list = enabled().stream().filter(d -> !SalesConstants.METHOD_ONLINE.equalsIgnoreCase(d.getItemValue())).toList();
            return list.stream().filter(d -> Objects.equals(d.getIsDefault(), 1)).findFirst().or(() -> list.stream().findFirst())
                    .map(PaymentMethods::of).orElse(new Method("TT", "银行转账", SalesConstants.CHANNEL_OFFLINE));
        }
        Method m = require(code);
        if (m.online()) {
            throw new BizException("「" + m.name() + "」是线上付款方式，请用「登记平台收款」");
        }
        return m;
    }

    /** 线上付款方式（平台收款） */
    public Method online(String code) {
        if (!StringUtils.hasText(code)) {
            throw new BizException("请选择付款方式");
        }
        Method m = require(code);
        if (!m.online()) {
            throw new BizException("平台收款只能选择线上付款方式");
        }
        return m;
    }

    private Method require(String code) {
        return enabled().stream().filter(d -> code.trim().equals(d.getItemCode())).findFirst().map(PaymentMethods::of)
                .orElseThrow(() -> new BizException("付款方式不存在或已停用"));
    }
}
