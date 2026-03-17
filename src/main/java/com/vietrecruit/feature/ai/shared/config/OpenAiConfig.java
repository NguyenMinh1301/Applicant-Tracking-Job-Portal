package com.vietrecruit.feature.ai.shared.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.vietrecruit.feature.ai.shared.tools.CandidateSearchTool;
import com.vietrecruit.feature.ai.shared.tools.JobSearchTool;
import com.vietrecruit.feature.ai.shared.tools.SalaryBenchmarkTool;

@Configuration
public class OpenAiConfig {

    @Bean
    @Qualifier("ragChatClient")
    public ChatClient ragChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem(
                        "You are an AI assistant for VietRecruit, a Vietnamese recruitment"
                                + " platform. Be precise and structured in responses.")
                .build();
    }

    @Bean
    @Qualifier("agentChatClient")
    public ChatClient agentChatClient(
            ChatModel chatModel,
            JobSearchTool jobSearchTool,
            CandidateSearchTool candidateSearchTool,
            SalaryBenchmarkTool salaryBenchmarkTool) {
        return ChatClient.builder(chatModel)
                .defaultSystem(
                        "You are an AI agent for VietRecruit recruitment platform. Use the"
                                + " provided tools to search jobs, candidates, and salary benchmarks."
                                + " Be precise and actionable.")
                .defaultTools(jobSearchTool, candidateSearchTool, salaryBenchmarkTool)
                .build();
    }
}
