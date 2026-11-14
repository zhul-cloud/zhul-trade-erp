package com.zhul.erp.framework.config;

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
 * 长中文文件名能上传；分段数超过 50 被拒。
 */
class TomcatMultipartLimitsTest {

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private WebServer server;

    @BeforeEach
    void start() {
        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory(0);
        new TomcatMultipartConfig().customize(factory);
        server = factory.getWebServer(ctx -> {
            ServletRegistrationBean<HttpServlet> reg = new ServletRegistrationBean<>(new PartCountServlet(), "/up");
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
    void longChineseFileName_isAccepted() throws Exception {
        // Windows 文件名上限 255 个字符，全中文
        assertThat(post("测".repeat(250) + ".xlsx", 1)).isEqualTo("200 parts=1");
    }

    @Test
    void twentyFiles_accepted_partCountOverDefault_rejected() throws Exception {
        assertThat(post("报价.xlsx", 20)).isEqualTo("200 parts=20");
        assertThat(post("报价.xlsx", 51)).startsWith("500 ").contains("FileCountLimitExceededException");
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
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode() + " " + response.body();
    }

    /** 读取全部分段并返回数量；解析失败时返回 500 和异常信息 */
    private static final class PartCountServlet extends HttpServlet {
        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            try {
                resp.getWriter().write("parts=" + req.getParts().size());
            } catch (IllegalStateException | jakarta.servlet.ServletException e) {
                resp.setStatus(500);
                resp.getWriter().write(e.getClass().getName() + ": " + e.getMessage());
            }
        }
    }
}
