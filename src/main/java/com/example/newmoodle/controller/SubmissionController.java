package com.example.newmoodle.controller;

import com.example.newmoodle.model.Submission;
import com.example.newmoodle.service.SubmissionService;
import com.example.newmoodle.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/submission")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;
    private final UserService userService;

    @GetMapping("/{id}")
    public ResponseEntity<?> getSubmission(@PathVariable Long id) {
        return ResponseEntity.ok(submissionService.getSubmissionById(id));
    }

    @PostMapping(path = "/{sectionId}/{assignmentId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> submitAssignment(@PathVariable Long sectionId, @PathVariable Long assignmentId, @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(submissionService.createSubmission(userService.getAuthenticatedUser(), file, assignmentId, sectionId ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteSubmission(@PathVariable Long id) throws IOException {
        submissionService.deleteSubmission(id, userService.getAuthenticatedUser());
        return ResponseEntity.ok("Deleted successfully");
    }
}
