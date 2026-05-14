package com.example.fast_cv.telegram.ai_integration.providers;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Google Gemini implementation of AiProvider.
 *
 * Uses Gemini 1.5 Flash via REST API.
 * Registered as a Spring bean via AiConfig — no @Component here.
 *
 * To activate: set in application.properties:
 *   ai.provider=gemini
 *   ai.gemini.api-key=${GEMINI_API_KEY}
 */
public class GeminiProvider implements AiProvider {

    private static final String MODEL   = "gemini-1.5-flash";
    private static final String API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/"
                    + MODEL + ":generateContent?key=";

    private final String apiKey;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public GeminiProvider(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public String call(String systemPrompt, String userMessage) {
        // Gemini combines system + user into a single "user" turn
        String combinedMessage = systemPrompt + "\n\n" + userMessage;

        String body = "{"
                + "\"contents\":[{"
                +   "\"role\":\"user\","
                +   "\"parts\":[{\"text\":" + jsonString(combinedMessage) + "}]"
                + "}],"
                + "\"generationConfig\":{"
                +   "\"maxOutputTokens\":2048,"
                +   "\"temperature\":0.2"
                + "}"
                + "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + apiKey))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException(
                        "Gemini API error " + response.statusCode() + ": " + response.body()
                );
            }

            return extractText(response.body());

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to call Gemini API", e);
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────

    /**
     * Extracts text from Gemini response:
     * { "candidates": [{ "content": { "parts": [{ "text": "..." }] } }] }
     */
    private String extractText(String responseBody) {
        String marker = "\"text\":\"";
        int start = responseBody.indexOf(marker);
        if (start == -1) {
            throw new RuntimeException("Unexpected Gemini response format: " + responseBody);
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
