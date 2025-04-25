package com.example.newmoodle.controller;

import com.example.newmoodle.dto.SubmissionDto;
import com.example.newmoodle.model.Submission;
import com.example.newmoodle.model.User;
import com.example.newmoodle.service.SubmissionService;
import com.example.newmoodle.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/submission")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;
    private final UserService userService;

    @GetMapping("/{id}")
    public ResponseEntity<?> getSubmission(@PathVariable Long id) {
        return ResponseEntity.ok(submissionService.mapToSubmissionDto(submissionService.getSubmissionById(id)));
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

    @GetMapping("/assignments/{assignmentId}/submissions")
    public ResponseEntity<?> getSubmissionsForAssignment(
            @PathVariable Long assignmentId) {
        try {
            return ResponseEntity.ok(submissionService.getSubmissionsByAssignmentId(assignmentId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/submissions/{submissionId}/grade")
    public ResponseEntity<?> gradeSubmission(
            @PathVariable Long submissionId,
            @Valid @RequestBody Integer gradeRequest
    ) {
        User currentUser = userService.getAuthenticatedUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            SubmissionDto gradedSubmission = submissionService.gradeSubmission(submissionId, gradeRequest, currentUser);
            return ResponseEntity.ok(gradedSubmission);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(null);
        }
    }


}
