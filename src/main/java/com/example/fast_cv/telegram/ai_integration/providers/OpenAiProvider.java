package com.example.fast_cv.telegram.ai_integration.providers;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * AiProvider implementation for OpenAI GPT.
 *
 * Usage:
 *   AiProvider ai = new OpenAiProvider(System.getenv("OPENAI_API_KEY"));
 *   CvParserService parser = new CvParserService(ai);
 */
public class OpenAiProvider implements AiProvider {

    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String MODEL   = "gpt-4o";

    private final String apiKey;
    private final HttpClient httpClient;

    public OpenAiProvider(String apiKey) {
        this.apiKey     = apiKey;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public String call(String systemPrompt, String userMessage) {
        String body = "{"
             + "\"model\":\"" + MODEL + "\","
             + "\"messages\":["
             +   "{\"role\":\"system\",\"content\":" + jsonString(systemPrompt) + "},"
             +   "{\"role\":\"user\",\"content\":"   + jsonString(userMessage)  + "}"
             + "],"
             + "\"max_tokens\":2048"
             + "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type",  "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException(
                    "OpenAI API error " + response.statusCode() + ": " + response.body()
                );
            }

            return extractText(response.body());

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to call OpenAI API", e);
        }
    }

    /**
     * Extracts content from OpenAI response:
     * { "choices": [{ "message": { "content": "..." } }] }
     */
    private String extractText(String responseBody) {
        String marker = "\"content\":\"";
        // Skip the first occurrence which might be in the request echo
        int start = responseBody.lastIndexOf(marker);
        if (start == -1) {
            throw new RuntimeException("Unexpected OpenAI response format: " + responseBody);
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
