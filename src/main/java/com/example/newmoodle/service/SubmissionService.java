package com.example.newmoodle.service;

import com.example.newmoodle.model.*;
import com.example.newmoodle.repository.AssignmentRepository;
import com.example.newmoodle.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final AssignmentService assignmentService;
    private final SectionService sectionService;
    private final FileService fileService;

    public Submission createSubmission(User user, MultipartFile file, Long assignmentId, Long sectionId) throws IOException {
        Section section = sectionService.getSectionById(sectionId);
        if(!section.getStudents().contains(user)) {
            throw new IllegalArgumentException("User is not a Student");
        }
        Assignment assignment = assignmentService.getAssignmentById(assignmentId);
        String filename = fileService.uploadFile(file);
        var submission = Submission.builder()
                .assignment(assignment)
                .fileUrl(filename)
                .student(user)
                .status(SubmissionStatus.PENDING)
                .build();
        return submissionRepository.save(submission);
    }

    public Submission getSubmissionById(Long id) {
        return submissionRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Submission not found"));
    }

    public Submission updateSubmission(Submission submission) {
        return submissionRepository.save(submission);
    }
    public void deleteSubmission(Long submissionId, User user) throws IOException {
        Submission submission = getSubmissionById(submissionId);

        if(submission.getStudent().equals(user)) {
            fileService.deleteFile(submission.getFileUrl());
            submissionRepository.delete(submission);
        }
        else { throw new IllegalArgumentException("User is not a Student"); }
    }
}
