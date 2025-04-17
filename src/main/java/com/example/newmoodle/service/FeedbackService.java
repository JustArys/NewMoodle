package com.example.newmoodle.service;

import com.example.newmoodle.model.*;
import com.example.newmoodle.repository.FeedbackRepository;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;


@Service
@RequiredArgsConstructor
public class FeedbackService {

    private static final Logger logger =  LoggerFactory.getLogger(FeedbackService.class);
    private final SubmissionService submissionService;
    private final FeedbackRepository feedbackRepository;
    private final UserService userService;
    private final FileService fileService;

    @Value("${openai.api.key}")
    private String openaiApiKey;

    @Transactional
    public Feedback generateFeedback(Long submissionId, User teacher, Language language) throws Exception {
        Submission submission = submissionService.getSubmissionById(submissionId);
        Feedback feedback = null;
        String chat = getFeedbackFromOpenAI(fileService.extractText(submission.getFileUrl()), language);
        feedback = Feedback.builder()
                .submission(submission)
                .teacher(teacher)
                .aiGenerated(chat)
                .build();

        submission.setStatus(SubmissionStatus.REVIEWED);
        submissionService.updateSubmission(submission);
        assert feedback != null;
        return feedbackRepository.save(feedback);
    }

    private String getFeedbackFromOpenAI(String text, Language language) {

        OpenAiService service = new OpenAiService(openaiApiKey);

        List<ChatMessage> messages = getChatMessages(text, language);

        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest
                .builder()
                .model("gpt-4o-mini") // Or gpt-3.5-turbo, as appropriate
                .messages(messages)
                .maxTokens(1000)
                .temperature(0.7)
                .build();

        try {
            StringBuilder feedback = new StringBuilder();
            service.streamChatCompletion(chatCompletionRequest)
                    .doOnError(Throwable::printStackTrace)
                    .blockingForEach(c -> c.getChoices().forEach(
                            choice -> {
                                String content = choice.getMessage().getContent();
                                if (content != null) {
                                    feedback.append(content);
                                }
                            }
                    ));
            return feedback.toString();

        } catch (Exception e) {
            logger.error("Error calling OpenAI API:", e);
            return "Failed to get feedback from OpenAI: " + e.getMessage();
        }
    }

    private static List<ChatMessage> getChatMessages(String text, Language language) {
        String languageName = switch (language) { // Switch on the enum itself
            case ENGLISH -> "English";
            case KAZAKH -> "Kazakh";
            case RUSSIAN -> "Russian";
            default -> "English"; // Default to English
        };

        List<ChatMessage> messages = new ArrayList<>();
        ChatMessage userMessage = new ChatMessage(ChatMessageRole.USER.value(),
                "Provide detailed feedback in " + languageName + " on the following student assignment:\n" + text);
        messages.add(userMessage);
        return messages;
    }
}
