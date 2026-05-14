package com.example.fast_cv.telegram.ai_integration.providers;

/**
 * Strategy interface for AI providers.
 * Implement this to plug in any AI (Claude, GPT, Gemini, etc.)
 * without changing the main parsing logic.
 */
public interface AiProvider {

    /**
     * Sends a prompt to the AI and returns the raw response text.
     *
     * @param systemPrompt  instructions for the AI (role, format, rules)
     * @param userMessage   the actual user text to process
     * @return raw string response from the AI
     */
    String call(String systemPrompt, String userMessage);
}
