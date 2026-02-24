package com.vietrecruit.feature.notification.dto;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class EmailSenderAliasTest {

    @Test
    void toFromAddress_noReply_shouldFormatCorrectly() {
        String result = EmailSenderAlias.NO_REPLY.toFromAddress("vietrecruit.site");
        assertEquals("VietRecruit <no-reply@vietrecruit.site>", result);
    }

    @Test
    void toFromAddress_authentication_shouldFormatCorrectly() {
        String result = EmailSenderAlias.AUTHENTICATION.toFromAddress("vietrecruit.site");
        assertEquals("VietRecruit Auth <authentication@vietrecruit.site>", result);
    }

    @Test
    void toFromAddress_notification_shouldFormatCorrectly() {
        String result = EmailSenderAlias.NOTIFICATION.toFromAddress("vietrecruit.site");
        assertEquals("VietRecruit <notification@vietrecruit.site>", result);
    }

    @Test
    void toFromAddress_customDomain_shouldUseProvidedDomain() {
        String result = EmailSenderAlias.NO_REPLY.toFromAddress("example.com");
        assertEquals("VietRecruit <no-reply@example.com>", result);
    }
}
