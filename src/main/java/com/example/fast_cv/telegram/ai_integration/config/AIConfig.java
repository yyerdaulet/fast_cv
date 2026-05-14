package com.example.fast_cv.telegram.ai_integration.config;

import com.example.fast_cv.telegram.ai_integration.providers.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AIConfig {

    @Value("${ai.provider}")
    private String provider;

    @Value("${ai.claude.api-key:}")
    private String claudeApiKey;

    @Value("${ai.openai.api-key:}")
    private String openAiApiKey;

    @Value("${ai.gemini.api-key:}")
    private String geminiApiKey;

    @Value("${ai.groq.api-key:}")
    private String groqApiKey;

    @Value("${ai.groq.model:}")
    private String groqModel;

    @Value("${ai.mistral.api-key:}")
    private String mistralApiKey;

    private void check(){
        System.out.println(groqApiKey);
    }

    @Bean
    public AiProvider aiProvider() {
        check();
        return switch (provider.toLowerCase()) {
            case "claude" -> new ClaudeProvider(claudeApiKey);
            case "openai" -> new OpenAiProvider(openAiApiKey);
            case "gemini" -> new GeminiProvider(geminiApiKey);
            case "groq" -> new GroqProvider(groqApiKey,groqModel);
            case "mistral" -> new MistralProvider(mistralApiKey);
            default -> throw new IllegalArgumentException(
                    "Unknown AI provider: '" + provider + "'. Use 'claude' or 'openai'."
            );
        };
    }
}
