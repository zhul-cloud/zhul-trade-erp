package com.zhul.erp.framework.config;

import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

/**
 * 上传请求的分段限制（audit DEP-002）。Tomcat 10.1.42 起限制 multipart 的分段数（默认 50）和每段头部字节数（默认 512），
 * 防止大量分段撑爆内存。512 字节放不下长中文文件名（约 130 个汉字以上即被拒），这里放宽到 2048：
 * Windows 文件名最长 255 个字符，全中文约 765 字节，加上表单名与类型仍在 2048 内。分段数沿用默认 50（单次最多导入 20 个文件）。
 */
@Component
public class TomcatMultipartConfig implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    /** 每个分段的头部最大字节数 */
    public static final int MAX_PART_HEADER_SIZE = 2048;

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        factory.addConnectorCustomizers(connector -> connector.setMaxPartHeaderSize(MAX_PART_HEADER_SIZE));
    }
}
