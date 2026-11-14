package com.zhul.erp.common.exception;

import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * audit DEP-002：在真实的内嵌 Tomcat 上验证上传分段限制（MockMvc 不经过 Tomcat 的 multipart 解析）。
 * 沿用 Tomcat 默认值，不放宽：每段头部 512 字节（约 120 个汉字以内的文件名可传，更长的拒绝并提示文件名太长）、最多 50 段。
 */
class TomcatMultipartLimitsTest {

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private WebServer server;

    @BeforeEach
    void start() {
        server = new TomcatServletWebServerFactory(0).getWebServer(ctx -> {
            ServletRegistrationBean<HttpServlet> reg = new ServletRegistrationBean<>(new SpringMultipartServlet(), "/up");
            reg.setMultipartConfig(new MultipartConfigElement("", 100L << 20, 100L << 20, 0));
            reg.onStartup(ctx);
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    @Test
    void normalChineseFileName_accepted_tooLong_rejectedWithClearMessage() throws Exception {
        assertThat(post("测".repeat(100) + ".xlsx", 1)).isEqualTo("parts=1");
        assertThat(post("测".repeat(150) + ".xlsx", 1)).isEqualTo("文件名太长，请改短后重新上传");
    }

    @Test
    void twentyFiles_accepted_partCountOverDefault_rejected() throws Exception {
        assertThat(post("报价.xlsx", 20)).isEqualTo("parts=20");
        assertThat(post("报价.xlsx", 51)).startsWith("rejected");
    }

    private String post(String fileName, int files) throws IOException, InterruptedException {
        String boundary = "----zhul-test";
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (int i = 0; i < files; i++) {
            body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"files\"; filename=\"" + fileName + "\"\r\n"
                    + "Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\nxx\r\n")
                    .getBytes(StandardCharsets.UTF_8));
        }
        body.writeBytes(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + server.getPort() + "/up"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        return CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
    }

    /** 像 DispatcherServlet 一样用 Spring 的解析器解析，异常交给全局异常处理，返回分段数或提示 */
    private static final class SpringMultipartServlet extends HttpServlet {
        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setContentType("text/plain;charset=UTF-8");
            try {
                int n = new StandardServletMultipartResolver().resolveMultipart(req).getMultiFileMap().get("files").size();
                resp.getWriter().write("parts=" + n);
            } catch (MaxUploadSizeExceededException e) {
                resp.getWriter().write(new GlobalExceptionHandler().handleMaxUploadSizeExceeded(e).getMessage());
            } catch (MultipartException e) {
                resp.getWriter().write("rejected " + e.getMessage());
            }
        }
    }
}
