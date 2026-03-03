package com.vietrecruit.feature.notification.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import com.vietrecruit.common.enums.ApiErrorCode;
import com.vietrecruit.common.exception.ApiException;
import com.vietrecruit.feature.notification.dto.EmailRequest;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;

/**
 * Low-level client for sending emails via the Resend HTTP API. Protected by Resilience4j Retry and
 * Circuit Breaker.
 */
@Slf4j
@Component
public class ResendEmailClient {

    private final Resend resend;
    private final String domain;

    public ResendEmailClient(Resend resend, @Qualifier("resendDomain") String domain) {
        this.resend = resend;
        this.domain = domain;
    }

    /**
     * Sends an email via Resend API.
     *
     * <p>Retry is evaluated first (inner), then Circuit Breaker (outer). On sustained failures, the
     * circuit opens and short-circuits further calls.
     *
     * @param request the original email request (for metadata)
     * @param renderedHtml the fully rendered HTML content
     */
    @Retry(name = "resendApi")
    @CircuitBreaker(name = "resendApi", fallbackMethod = "sendFallback")
    public void send(EmailRequest request, String renderedHtml) {
        String fromAddress = request.senderAlias().toFromAddress(domain);

        CreateEmailOptions options =
                CreateEmailOptions.builder()
                        .from(fromAddress)
                        .to(request.to().toArray(String[]::new))
                        .subject(request.subject())
                        .html(renderedHtml)
                        .build();

        log.info(
                "Sending email via Resend: from={}, to={}, subject={}",
                fromAddress,
                request.to(),
                request.subject());

        try {
            CreateEmailResponse response = resend.emails().send(options);
            log.info("Email sent via Resend: id={}, to={}", response.getId(), request.to());
        } catch (ResendException e) {
            throw new RuntimeException("Resend API call failed", e);
        }
    }

    /**
     * Fallback when circuit breaker is open. Logs the failure and throws an ApiException so the
     * Kafka consumer can handle it (retry via @RetryableTopic or route to DLT).
     */
    @SuppressWarnings("unused")
    private void sendFallback(EmailRequest request, String renderedHtml, Throwable throwable) {
        log.error(
                "Resend API circuit breaker open. Email not sent: to={}, subject={}",
                request.to(),
                request.subject(),
                throwable);
        throw new ApiException(ApiErrorCode.NOTIFICATION_SEND_FAILED);
    }
}
