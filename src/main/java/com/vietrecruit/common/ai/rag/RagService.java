package com.vietrecruit.common.ai.rag;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.vietrecruit.common.ai.embedding.EmbeddingService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class RagService {

    private final ChatClient ragChatClient;
    private final EmbeddingService embeddingService;

    public RagService(
            @Qualifier("ragChatClient") ChatClient ragChatClient,
            EmbeddingService embeddingService) {
        this.ragChatClient = ragChatClient;
        this.embeddingService = embeddingService;
    }

    public String generate(String userQuery, String systemContext, int topK) {
        List<Document> relevantDocs = embeddingService.search(userQuery, topK);

        String context =
                relevantDocs.stream().map(Document::getText).collect(Collectors.joining("\n---\n"));

        String augmentedPrompt =
                "Use the following context to answer the question.\n\n"
                        + "Context:\n"
                        + context
                        + "\n\n"
                        + "Additional instructions: "
                        + systemContext
                        + "\n\n"
                        + "Question: "
                        + userQuery;

        return ragChatClient.prompt().user(augmentedPrompt).call().content();
    }
}
