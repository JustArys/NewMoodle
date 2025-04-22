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
import org.springframework.util.StringUtils; // Import StringUtils

import java.io.IOException;
import java.util.Base64;
// No longer need List here as FileService handles it
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private static final Logger logger = LoggerFactory.getLogger(FeedbackService.class);
    private final SubmissionService submissionService;
    private final FeedbackRepository feedbackRepository;
    private final FileService fileService; // Assume FileService is autowired
    private final FeedbackMapper feedbackMapper;

    @Value("${openai.api.key}")
    private String openaiApiKey;

    // Updated prompt template to include Assignment File Content
    private static final String PROMPT_TEMPLATE = """
            You are an experienced educator and subject matter expert, professor. Your task is to analyze a student’s submission, provide detailed and constructive feedback, and assign an objective grade based on the provided criteria.

            Here is the input data:

            Assignment Description:
            %s

            Assignment File Content:
            %s

            Student Submission:
            %s

            Response Format:

            Feedback:
            Give detailed, constructive feedback. What was done well? What needs improvement? Use specific examples from the student’s work.

            Grade:
            Assign a score on a scale from 0 to 100. Explain how you arrived at this score based on the criteria.

            Recommendations:
            If the submission needs improvement, suggest specific steps to help the student do better.

            Important:
            — Be objective and supportive.
            — Avoid vague comments; be as specific as possible.
            — Always respond in the same language as the assignment was written in (%s).
            """;


    @Transactional
    public FeedbackDto generateFeedbackAndGetDto(Long submissionId, User teacher, Language language) throws Exception {
        Feedback feedbackEntity = generateFeedbackInternal(submissionId, teacher, language);
        return feedbackMapper.feedbackToFeedbackDto(feedbackEntity);
    }

    private Feedback generateFeedbackInternal(Long submissionId, User teacher, Language language) throws Exception {
        Submission submission = submissionService.getSubmissionById(submissionId);
        Assignment assignment = submission.getAssignment();

        if (assignment == null) {
            logger.error("Submission ID {} is not linked to a valid Assignment.", submissionId);
            throw new IllegalStateException("Submission must be linked to an assignment.");
        }

        // --- Process Assignment File ---
        String assignmentFileContent = getAssignmentFileContent(assignment);
        // --- Process Assignment File End ---

        String submissionFileKey = submission.getFileUrl();
        if (!StringUtils.hasText(submissionFileKey)) { // Use StringUtils for better null/empty check
            logger.error("Submission ID {} has no associated file.", submissionId);
            throw new IllegalArgumentException("Submission has no file associated.");
        }

        String generatedContent;
        // Use FileService method to check submission file type
        if (fileService.isImageFile(submissionFileKey)) {
            logger.info("Student submission is an image file (key: {}) – embedding as Base64.", submissionFileKey);
            generatedContent = callOpenAIWithSubmissionImage(assignment, assignmentFileContent, submissionFileKey, language);
        } else if (fileService.isTextExtractableFile(submissionFileKey)) {
            logger.info("Student submission is a text file (key: {}) – extracting text.", submissionFileKey);
            try {
                String studentText = fileService.extractText(submissionFileKey);
                generatedContent = callOpenAIWithSubmissionText(assignment, assignmentFileContent, studentText, language);
            } catch (Exception e) {
                logger.error("Failed to extract text from submission file key {}: {}", submissionFileKey, e.getMessage(), e);
                // Decide how to handle: throw error, or generate feedback without submission content?
                // Option: Generate feedback noting the failure
                // generatedContent = callOpenAIWithSubmissionText(assignment, assignmentFileContent, "[Error extracting text from student submission]", language);
                // Option: Throw an exception
                throw new Exception("Failed to process student submission file: " + e.getMessage(), e);
            }
        } else {
            logger.warn("Submission file key {} is of an unsupported type for feedback generation.", submissionFileKey);
            throw new IllegalArgumentException("Unsupported file type for student submission: " + fileService.getExtension(submissionFileKey));
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

    /**
     * Retrieves and processes the content of the file attached to the assignment.
     * Returns extracted text or a placeholder message.
     */
    private String getAssignmentFileContent(Assignment assignment) {
        String assignmentFilePath = assignment.getFilePath();
        if (!StringUtils.hasText(assignmentFilePath)) {
            return "[No assignment file attached]";
        }

        logger.info("Processing assignment file: {}", assignmentFilePath);
        try {
            if (fileService.isTextExtractableFile(assignmentFilePath)) {
                // Extract text from assignment file
                return fileService.extractText(assignmentFilePath);
            } else if (fileService.isImageFile(assignmentFilePath)) {
                // If assignment file is an image, return placeholder (won't embed this one)
                logger.warn("Assignment file {} is an image. Including placeholder in prompt.", assignmentFilePath);
                return String.format("[Assignment file is an image: %s]", assignmentFilePath.substring(assignmentFilePath.lastIndexOf('/') + 1)); // Show filename
            } else {
                logger.warn("Assignment file {} is of an unsupported type. Including placeholder.", assignmentFilePath);
                return String.format("[Assignment file of unsupported type: %s]", assignmentFilePath.substring(assignmentFilePath.lastIndexOf('/') + 1));
            }
        } catch (Exception e) {
            logger.error("Failed to process assignment file key {}: {}. Including error message in prompt.", assignmentFilePath, e.getMessage(), e);
            return String.format("[Error processing assignment file %s: %s]", assignmentFilePath.substring(assignmentFilePath.lastIndexOf('/') + 1), e.getMessage());
        }
    }


    // Renamed for clarity: handles text-based submissions
    private String callOpenAIWithSubmissionText(Assignment assignment, String assignmentFileContent, String studentText, Language language) {
        OpenAIClient client = OpenAIOkHttpClient.builder()
                .apiKey(openaiApiKey)
                .build();

        String assignmentDescription = getSanitizedString(assignment.getDescription(), "[No assignment description provided]");
        String processedAssignmentContent = getSanitizedString(assignmentFileContent, "[No assignment file content]"); // Already processed
        String submissionContent = getSanitizedString(studentText, "[No text extracted from student submission]");
        String languageName = getLanguageName(language);

        // Format the prompt using the updated template
        String prompt = String.format(PROMPT_TEMPLATE,
                assignmentDescription,
                processedAssignmentContent, // Add assignment file content here
                submissionContent,
                languageName
        );

        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(ChatModel.GPT_4O_MINI) // Model for text
                .addUserMessage(prompt)
                .temperature(0.7)
                .maxCompletionTokens(2000) // Increased further for more context
                .build();

        logger.debug("Sending text prompt to OpenAI for assignment '{}', language '{}'", assignment.getTitle(), languageName);
        ChatCompletion completion = client.chat().completions().create(params);
        return extractContent(completion);
    }

    // Renamed for clarity: handles image-based submissions
    private String callOpenAIWithSubmissionImage(Assignment assignment, String assignmentFileContent, String submissionFileKey, Language language) throws IOException {
        // 1) Download student submission image bytes
        byte[] imageBytes = fileService.downloadFileAsBytes(submissionFileKey);
        String mimeType = fileService.getMimeType(submissionFileKey);
        String b64 = Base64.getEncoder().encodeToString(imageBytes);
        String dataUri = "data:" + mimeType + ";base64," + b64;

        // 2) Prepare the text part of the prompt
        String assignmentDescription = getSanitizedString(assignment.getDescription(), "[No assignment description provided]");
        String processedAssignmentContent = getSanitizedString(assignmentFileContent, "[No assignment file content]"); // Already processed
        String languageName = getLanguageName(language);

        // Format the text part using the template, with placeholder for student image
        String textPromptPart = String.format(PROMPT_TEMPLATE,
                assignmentDescription,
                processedAssignmentContent, // Add assignment file content here
                "[Student submission is provided as an image below]",
                languageName
        );

        // 3) Construct the final prompt including the student image markdown
        String finalPromptWithImage = textPromptPart + "\n\n![](" + dataUri + ")";

        // 4) Send to GPT Vision model
        OpenAIClient client = OpenAIOkHttpClient.builder()
                .apiKey(openaiApiKey)
                .build();

        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(ChatModel.GPT_4_1_MINI)
                .addUserMessage(finalPromptWithImage)
                .temperature(0.7)
                .maxCompletionTokens(2000)
                .build();

        logger.debug("Sending image prompt to OpenAI for assignment '{}', language '{}'", assignment.getTitle(), languageName);
        ChatCompletion completion = client.chat().completions().create(params);
        return extractContent(completion);
    }

    // Helper to sanitize potentially null/blank strings for the prompt
    private String getSanitizedString(String input, String fallback) {
        return StringUtils.hasText(input) ? input : fallback;
    }


    private String extractContent(ChatCompletion completion) {
        // ... (implementation unchanged)
        if (completion == null || completion.choices() == null || completion.choices().isEmpty()) {
            logger.warn("Received empty or null completion from OpenAI.");
            return "[Error: No content received from AI]";
        }
        return completion.choices().stream()
                .map(choice -> choice.message().content())
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining("\n\n"));
    }

    private String getLanguageName(Language language) {
        // ... (implementation unchanged)
        return switch (language) {
            case ENGLISH -> "English";
            case KAZAKH   -> "Kazakh";
            case RUSSIAN  -> "Russian";
        };
    }

    // No longer need isImageFile here, using FileService's public method
}