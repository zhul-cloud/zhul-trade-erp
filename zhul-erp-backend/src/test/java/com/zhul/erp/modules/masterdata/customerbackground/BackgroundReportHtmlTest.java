package com.zhul.erp.modules.masterdata.customerbackground;

import com.zhul.erp.modules.masterdata.customerbackground.support.BackgroundReportHtml;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 背调报告导出 HTML 的安全处理（不连库） */
class BackgroundReportHtmlTest {

    /** audit SEC-021：正文里的 Markdown 图片不生成外链图片，LibreOffice 转换时不会替用户访问内网地址 */
    @Test
    void markdownImages_areNotRenderedAsExternalResources() {
        String html = BackgroundReportHtml.markdownToHtml("看这里 ![内网](http://10.0.0.8/admin.png) 和 ![x][ref]\n\n"
                + "[ref]: https://169.254.169.254/latest/meta-data\n\n![data](data:image/png;base64,AAAA)");
        assertFalse(html.contains("<img"), html);
        assertFalse(html.contains("10.0.0.8"), html);
        assertFalse(html.contains("169.254.169.254"), html);
        assertTrue(html.contains("内网"), "保留图片说明文字：" + html);
    }
}
