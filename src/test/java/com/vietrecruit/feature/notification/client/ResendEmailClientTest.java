package com.vietrecruit.feature.notification.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.Emails;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import com.vietrecruit.common.enums.EmailSenderAlias;
import com.vietrecruit.feature.notification.dto.EmailRequest;

@ExtendWith(MockitoExtension.class)
class ResendEmailClientTest {

    @Mock private Resend resend;
    @Mock private Emails emails;

    private ResendEmailClient resendEmailClient;

    @BeforeEach
    void setUp() {
        resendEmailClient = new ResendEmailClient(resend, "vietrecruit.site");
    }

    @Test
    void send_shouldConstructCorrectEmailOptions() throws ResendException {
        EmailRequest request =
                new EmailRequest(
                        List.of("user@example.com"),
                        EmailSenderAlias.AUTHENTICATION,
                        "Verify your account",
                        null,
                        "welcome",
                        null);
        String renderedHtml = "<p>rendered</p>";

        CreateEmailResponse response = mock(CreateEmailResponse.class);
        when(response.getId()).thenReturn("email-id-123");
        when(resend.emails()).thenReturn(emails);
        when(emails.send(any(CreateEmailOptions.class))).thenReturn(response);

        resendEmailClient.send(request, renderedHtml);

        ArgumentCaptor<CreateEmailOptions> captor =
                ArgumentCaptor.forClass(CreateEmailOptions.class);
        verify(emails).send(captor.capture());

        CreateEmailOptions captured = captor.getValue();
        assertEquals("VietRecruit Auth <authentication@vietrecruit.site>", captured.getFrom());
        assertEquals("Verify your account", captured.getSubject());
        assertEquals(renderedHtml, captured.getHtml());
    }

    @Test
    void send_whenResendThrows_shouldWrapInRuntimeException() throws ResendException {
        EmailRequest request =
                new EmailRequest(
                        List.of("user@example.com"),
                        EmailSenderAlias.NO_REPLY,
                        "Test",
                        "<p>test</p>",
                        null,
                        null);

        when(resend.emails()).thenReturn(emails);
        when(emails.send(any(CreateEmailOptions.class)))
                .thenThrow(new ResendException("API error"));

        RuntimeException thrown =
                assertThrows(
                        RuntimeException.class,
                        () -> resendEmailClient.send(request, "<p>test</p>"));
        assertInstanceOf(ResendException.class, thrown.getCause());
    }
}
