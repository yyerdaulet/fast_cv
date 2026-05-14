package com.example.fast_cv.telegram.ai_integration.providers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Groq implementation of AiProvider.
 *
 * Uses OpenAI-compatible Chat Completions API with streaming.
 * Collects all streamed chunks and returns the full response as a string.
 *
 * To activate: set in application.properties:
 *   ai.provider=groq
 *   ai.groq.api-key=${GROQ_API_KEY}
 *   ai.groq.model=meta-llama/llama-4-scout-17b-16e-instruct   (optional, default below)
 */
public class GroqProvider implements AiProvider {

    private static final String API_URL       = "https://api.groq.com/openai/v1/chat/completions";
    private static final String DEFAULT_MODEL = "meta-llama/llama-4-scout-17b-16e-instruct";

    private final String apiKey;
    private final String model;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public GroqProvider(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model  = (model == null || model.isBlank()) ? DEFAULT_MODEL : model;
    }

    @Override
    public String call(String systemPrompt, String userMessage) {
        String body = "{"
                + "\"model\":"              + jsonString(model)       + ","
                + "\"temperature\":1,"
                + "\"max_completion_tokens\":1024,"
                + "\"top_p\":1,"
                + "\"stream\":true,"
                + "\"stop\":null,"
                + "\"messages\":["
                +   "{\"role\":\"system\",\"content\":" + jsonString(systemPrompt) + "},"
                +   "{\"role\":\"user\",\"content\":"   + jsonString(userMessage)  + "}"
                + "]"
                + "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type",  "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            // Stream response line by line (SSE format: "data: {...}\n")
            HttpResponse<java.io.InputStream> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                String error = new String(response.body().readAllBytes());
                throw new RuntimeException("Groq API error " + response.statusCode() + ": " + error);
            }

            return collectStream(response.body());

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to call Groq API", e);
        }
    }

    // ─── Stream collector ─────────────────────────────────────────────

    /**
     * Reads SSE stream and collects all delta content chunks into one string.
     *
     * Each SSE line looks like:
     *   data: {"choices":[{"delta":{"content":"Hello"}}]}
     *   data: [DONE]
     */
    private String collectStream(java.io.InputStream inputStream) throws IOException {
        StringBuilder fullResponse = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data: ")) continue;

                String data = line.substring(6).trim();
                if (data.equals("[DONE]")) break;

                String chunk = extractDeltaContent(data);
                if (chunk != null) {
                    fullResponse.append(chunk);
                }
            }
        }

        return fullResponse.toString();
    }

    /**
     * Extracts delta content from a single SSE chunk JSON:
     * {"choices":[{"delta":{"content":"..."}, ...}]}
     */
    private String extractDeltaContent(String chunkJson) {
        String marker = "\"content\":\"";
        int start = chunkJson.indexOf(marker);
        if (start == -1) return null;
        start += marker.length();

        StringBuilder sb = new StringBuilder();
        for (int i = start; i < chunkJson.length(); i++) {
            char c = chunkJson.charAt(i);
            if (c == '\\' && i + 1 < chunkJson.length()) {
                char next = chunkJson.charAt(i + 1);
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

    // ─── Helpers ──────────────────────────────────────────────────────

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