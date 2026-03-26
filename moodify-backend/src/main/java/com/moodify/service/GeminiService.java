package com.moodify.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class GeminiService {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Calls Gemini API with a mood-aware system prompt.
     * Returns null if the call fails so the caller can fall back.
     */
    public String ask(String userName, String userMessage, String mood, List<String> recentHistory) {
        // Skip if API key not configured
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        try {
            String systemPrompt = buildSystemPrompt(userName, mood);
            String fullPrompt = buildFullPrompt(systemPrompt, recentHistory, userMessage);

            Map<String, Object> body = new HashMap<>();
            List<Map<String, Object>> contents = new ArrayList<>();

            Map<String, Object> content = new HashMap<>();
            List<Map<String, Object>> parts = new ArrayList<>();
            Map<String, Object> part = new HashMap<>();
            part.put("text", fullPrompt);
            parts.add(part);
            content.put("parts", parts);
            contents.add(content);
            body.put("contents", contents);

            // Safety + generation config
            Map<String, Object> genConfig = new HashMap<>();
            genConfig.put("temperature", 0.85);
            genConfig.put("maxOutputTokens", 200);
            genConfig.put("topP", 0.9);
            body.put("generationConfig", genConfig);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String url = apiUrl + "?key=" + apiKey;
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                String result = extractText(response.getBody());
                if (result != null && !result.isBlank()) {
                    return result;
                }
            } else {
                System.err.println("[GeminiService] Non-200 response: " + response.getStatusCode());
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            System.err.println("[GeminiService] HTTP error " + e.getStatusCode() + ": " + e.getResponseBodyAsString());
        } catch (Exception e) {
            System.err.println("[GeminiService] API call failed: " + e.getMessage());
        }
        return null;
    }

    private String buildSystemPrompt(String userName, String mood) {
        String moodContext = (mood != null && !mood.isBlank())
                ? "The user's current mood is: " + mood + "."
                : "The user's mood is unknown.";

        return """
                You are Moo, a warm, caring, emotionally intelligent AI companion inside the Moodify app.
                Your role is to be a supportive friend — not a therapist, not a robot.
                
                User's name: %s
                %s
                
                Rules you must follow:
                - Always respond in 1-3 short sentences. Never write long paragraphs.
                - Be warm, human, and conversational. Never sound clinical or robotic.
                - Validate the user's feelings first before suggesting anything.
                - If the user mentions food/eating, suggest they check the Food tab in recommendations.
                - If the user mentions music, suggest the Music tab.
                - If the user mentions games, suggest the Games tab.
                - If the user seems in serious distress, gently suggest talking to a trusted person.
                - Never repeat the same response twice in a row.
                - Never diagnose, prescribe, or give medical advice.
                - Use 1 emoji per response maximum.
                - Respond in the same language the user writes in.
                """.formatted(userName, moodContext);
    }

    private String buildFullPrompt(String system, List<String> history, String userMessage) {
        StringBuilder sb = new StringBuilder();
        sb.append(system).append("\n\n");
        sb.append("Conversation so far:\n");
        for (String h : history) {
            sb.append(h).append("\n");
        }
        sb.append("User: ").append(userMessage).append("\n");
        sb.append("Moo:");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> body) {
        try {
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) body.get("candidates");
            if (candidates == null || candidates.isEmpty()) return null;
            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            if (content == null) return null;
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            if (parts == null || parts.isEmpty()) return null;
            String text = (String) parts.get(0).get("text");
            return text != null ? text.trim() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
