package com.example.fast_cv.telegram.ai_integration.providers;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * AiProvider implementation for Anthropic Claude.
 *
 * To switch to GPT or Gemini — just implement AiProvider in a new class,
 * no other code changes needed.
 */
public class ClaudeProvider implements AiProvider {

    private static final String API_URL  = "https://api.anthropic.com/v1/messages";
    private static final String MODEL    = "claude-opus-4-5";
    private static final String VERSION  = "2023-06-01";

    private final String apiKey;
    private final HttpClient httpClient;

    public ClaudeProvider(String apiKey) {
        this.apiKey     = apiKey;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public String call(String systemPrompt, String userMessage) {
        String body = buildRequestBody(systemPrompt, userMessage);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type",      "application/json")
                .header("x-api-key",         apiKey)
                .header("anthropic-version", VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException(
                    "Claude API error " + response.statusCode() + ": " + response.body()
                );
            }

            return extractText(response.body());

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to call Claude API", e);
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────

    private String buildRequestBody(String systemPrompt, String userMessage) {
        // Manual JSON build (no extra deps needed)
        return "{"
             + "\"model\":\"" + MODEL + "\","
             + "\"max_tokens\":2048,"
             + "\"system\":" + jsonString(systemPrompt) + ","
             + "\"messages\":[{"
             +   "\"role\":\"user\","
             +   "\"content\":" + jsonString(userMessage)
             + "}]"
             + "}";
    }

    /**
     * Extracts the text from Claude's response JSON.
     * Response structure: { "content": [{ "type": "text", "text": "..." }] }
     */
    private String extractText(String responseBody) {
        // Simple extraction without extra JSON library
        String marker = "\"text\":\"";
        int start = responseBody.indexOf(marker);
        if (start == -1) {
            throw new RuntimeException("Unexpected Claude response format: " + responseBody);
        }
        start += marker.length();

        // Walk forward, handle escaped quotes
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < responseBody.length(); i++) {
            char c = responseBody.charAt(i);
            if (c == '\\' && i + 1 < responseBody.length()) {
                char next = responseBody.charAt(i + 1);
                switch (next) {
                    case '"':  sb.append('"');  i++; break;
                    case 'n':  sb.append('\n'); i++; break;
                    case 't':  sb.append('\t'); i++; break;
                    case '\\': sb.append('\\'); i++; break;
                    default:   sb.append(c);
                }
            } else if (c == '"') {
                break; // end of text value
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Wraps a Java string into a JSON string with proper escaping */
    private String jsonString(String value) {
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
             + "\"";
    }
}
