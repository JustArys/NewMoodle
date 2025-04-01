package com.example.newmoodle.controller;

import com.example.newmoodle.model.Feedback;
import com.example.newmoodle.model.Language;
import com.example.newmoodle.model.User;
import com.example.newmoodle.service.FeedbackService;
import com.example.newmoodle.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;
    private final UserService userService;

    @PostMapping("/{submissionId}/")
    public ResponseEntity<?> generateFeedback(@PathVariable Long submissionId, @RequestParam Language language) throws Exception {
        return ResponseEntity.ok(feedbackService.generateFeedback(submissionId, userService.getAuthenticatedUser(), language));
    }
}
