package com.zhul.erp.modules.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.constants.RedisKeyConstants;
import com.zhul.erp.modules.system.dto.UpdateUserRequest;
import com.zhul.erp.modules.system.service.UserService;
import com.zhul.erp.support.IntegrationTestBase;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** 找回密码：验证码通过发信邮箱（SMTP）真实发送 */
class PasswordResetMailContractTest {

    private static final long ACCOUNT_ID = 99000051L;
    private static final String USERNAME = "it_reset_mail";
    private static final String EMAIL = "zhang.wei@fouwell.com";
    private static final String SEND_CODE = "/api/v1/auth/password/send-code";

    @AutoConfigureMockMvc
    abstract static class Base extends IntegrationTestBase {
        @Autowired protected MockMvc mvc;
        @Autowired protected ObjectMapper objectMapper;
        @Autowired protected StringRedisTemplate redis;

        @BeforeEach
        void account() {
            cleanup();
            jdbc.update("insert into user_basic (id, tenant_id, name, username, email) values (?, 0, '张伟', ?, ?)",
                    ACCOUNT_ID, USERNAME, EMAIL);
            jdbc.update("insert into account (id, tenant_id, user_id, username, email, admin_flag) values (?, 0, ?, ?, ?, 0)",
                    ACCOUNT_ID, ACCOUNT_ID, USERNAME, EMAIL);
            jdbc.update("insert into account_local_auth (account_id, username, password, salt) values (?, ?, 'x', 'x')",
                    ACCOUNT_ID, USERNAME);
        }

        @AfterEach
        void cleanup() {
            jdbc.update("delete from account_local_auth where account_id = ?", ACCOUNT_ID);
            jdbc.update("delete from account where id = ?", ACCOUNT_ID);
            jdbc.update("delete from user_basic where id = ?", ACCOUNT_ID);
            redis.delete(RedisKeyConstants.RESET_CODE_COOLDOWN_PREFIX + USERNAME);
        }

        protected JsonNode sendCode(String email) throws Exception {
            String body = objectMapper.writeValueAsString(Map.of("username", USERNAME, "email", email));
            return objectMapper.readTree(mvc.perform(post(SEND_CODE).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        }

        protected String storedCode() {
            return jdbc.queryForObject("select reset_token from account_local_auth where account_id = ?", String.class, ACCOUNT_ID);
        }
    }

    /** 配置了发信邮箱：验证码发到账号绑定的邮箱 */
    @Nested
    @TestPropertySource(properties = {"spring.mail.host=smtp.test.local", "spring.mail.username=noreply@fouwell.com",
            "zhul.mail.from-name=烛龙ERP", "zhul.mail.log-only=false"})
    class Configured extends Base {

        @MockBean
        private JavaMailSender sender;
        @Autowired
        private UserService userService;

        @BeforeEach
        void mockSender() {
            reset(sender);
            when(sender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
        }

        @Test
        void sendsCodeToBoundEmail() throws Exception {
            assertEquals(0, sendCode(EMAIL).path("code").asInt());
            ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
            verify(sender).send(captor.capture());
            MimeMessage m = captor.getValue();
            assertEquals(EMAIL, ((InternetAddress) m.getAllRecipients()[0]).getAddress());
            InternetAddress from = (InternetAddress) m.getFrom()[0];
            assertEquals("noreply@fouwell.com", from.getAddress());
            assertEquals("烛龙ERP", from.getPersonal());
            assertEquals("【烛龙ERP】找回密码验证码", m.getSubject());
            String html = (String) m.getContent();
            String code = storedCode();
            assertEquals(6, code.length());
            assertTrue(html.contains(code), "邮件里的验证码与库里一致");
            assertTrue(html.contains("10 分钟内有效"));

            assertEquals(1011, sendCode(EMAIL).path("code").asInt(), "发送成功后 60 秒内不能重发");
        }

        @Test
        void sendsToEmailChangedInUserManagement() throws Exception {
            UpdateUserRequest req = new UpdateUserRequest();
            req.setEmail("li.na@fouwell.com");
            userService.updateUser((int) ACCOUNT_ID, req);

            assertEquals(0, sendCode("li.na@fouwell.com").path("code").asInt());
            ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
            verify(sender).send(captor.capture());
            assertEquals("li.na@fouwell.com", ((InternetAddress) captor.getValue().getAllRecipients()[0]).getAddress(),
                    "用户管理改了邮箱后，验证码发到新邮箱");
        }

        @Test
        void wrongEmailSendsNothingButLooksTheSame() throws Exception {
            assertEquals(0, sendCode("someone@else.com").path("code").asInt(), "邮箱不匹配也返回成功，防止枚举");
            verify(sender, never()).send(any(MimeMessage.class));
        }

        @Test
        void sendFailureIsReportedAndCanRetryAtOnce() throws Exception {
            doThrow(new MailSendException("535 authentication failed")).when(sender).send(any(MimeMessage.class));
            JsonNode res = sendCode(EMAIL);
            assertEquals(1019, res.path("code").asInt());
            assertEquals("验证码邮件发送失败，请稍后重试", res.path("message").asText());
            reset(sender);
            when(sender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
            assertEquals(0, sendCode(EMAIL).path("code").asInt(), "没发出去不进入冷却，可以马上重试");
        }
    }

    /** 没配置发信邮箱、也没开只写日志（生产）：直接提示，不暴露账号是否存在 */
    @Nested
    @TestPropertySource(properties = {"spring.mail.host=", "zhul.mail.log-only=false"})
    class NotConfigured extends Base {

        @Test
        void tellsUserToContactAdmin() throws Exception {
            JsonNode res = sendCode(EMAIL);
            assertEquals(1018, res.path("code").asInt());
            assertEquals(1018, sendCode("someone@else.com").path("code").asInt(), "不论账号邮箱是否正确，提示都一样");
            assertEquals(null, storedCode());
        }
    }
}
