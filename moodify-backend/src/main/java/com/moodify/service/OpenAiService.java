package com.moodify.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class OpenAiService {

    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${openai.api.url}")
    private String apiUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Calls OpenAI API with a mood-aware system prompt.
     * Returns null if the call fails so the caller can fall back.
     */
    public String ask(String userName, String userMessage, String mood, List<String> recentHistory) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        try {
            String systemPrompt = buildSystemPrompt(userName, mood);

            Map<String, Object> body = new HashMap<>();
            body.put("model", "gpt-4o-mini");
            
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt));
            
            if (recentHistory != null) {
                for (String historyObj : recentHistory) {
                    // Quick parse of "User: ..." or "Moo: ..."
                    if (historyObj.startsWith("User: ")) {
                        messages.add(Map.of("role", "user", "content", historyObj.replace("User: ", "")));
                    } else if (historyObj.startsWith("Moo: ")) {
                        messages.add(Map.of("role", "assistant", "content", historyObj.replace("Moo: ", "")));
                    }
                }
            }
            messages.add(Map.of("role", "user", "content", userMessage));
            
            body.put("messages", messages);
            body.put("temperature", 0.5);
            body.put("max_tokens", 200);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    apiUrl, HttpMethod.POST, entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                String result = extractText(response.getBody());
                if (result != null && !result.isBlank()) {
                    return result;
                }
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            System.err.println("[OpenAiService] HTTP error " + e.getStatusCode() + ": " + e.getResponseBodyAsString());
        } catch (Exception e) {
            System.err.println("[OpenAiService] API call failed: " + e.getMessage());
        }
        return null;
    }

    private String buildSystemPrompt(String userName, String mood) {
        String moodContext = (mood != null && !mood.isBlank())
                ? "The user's current logged mood is: " + mood + "."
                : "The user's mood is not yet logged.";

        return """
                You are Moo — a warm, emotionally intelligent AI companion inside the Moodify wellness app.
                
                User's name: %s
                %s
                
                CRITICAL RULE — READ THE MESSAGE CAREFULLY:
                - If the user says anything negative (not feeling good, sad, tired, stressed, anxious, hurt, bad, down, sick, upset, lonely, angry, frustrated, overwhelmed, disappointed, scared, worried, not okay, not well) — respond with EMPATHY and CONCERN. NEVER respond positively to negative messages.
                - If the user says something positive (happy, great, excited, good, amazing, wonderful) — respond with warmth and celebration.
                - ALWAYS match your tone to what the user actually said. Read their words carefully before responding.
                
                RESPONSE RULES:
                - 1 to 2 sentences only. Never more.
                - Warm, human, genuine — like a caring friend
                - Acknowledge their feeling FIRST, then ask one gentle follow-up question
                - Use at most 1 emoji per response
                - NEVER say "I'm having trouble connecting"
                - NEVER give medical advice
                - NEVER repeat a previous response
                
                EXAMPLES:
                User: "i am not feeling good" → "Oh no, I'm sorry to hear that. What's been going on? 💙"
                User: "i am sad" → "I hear you, that sounds really hard. Do you want to talk about what happened?"
                User: "i am happy" → "That's wonderful! What's been making you feel so good? ✨"
                User: "i am stressed" → "That sounds exhausting. What's been weighing on you most right now?"
                """.formatted(userName, moodContext);
    }

    private String extractText(Map<String, Object> body) {
        try {
            Object choicesObj = body.get("choices");
            if (!(choicesObj instanceof List<?> choicesList) || choicesList.isEmpty()) return null;

            Object firstChoice = choicesList.get(0);
            if (!(firstChoice instanceof Map<?, ?> choiceMap)) return null;

            Object messageObj = choiceMap.get("message");
            if (!(messageObj instanceof Map<?, ?> messageMap)) return null;

            Object content = messageMap.get("content");
            return content instanceof String s ? s.trim() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
