package com.vietrecruit.feature.notification.dto;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import com.vietrecruit.common.enums.EmailSenderAlias;

/**
 * Immutable email request payload. Exactly one of {@code html} or {@code templateId} must be
 * non-null.
 *
 * @param to list of recipient email addresses
 * @param senderAlias the sender alias determining the From address
 * @param subject email subject line
 * @param html raw HTML content (mutually exclusive with templateId)
 * @param templateId Thymeleaf template identifier (mutually exclusive with html)
 * @param templateVariables variables for Thymeleaf template rendering
 */
public record EmailRequest(
        @NotEmpty List<String> to,
        @NotNull EmailSenderAlias senderAlias,
        @NotBlank String subject,
        String html,
        String templateId,
        Map<String, Object> templateVariables) {

    /**
     * Validates that exactly one content source is provided.
     *
     * @throws IllegalArgumentException if both or neither content source is set
     */
    public EmailRequest {
        boolean hasHtml = html != null && !html.isBlank();
        boolean hasTemplate = templateId != null && !templateId.isBlank();
        if (hasHtml == hasTemplate) {
            throw new IllegalArgumentException(
                    "Exactly one of 'html' or 'templateId' must be provided");
        }
    }
}
