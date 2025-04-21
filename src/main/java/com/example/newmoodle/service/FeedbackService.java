package com.example.newmoodle.service;

import com.example.newmoodle.dto.FeedbackDto;
import com.example.newmoodle.mapper.FeedbackMapper;
import com.example.newmoodle.model.*;
import com.example.newmoodle.repository.FeedbackRepository;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private static final Logger logger = LoggerFactory.getLogger(FeedbackService.class);
    private final SubmissionService submissionService;
    private final FeedbackRepository feedbackRepository;
    private final FileService fileService;
    private final FeedbackMapper feedbackMapper;

    @Value("${openai.api.key}")
    private String openaiApiKey;

    @Transactional
    public FeedbackDto generateFeedbackAndGetDto(Long submissionId, User teacher, Language language) throws Exception {
        Feedback feedbackEntity = generateFeedbackInternal(submissionId, teacher, language);
        return feedbackMapper.feedbackToFeedbackDto(feedbackEntity);
    }

    private Feedback generateFeedbackInternal(Long submissionId, User teacher, Language language) throws Exception {
        Submission submission = submissionService.getSubmissionById(submissionId);
        String fileKey = submission.getFileUrl();
        if (fileKey == null || fileKey.isEmpty()) {
            logger.error("Submission ID {} has no associated file.", submissionId);
            throw new IllegalArgumentException("Submission has no file associated.");
        }

        String generatedContent;
        if (isImageFile(fileKey)) {
            logger.info("Image file detected for key {} – embedding as Base64.", fileKey);
            generatedContent = callOpenAIWithImageBase64(fileKey, language);
        } else {
            logger.info("Non‑image file detected for key {} – extracting text.", fileKey);
            String text = fileService.extractText(fileKey);
            generatedContent = callOpenAIWithText(text, language);
        }

        Feedback feedback = Feedback.builder()
                .submission(submission)
                .teacher(teacher)
                .aiGenerated(generatedContent)
                .build();

        submission.setStatus(SubmissionStatus.REVIEWED);
        submissionService.updateSubmission(submission);
        return feedbackRepository.save(feedback);
    }

    private String callOpenAIWithText(String text, Language language) {
        OpenAIClient client = OpenAIOkHttpClient.builder()
                .apiKey(openaiApiKey)
                .build();

        String prompt = String.format(
                "Provide detailed feedback in %s on the following student assignment text:\n%s",
                getLanguageName(language),
                (text == null || text.isBlank()) ? "[No text provided]" : text
        );

        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(ChatModel.GPT_4O_MINI)
                .addUserMessage(prompt)
                .temperature(0.7)
                .maxCompletionTokens(1000)
                .build();

        ChatCompletion completion = client.chat().completions().create(params);
        return extractContent(completion);
    }

    private String callOpenAIWithImageBase64(String fileKey, Language language) throws IOException {
        // 1) Download raw bytes
        byte[] imageBytes = fileService.downloadFileAsBytes(fileKey);

        // 2) Detect MIME type from extension
        String mimeType = fileService.getMimeType(fileKey);

        // 3) Encode to Base64
        String b64 = Base64.getEncoder().encodeToString(imageBytes);

        // 4) Build a data URI markdown
        String prompt = String.format(
                "Provide detailed feedback in %s on the student assignment shown in the image below:",
                getLanguageName(language)
        );
        String dataUri = "data:" + mimeType + ";base64," + b64;
        String markdown = prompt + "\n![](" + dataUri + ")";

        // 5) Send to GPT
        OpenAIClient client = OpenAIOkHttpClient.builder()
                .apiKey(openaiApiKey)
                .build();

        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(ChatModel.GPT_4O_MINI)
                .addUserMessage(markdown)
                .temperature(0.7)
                .maxCompletionTokens(1000)
                .build();

        ChatCompletion completion = client.chat().completions().create(params);
        return extractContent(completion);
    }

    private String extractContent(ChatCompletion completion) {
        return completion.choices().stream()
                .map(choice -> choice.message().content())
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining("\n\n"));
    }

    private String getLanguageName(Language language) {
        return switch (language) {
            case ENGLISH -> "English";
            case KAZAKH   -> "Kazakh";
            case RUSSIAN  -> "Russian";
        };
    }

    private boolean isImageFile(String key) {
        if (key == null || !key.contains(".")) return false;
        String ext = key.substring(key.lastIndexOf('.') + 1).toLowerCase();
        return List.of("png","jpg","jpeg","gif","webp").contains(ext);
    }
}
