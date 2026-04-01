package com.vietrecruit.feature.ai.ingestion.consumer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiIngestionConsumer {

    private final S3Client s3Client;

    @Value("${cloudflare.r2.bucket}")
    private String bucket;

    String fetchAndParseCvFromR2(String cvFileKey) {
        if (cvFileKey == null || cvFileKey.isBlank()) {
            return null;
        }
        try {
            GetObjectRequest request =
                    GetObjectRequest.builder().bucket(bucket).key(cvFileKey).build();
            var response = s3Client.getObject(request);

            // Tika auto-detects document type from magic bytes; primarily handles PDF CVs
            org.springframework.ai.reader.tika.TikaDocumentReader reader =
                    new org.springframework.ai.reader.tika.TikaDocumentReader(
                            new InputStreamResource(response));
            var docs = reader.read();

            return docs.stream()
                    .map(org.springframework.ai.document.Document::getText)
                    .reduce("", (a, b) -> a + "\n" + b)
                    .trim();
        } catch (Exception e) {
            log.error(
                    "AI ingestion: failed to fetch/parse CV from R2: key={}, error={}",
                    cvFileKey,
                    e.getMessage());
            return null;
        }
    }
}
