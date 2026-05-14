package com.example.fast_cv.telegram.ai_integration.providers;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Mistral AI implementation of AiProvider.
 *
 * Uses Mistral Small via REST API.
 * Registered as a Spring bean via AiConfig — no @Component here.
 *
 * To activate: set in application.properties:
 *   ai.provider=mistral
 *   ai.mistral.api-key=${MISTRAL_API_KEY}
 */
public class MistralProvider implements AiProvider {

    private static final String MODEL   = "mistral-small-latest";
    private static final String API_URL = "https://api.mistral.ai/v1/chat/completions";

    private final String apiKey;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public MistralProvider(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public String call(String systemPrompt, String userMessage) {
        String body = "{"
                + "\"model\":\"" + MODEL + "\","
                + "\"messages\":["
                +   "{\"role\":\"system\",\"content\":" + jsonString(systemPrompt) + "},"
                +   "{\"role\":\"user\",\"content\":"   + jsonString(userMessage)  + "}"
                + "],"
                + "\"max_tokens\":2048,"
                + "\"temperature\":0.2"
                + "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException(
                        "Mistral API error " + response.statusCode() + ": " + response.body()
                );
            }

            return extractText(response.body());

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to call Mistral API", e);
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────

    /**
     * Extracts text from Mistral response:
     * { "choices": [{ "message": { "content": "..." } }] }
     */
    private String extractText(String responseBody) {
        String marker = "\"content\":\"";
        int start = responseBody.indexOf(marker);
        if (start == -1) {
            throw new RuntimeException("Unexpected Mistral response format: " + responseBody);
        }
        start += marker.length();

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
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

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
