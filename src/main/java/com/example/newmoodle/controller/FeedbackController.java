package com.example.newmoodle.controller;

import com.example.newmoodle.dto.FeedbackDto; // Import DTO
import com.example.newmoodle.model.Language;
import com.example.newmoodle.model.User;
import com.example.newmoodle.service.FeedbackService;
import com.example.newmoodle.service.UserService; // Keep for getting authenticated user
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException; // For specific exception handling
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private static final Logger logger = LoggerFactory.getLogger(FeedbackController.class);
    private final FeedbackService feedbackService;
    private final UserService userService; // To get the currently logged-in teacher

    @PostMapping("/{submissionId}/")
    public ResponseEntity<?> generateFeedback(@PathVariable Long submissionId, @RequestParam Language language) {
        try {
            User currentTeacher = userService.getAuthenticatedUser();
            // Optional: Add role check if needed
            // if (currentTeacher == null || currentTeacher.getRole() != Role.TEACHER) {
            //    return ResponseEntity.status(HttpStatus.FORBIDDEN).body("User is not authorized or not found.");
            // }

            FeedbackDto feedbackDto = feedbackService.generateFeedbackAndGetDto(submissionId, currentTeacher, language);
            return ResponseEntity.ok(feedbackDto);

        } catch (UsernameNotFoundException e) {
            // Catch specific exception from userService if user is not found
            logger.warn("Authentication error during feedback generation: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Authentication failed: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            // Handle known "bad request" errors like submission not found, no file, etc.
            logger.warn("Bad request generating feedback for submission {}: {}", submissionId, e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            // Catch all other unexpected errors
            logger.error("Internal server error generating feedback for submission id {}", submissionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An internal error occurred.");
        }
    }
}