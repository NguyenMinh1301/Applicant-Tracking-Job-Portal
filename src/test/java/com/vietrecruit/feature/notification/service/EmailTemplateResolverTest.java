package com.vietrecruit.feature.notification.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.vietrecruit.common.enums.EmailSenderAlias;
import com.vietrecruit.feature.notification.dto.EmailRequest;

@ExtendWith(MockitoExtension.class)
class EmailTemplateResolverTest {

    @Mock private TemplateEngine templateEngine;

    @InjectMocks private EmailTemplateResolver emailTemplateResolver;

    @Test
    void resolve_withRawHtml_shouldReturnHtmlDirectly() {
        String rawHtml = "<p>Hello World</p>";
        EmailRequest request =
                new EmailRequest(
                        List.of("user@example.com"),
                        EmailSenderAlias.NO_REPLY,
                        "Subject",
                        rawHtml,
                        null,
                        null);

        String result = emailTemplateResolver.resolve(request);

        assertEquals(rawHtml, result);
    }

    @Test
    void resolve_withTemplateId_shouldRenderTemplate() {
        EmailRequest request =
                new EmailRequest(
                        List.of("user@example.com"),
                        EmailSenderAlias.NO_REPLY,
                        "Welcome",
                        null,
                        "welcome",
                        Map.of("heading", "Hello", "message", "Welcome aboard"));

        String expectedHtml = "<h1>Hello</h1><p>Welcome aboard</p>";
        when(templateEngine.process(eq("email/welcome"), any(Context.class)))
                .thenReturn(expectedHtml);

        String result = emailTemplateResolver.resolve(request);

        assertEquals(expectedHtml, result);
    }

    @Test
    void resolve_withTemplateIdAndNullVariables_shouldRenderWithEmptyContext() {
        EmailRequest request =
                new EmailRequest(
                        List.of("user@example.com"),
                        EmailSenderAlias.NO_REPLY,
                        "Welcome",
                        null,
                        "welcome",
                        null);

        String expectedHtml = "<h1>Default</h1>";
        when(templateEngine.process(eq("email/welcome"), any(Context.class)))
                .thenReturn(expectedHtml);

        String result = emailTemplateResolver.resolve(request);

        assertEquals(expectedHtml, result);
    }
}
