package com.vietrecruit.feature.notification.service.impl;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.vietrecruit.common.config.kafka.KafkaTopicNames;
import com.vietrecruit.common.enums.EmailSenderAlias;
import com.vietrecruit.feature.notification.dto.EmailRequest;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks private NotificationServiceImpl notificationService;

    @Test
    void send_shouldPublishToCorrectKafkaTopic() {
        EmailRequest request =
                new EmailRequest(
                        List.of("user@example.com"),
                        EmailSenderAlias.NO_REPLY,
                        "Test Subject",
                        "<p>Hello</p>",
                        null,
                        null);

        RecordMetadata metadata =
                new RecordMetadata(
                        new TopicPartition(KafkaTopicNames.NOTIFICATION_EMAIL, 0), 0L, 0, 0L, 0, 0);
        SendResult<String, Object> sendResult =
                new SendResult<>(
                        new ProducerRecord<>(KafkaTopicNames.NOTIFICATION_EMAIL, request),
                        metadata);
        CompletableFuture<SendResult<String, Object>> future =
                CompletableFuture.completedFuture(sendResult);

        when(kafkaTemplate.send(eq(KafkaTopicNames.NOTIFICATION_EMAIL), eq(request)))
                .thenReturn(future);

        notificationService.send(request);

        verify(kafkaTemplate).send(KafkaTopicNames.NOTIFICATION_EMAIL, request);
    }

    @Test
    void send_whenKafkaFails_shouldLogError() {
        EmailRequest request =
                new EmailRequest(
                        List.of("user@example.com"),
                        EmailSenderAlias.NO_REPLY,
                        "Test Subject",
                        "<p>Hello</p>",
                        null,
                        null);

        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Kafka unavailable"));

        when(kafkaTemplate.send(eq(KafkaTopicNames.NOTIFICATION_EMAIL), eq(request)))
                .thenReturn(future);

        // Should not throw — failure is handled asynchronously via whenComplete
        // callback
        notificationService.send(request);

        verify(kafkaTemplate).send(KafkaTopicNames.NOTIFICATION_EMAIL, request);
    }
}
