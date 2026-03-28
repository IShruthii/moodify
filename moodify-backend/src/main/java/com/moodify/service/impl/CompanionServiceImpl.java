package com.moodify.service.impl;

import com.moodify.dto.ChatRequest;
import com.moodify.dto.ChatResponse;
import com.moodify.entity.ChatMessage;
import com.moodify.entity.User;
import com.moodify.repository.ChatMessageRepository;
import com.moodify.repository.UserRepository;
import com.moodify.service.CompanionService;
import com.moodify.service.OpenAiService;
import com.moodify.util.MoodData;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class CompanionServiceImpl implements CompanionService {

    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final OpenAiService openAiService;

    private final Map<String, String> lastBotResponse = new ConcurrentHashMap<>();
    private final Map<String, Integer> sessionTurnCount = new ConcurrentHashMap<>();

    public CompanionServiceImpl(ChatMessageRepository chatMessageRepository,
                                UserRepository userRepository,
                                OpenAiService openAiService) {
        this.chatMessageRepository = chatMessageRepository;
        this.userRepository = userRepository;
        this.openAiService = openAiService;
    }

    @Override
    @Transactional
    public ChatResponse chat(Long userId, ChatRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String sessionId = request.getSessionId() != null
                ? request.getSessionId()
                : UUID.randomUUID().toString();

        int turn = sessionTurnCount.merge(sessionId, 1, Integer::sum);

        ChatMessage userMessage = new ChatMessage();
        userMessage.setUser(user);
        userMessage.setSender("USER");
        userMessage.setMessage(request.getMessage());
        userMessage.setSessionId(sessionId);
        chatMessageRepository.save(userMessage);

        String responseText = generateResponse(request, user.getName(), sessionId, turn, userId);
        List<String> suggestions = generateSuggestions(request, turn);
        String actionType = determineActionType(request);

        ChatMessage botMessage = new ChatMessage();
        botMessage.setUser(user);
        botMessage.setSender("BOT");
        botMessage.setMessage(responseText);
        botMessage.setSessionId(sessionId);
        chatMessageRepository.save(botMessage);

        lastBotResponse.put(sessionId, responseText);
        return new ChatResponse(responseText, sessionId, suggestions, actionType);
    }

    @Override
    public List<ChatResponse> getChatHistory(Long userId, String sessionId) {
        return chatMessageRepository
                .findByUserIdAndSessionIdOrderByCreatedAtAsc(userId, sessionId)
                .stream()
                .map(msg -> new ChatResponse(msg.getMessage(), msg.getSessionId(),
                        Collections.emptyList(), msg.getSender()))
                .collect(Collectors.toList());
    }

    // ─── Main response router ────────────────────────────────────────────────

    private String generateResponse(ChatRequest request, String userName,
                                    String sessionId, int turn, Long userId) {
        String quickAction = request.getQuickAction();
        String mood = request.getCurrentMood();
        String raw = request.getMessage() != null ? request.getMessage() : "";
        String userMsg = raw.toLowerCase().trim();
        String last = lastBotResponse.getOrDefault(sessionId, "");

        // Quick action buttons — always rule-based (fast, no API needed)
        if (quickAction != null && !quickAction.isBlank()) {
            return handleQuickAction(quickAction, userName, turn);
        }

        // Try OpenAI first for natural conversation
        if (!userMsg.isBlank()) {
            List<String> history = buildRealHistory(userId, sessionId);
            String aiReply = openAiService.ask(userName, raw, mood, history);
            if (aiReply != null && !aiReply.isBlank() && !aiReply.equals(last)) {
                return aiReply;
            }
        }

        // Fallback: rule-based intent detection
        String intentReply = detectIntentReply(userMsg, userName, mood, turn, last);
        if (intentReply != null) return intentReply;

        if (mood != null && !mood.isBlank()) {
            MoodData.MoodInfo info = MoodData.getMoodInfo(mood);
            return switch (info.getCategory()) {
                case "NEGATIVE" -> pickUnique(negativeReplies(mood, userName, turn), last);
                case "POSITIVE" -> pickUnique(positiveReplies(mood, userName, turn), last);
                default         -> pickUnique(neutralReplies(userName, turn), last);
            };
        }

        return pickUnique(genericReplies(userName, turn), last);
    }

    private List<String> buildRealHistory(Long userId, String sessionId) {
        try {
            List<ChatMessage> recent = chatMessageRepository
                    .findByUserIdAndSessionIdOrderByCreatedAtAsc(userId, sessionId);
            // Take last 6 messages for context window
            int start = Math.max(0, recent.size() - 6);
            return recent.subList(start, recent.size()).stream()
                    .map(m -> (m.getSender().equals("USER") ? "User: " : "Moo: ") + m.getMessage())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    // ─── Quick action handlers ───────────────────────────────────────────────

    private String handleQuickAction(String action, String name, int turn) {
        return switch (action) {
            case "CALM_ME" -> pickRandom(List.of(
                "Let's breathe together right now. Breathe in slowly for 4 counts... hold for 4... and let it all out for 6. You're safe. You're okay. 🌿",
                "Close your eyes for a moment. Imagine a place that feels completely safe to you. Take three slow, deep breaths. I'm right here with you. 💙",
                "Let's do a quick body scan. Start from your feet — are they tense? Relax them. Move up slowly. Breathe. You've got this. 🧘",
                "Try the 5-4-3-2-1 method with me: name 5 things you can see, 4 you can touch, 3 you can hear, 2 you can smell, 1 you can taste. It really helps. 🌸"
            ));
            case "MUSIC" -> pickRandom(List.of(
                "Music is honestly one of the best medicines. I've picked some tracks that match your energy — check the recommendations tab. 🎵",
                "Sometimes a song says what words can't. Head to your recommendations for some curated music just for how you're feeling right now. 🎶",
                "I'd suggest something soft and gentle right now. Your music recommendations are waiting — go let the sound do its thing. 🎧"
            ));
            case "JOURNAL" -> pickRandom(List.of(
                "Writing can be so healing, " + name + ". Here's a prompt: What's one thing you wish someone understood about how you're feeling right now? Take your time. 📝",
                "Try this: write without stopping for 5 minutes. Don't edit, don't judge — just let it flow. Whatever comes out is valid. 📖",
                "Here's a gentle prompt: If your feelings had a color today, what would it be and why? There's no wrong answer. ✍️",
                "What's one small thing that happened today that you want to remember? Write it down — the good stuff deserves to be captured too. 🌟"
            ));
            case "PLAY_GAME" -> pickRandom(List.of(
                "A little play goes a long way! I've picked a game that matches your energy — light, fun, and just for you. 🎮",
                "Sometimes the best thing is to just play. Check out the games section — there's something there that'll help shift your mood. 🕹️",
                "Your brain deserves a little break and some fun. Head to the games — I think you'll enjoy what's there right now. 🎯"
            ));
            case "NEED_HELP" -> pickRandom(List.of(
                "I hear you, and I'm really glad you reached out. You don't have to carry this alone. If things feel really heavy, please talk to someone you trust — a family member, a close friend, or a counselor. You matter so much. 💙",
                "Thank you for telling me. That took courage. Please know that reaching out to a trusted person — a friend, family member, or professional — is one of the bravest things you can do. You deserve support. 🌿",
                "I'm here with you right now. And I want you to know — if things feel too heavy to carry alone, please reach out to someone who can truly be there for you in person. You are not alone. 💜"
            ));
            default -> "I'm here with you. What's on your mind? 😊";
        };
    }

    // ─── Intent detection ────────────────────────────────────────────────────

    private String detectIntentReply(String msg, String name, String mood, int turn, String last) {

        // Food / eating
        if (matches(msg, "eat", "eating", "food", "hungry", "hunger", "snack", "meal",
                "want to eat", "something to eat", "order food", "cook", "restaurant", "pizza",
                "burger", "biryani", "chocolate", "ice cream", "dessert", "coffee", "tea")) {
            return pickUnique(List.of(
                "Food is always a good idea! 🍜 Head to your recommendations — I've picked some great options based on how you're feeling. Want comfort food, something light, or a treat?",
                "Ooh, eating something sounds like a great plan right now. Check the Food tab in your recommendations — I've curated it just for your mood. 🍽️",
                "Yes! Sometimes the right food really does help. Your recommendations have mood-based food suggestions — go check the Food tab. 🌮",
                "Comfort food hits different when you need it. I've got some great options in your recommendations — Swiggy and Zomato links included! 🍕"
            ), last);
        }

        // Fight / argument
        if (matches(msg, "fight", "argument", "argue", "argued", "fought", "conflict",
                "had a fight", "had an argument", "fight with", "argument with")) {
            return pickUnique(List.of(
                "Ugh, fights are so draining — especially with people we care about. What happened? I'm here to listen without any judgment. 💙",
                "That sounds really tough. Arguments leave us feeling so unsettled. Do you want to talk about what went down? 🌿",
                "I'm sorry you went through that. Conflicts with people close to us can really sting. How are you feeling about it right now?",
                "That's hard. Sometimes we just need to vent before we can even think clearly. Go ahead — tell me what happened. 💜"
            ), last);
        }

        // Greetings — only match if it's a short standalone greeting on turn 1
        if (turn <= 1 && matches(msg, "hi", "hello", "hey", "hii", "heyy", "sup", "yo")
                && msg.length() <= 10) {
            return pickUnique(List.of(
                "Hey " + name + "! So glad you're here. How are you feeling right now? 😊",
                "Hi " + name + "! What's on your mind today? 🌟",
                "Hey! How's your heart doing today? 💙"
            ), last);
        }

        // Not feeling well / unwell
        if (matches(msg, "not feeling well", "not well", "feeling bad", "not good", "unwell", "sick")) {
            return pickUnique(List.of(
                "Oh no, I'm sorry to hear that. Are you feeling physically unwell, or is it more of an emotional heaviness? Either way, I'm here. 💙",
                "That doesn't sound good at all. Tell me more — what's going on? I want to understand. 🌿",
                "I'm sorry you're not feeling well. Sometimes just talking about it helps. What's been happening? 💜"
            ), last);
        }

        // What to do / help / suggestions
        if (matches(msg, "what to do", "what should i do", "help me", "i don't know what to do",
                "don't know", "confused", "lost", "suggest", "suggestion")) {
            return pickUnique(List.of(
                "Let's figure this out together. First — what feels most overwhelming right now? Once we name it, we can take it one small step at a time. 🌿",
                "When everything feels unclear, the best thing is to start tiny. What's one small thing that might make you feel even 1% better right now?",
                "I hear you. Sometimes we just need someone to think alongside us. Tell me what's going on and we'll work through it together. 💙",
                "Here's what I'd suggest: take a breath first. Then tell me the one thing that's bothering you most. We'll start there. 🌸"
            ), last);
        }

        // Sad / crying
        if (matches(msg, "sad", "crying", "cry", "tears", "heartbroken", "broken", "hurt", "pain")) {
            return pickUnique(List.of(
                "I'm really sorry you're feeling this way. Sadness is heavy, and it's okay to let yourself feel it. I'm right here with you. 💙",
                "It's okay to cry, " + name + ". Tears are just feelings that need somewhere to go. What's been weighing on your heart?",
                "That sounds really painful. You don't have to pretend to be okay here. What happened? 🌿",
                "I see you, and I hear you. Feeling sad is hard. Would you like to talk about what's going on, or would a gentle distraction help more right now?"
            ), last);
        }

        // Stress / anxiety / overwhelmed
        if (matches(msg, "stress", "stressed", "anxious", "anxiety", "worried", "worry",
                "overwhelmed", "too much", "pressure", "panic")) {
            return pickUnique(List.of(
                "That sounds really overwhelming. Let's slow down — take one deep breath with me right now. In... and out. You're handling more than you realize. 🌿",
                "Stress has a way of making everything feel urgent at once. Let's break it down — what's the one thing weighing on you most right now?",
                "I hear you. When everything piles up, it can feel impossible. But you don't have to solve it all at once. What's the first thing on your mind?",
                "Anxiety is exhausting. You're not weak for feeling this — you're human. Want to try a quick breathing exercise together? 🧘"
            ), last);
        }

        // Angry / frustrated
        if (matches(msg, "angry", "anger", "mad", "furious", "frustrated", "frustrating",
                "annoyed", "irritated", "rage")) {
            return pickUnique(List.of(
                "I hear that frustration. It's completely valid to feel angry — it usually means something important to you isn't being respected. What happened? 🌊",
                "Anger is a signal, not a flaw. Something clearly matters to you here. Tell me what's going on — I'm listening.",
                "That sounds really frustrating. Sometimes just venting helps. Go ahead — I'm not going anywhere. 💙",
                "I get it. Some things are genuinely infuriating. Would it help to talk it through, or would you rather try something to release that energy?"
            ), last);
        }

        // Tired / exhausted
        if (matches(msg, "tired", "exhausted", "drained", "no energy", "sleepy", "fatigue", "worn out")) {
            return pickUnique(List.of(
                "Your body and mind are clearly asking for rest. That's not laziness — that's wisdom. Is there something keeping you from resting? 😴",
                "Being tired is your body's way of saying it needs care. What would feel most restorative for you right now?",
                "I hear you. Sometimes the most productive thing you can do is rest. Give yourself permission to slow down today. 🌿",
                "Exhaustion is real and it's valid. Have you been pushing yourself too hard lately? Tell me what's been going on."
            ), last);
        }

        // Happy / good / great
        if (matches(msg, "happy", "great", "amazing", "wonderful", "fantastic", "good", "awesome",
                "excited", "joyful", "love it", "loving")) {
            return pickUnique(List.of(
                "That genuinely makes me happy to hear! What's been the highlight of your day? ✨",
                "I love this energy! Tell me everything — what's going so well? 🌟",
                "Yes! This is what I love to hear. What's been making you feel this way? 😊",
                "That's so good, " + name + "! Soak it in — you deserve these good moments. What's been going on? 🎉"
            ), last);
        }

        // Lonely
        if (matches(msg, "lonely", "alone", "no one", "nobody", "isolated", "miss", "missing")) {
            return pickUnique(List.of(
                "Loneliness is one of the hardest feelings. But you reached out, and that matters. I'm here with you right now. 💙",
                "You're not as alone as it might feel right now. I'm here, and I'm listening. Who do you miss most?",
                "That feeling of being alone even in a crowd — I understand. Tell me what's been going on. 🌿",
                "I'm glad you came here. Sometimes just having someone to talk to makes a difference. What's been making you feel this way?"
            ), last);
        }

        // Bored
        if (matches(msg, "bored", "boring", "nothing to do", "boredom", "dull")) {
            return pickUnique(List.of(
                "Boredom is actually your brain saying it's ready for something new! Want me to suggest a game, a challenge, or something creative? 🎯",
                "Interesting — boredom often hides a deeper restlessness. Is there something you've been wanting to do but haven't started yet?",
                "Let's fix that! I can suggest a game, a journal prompt, or a fun challenge. What sounds most appealing right now? 🎮"
            ), last);
        }

        // Achievement / success / completed something
        if (matches(msg, "completed", "finished", "done", "achieved", "passed", "won",
                "success", "project", "exam", "interview", "got the job", "promotion")) {
            return pickUnique(List.of(
                "That's genuinely amazing, " + name + "! Completing something you worked hard on is such a big deal. How does it feel? 🌟",
                "Yes! You did it! That kind of accomplishment deserves to be celebrated. What was the hardest part? ✨",
                "That's so exciting! You should be really proud of yourself. What's next for you? 🎉",
                "Completing a project is no small thing — it takes real dedication. Celebrate this moment! 💪"
            ), last);
        }

        // Thank you
        if (matches(msg, "thank you", "thanks", "thank u", "thankyou", "ty")) {
            return pickUnique(List.of(
                "Always here for you, " + name + ". 💙 Is there anything else on your mind?",
                "Of course! That's what I'm here for. How are you feeling now? 🌿",
                "You're so welcome. Remember — you can always come back whenever you need. 😊"
            ), last);
        }

        // Okay / fine / alright
        if (matches(msg, "okay", "ok", "fine", "alright", "i'm fine", "im fine", "i am fine")) {
            return pickUnique(List.of(
                "Sometimes 'okay' is enough. But if there's something underneath that, I'm here to listen. 😊",
                "Okay is a start! Is there anything specific on your mind today, or just checking in? 🌿",
                "I hear you. 'Fine' can mean a lot of things. How are you really doing? 💙"
            ), last);
        }

        // Music / listen
        if (matches(msg, "music", "song", "listen", "playlist", "spotify", "youtube")) {
            return pickUnique(List.of(
                "Music is such a good call right now. 🎵 Check the Music tab in your recommendations — I've picked tracks that match your mood.",
                "Yes! The right song can shift everything. Head to your recommendations for a curated playlist just for you. 🎶",
                "I love that you want music. Your recommendations have mood-matched playlists on Spotify, YouTube Music, JioSaavn and Gaana. 🎧"
            ), last);
        }

        // Game / play
        if (matches(msg, "game", "play", "bored game", "want to play")) {
            return pickUnique(List.of(
                "Let's play! 🎮 Check the Games tab in your recommendations — I've picked one that matches your energy right now.",
                "A little play goes a long way. Head to the Games tab — there's something there just for your mood. 🕹️",
                "Games are a great idea. Your recommendations have mood-matched games waiting for you! 🎯"
            ), last);
        }

        // Follow-up for deeper conversation (turn > 2 and short message)
        if (turn > 2 && msg.length() < 20) {
            return pickUnique(List.of(
                "Tell me more — I want to really understand what you're going through. 💙",
                "I'm listening. What else is on your mind? 🌿",
                "Go on — there's no rush here. I've got all the time for you. 😊",
                "I hear you. What's the part that's bothering you most right now?"
            ), last);
        }

        return null;
    }

    // ─── Mood-based response pools ───────────────────────────────────────────

    private List<String> negativeReplies(String mood, String name, int turn) {
            String m = mood.toLowerCase();
            if (turn == 1) {
                return List.of(
                    "I hear you, " + name + ". Feeling " + m + " is completely valid — it takes courage to acknowledge it. What's been going on? 💙",
                    "That sounds really hard. Feeling " + m + " is tough, and your feelings are real. I'm here with you. 🌿",
                    "Thank you for sharing that. Feeling " + m + " can be exhausting. What's been weighing on you most? 💜",
                    "I'm glad you came here. Feeling " + m + " is something many people go through, and you don't have to face it alone. Tell me more. 💙",
                    "That's a lot to carry. I want to understand — what's been happening that's made you feel " + m + "? 🌿"
                );
            }
            return List.of(
                "I'm still here with you. What would feel most helpful right now — talking it through, a distraction, or just some quiet company? 💙",
                "You're doing really well just by being here and talking. What's one small thing that might help you feel even a little better?",
                "Sometimes when we feel " + m + ", the best thing is just to take it one breath at a time. What do you need most right now? 🌿",
                "I want to understand better. Can you tell me more about what's been happening? 💜",
                "You don't have to have it all figured out. Just being here and talking is already a step. What's on your heart right now?",
                "That makes a lot of sense. Feeling " + m + " after what you've been through is completely understandable. 💙",
                "I'm not going anywhere. Take your time — what's the part that's hardest to deal with right now?",
                "It's okay to sit with this feeling for a moment. You don't have to rush past it. What's coming up for you? 🌿",
                "You're stronger than you think, even when it doesn't feel that way. What's been the hardest part of today?",
                "I hear you. Sometimes just naming the feeling out loud is the first step. What else is going on? 💜"
            );
        }


    private List<String> positiveReplies(String mood, String name, int turn) {
        String m = mood.toLowerCase();
        return List.of(
            "That's wonderful, " + name + "! Feeling " + m + " is such a gift. What's been making you feel this way? ✨",
            "I love hearing this! Your " + m + " energy is beautiful. What's been the highlight of your day? 🌟",
            "This makes me so happy! When we feel " + m + ", it's a perfect time to do something meaningful. What's calling to you? 😊",
            "Yes! Soak in this feeling, " + name + ". You deserve it. What's been going well? 🎉",
            "Feeling " + m + " is something to celebrate. Tell me everything — I want to hear all about it! 🌸",
            "That energy is contagious! What's been the best part of your day so far? ✨",
            "I'm so glad you're feeling " + m + ". What do you want to do with this good energy today? 🌟",
            "That's genuinely great to hear. What's been contributing to this feeling? 😊",
            "Love this! Feeling " + m + " is a sign things are going right. What's been working well for you? 🌸",
            "You deserve to feel this way. What's one thing you want to do to make the most of this mood? ✨",
            "This is so good to hear! What happened today that made you feel " + m + "? 🌟",
            "That's beautiful. Moments like these are worth holding onto. What made today special? 😊"
        );
    }

    private List<String> neutralReplies(String name, int turn) {
        return List.of(
            "Hey " + name + ", neutral days are completely valid. Is there anything specific on your mind today? 😊",
            "Sometimes neutral is actually peaceful. How are you really feeling underneath it all? 🌿",
            "I'm here either way. Is there something you'd like to talk about, or just wanted some company? 💙",
            "Neutral can be a good place to start. What would make today feel a little more meaningful for you?",
            "That's okay. Not every day has to be a peak. What's been on your mind lately? 😊",
            "Sometimes 'okay' is exactly where we need to be. Is there anything you've been thinking about? 🌿",
            "I hear you. What would feel good to do or talk about right now? 💙",
            "Neutral days can sometimes be a reset. What's one small thing that might make today feel a bit better? 😊",
            "That's completely fine. Is there something on your mind you'd like to explore? 🌿",
            "I'm here with you. What's been going on in your world lately? 💙"
        );
    }

    private List<String> genericReplies(String name, int turn) {
        if (turn == 1) {
            return List.of(
                "Hey " + name + "! I'm here and I'm listening. What's on your mind today? 😊",
                "Hi there! So glad you came to chat. How are you feeling right now? 💙",
                "Hello " + name + "! What's going on? I've got time for you. 🌿",
                "Hey! Good to see you here. What's been on your heart today? 😊",
                "Hi " + name + "! I'm all ears. What would you like to talk about? 💙"
            );
        }
        return List.of(
            "That's really interesting. Tell me more — I want to understand fully. 💙",
            "I hear you. What's the part that matters most to you right now? 🌿",
            "Go on — I'm listening and I've got all the time for you. 😊",
            "That makes sense. How are you feeling about it? 💜",
            "I'm with you. What would feel most helpful right now — talking it through, or trying something to shift the mood?",
            "You can share anything here — no judgment, ever. What's on your heart? 🌸",
            "I appreciate you sharing that with me. What else is going on? 💙",
            "That sounds like a lot. What's been the hardest part to deal with? 🌿",
            "I'm really glad you're talking to me about this. What do you need most right now? 😊",
            "Tell me more — every detail helps me understand better. 💜",
            "I'm here and I'm not going anywhere. What's weighing on you most? 🌸",
            "That's worth talking about. What's been happening? 💙"
        );
    }

    // ─── Suggestions ─────────────────────────────────────────────────────────

    private List<String> generateSuggestions(ChatRequest request, int turn) {
        String mood = request.getCurrentMood();
        String msg = request.getMessage() != null ? request.getMessage().toLowerCase() : "";

        // Context-sensitive suggestions
        if (matches(msg, "stress", "anxious", "overwhelmed", "panic", "worried")) {
            return List.of("🌿 Calm Me", "🎵 Music", "📝 Journal", "🎮 Play Game");
        }
        if (matches(msg, "sad", "cry", "lonely", "hurt", "broken")) {
            return List.of("💙 Need Help", "🎵 Music", "📝 Journal", "🌿 Calm Me");
        }
        if (matches(msg, "bored", "nothing to do")) {
            return List.of("🎮 Play Game", "🎵 Music", "📝 Journal");
        }
        if (matches(msg, "angry", "frustrated", "mad")) {
            return List.of("🌿 Calm Me", "🎮 Play Game", "📝 Journal");
        }

        if (mood == null) {
            return turn <= 1
                    ? List.of("😊 I'm okay", "😔 Not great", "😰 Stressed", "💙 Need Help")
                    : List.of("🌿 Calm Me", "🎵 Music", "🎮 Play Game", "📝 Journal");
        }

        MoodData.MoodInfo info = MoodData.getMoodInfo(mood);
        return switch (info.getCategory()) {
            case "NEGATIVE" -> List.of("🌿 Calm Me", "🎵 Music", "📝 Journal", "🎮 Play Game", "💙 Need Help");
            case "POSITIVE" -> List.of("🎵 Music", "🎮 Play Game", "⚡ Challenge", "📝 Journal");
            default         -> List.of("🎵 Music", "🎮 Play Game", "📝 Journal", "🌿 Calm Me");
        };
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /** Pick a response that is different from the last one sent */
    private String pickUnique(List<String> options, String last) {
        if (options.size() == 1) return options.get(0);
        List<String> filtered = options.stream()
                .filter(r -> !r.equals(last))
                .collect(Collectors.toList());
        List<String> pool = filtered.isEmpty() ? options : filtered;
        return pool.get(new Random().nextInt(pool.size()));
    }

    private String pickRandom(List<String> options) {
        return options.get(new Random().nextInt(options.size()));
    }

    /** Check if message contains any of the given keywords */
    private boolean matches(String msg, String... keywords) {
        for (String kw : keywords) {
            if (msg.contains(kw)) return true;
        }
        return false;
    }

    private String determineActionType(ChatRequest request) {
        if (request.getQuickAction() != null) return request.getQuickAction();
        return "CHAT";
    }
}
