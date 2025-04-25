package com.example.newmoodle.service;

import com.example.newmoodle.dto.AssignmentSimpleDto;
import com.example.newmoodle.dto.SubmissionDto;
import com.example.newmoodle.dto.UserSimpleDto;
import com.example.newmoodle.model.*;
import com.example.newmoodle.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final AssignmentService assignmentService;
    private final SectionService sectionService;
    private final FileService fileService;

    @Transactional
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
                .grade(null)
                .build();
        return submissionRepository.save(submission);
    }

    public Submission getSubmissionById(Long id) {
        return submissionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Submission with id " + id + " not found"));
    }

    public List<SubmissionDto> getSubmissionsByAssignmentId(Long assignmentId) {
        assignmentService.getAssignmentById(assignmentId);

        List<Submission> submissions = submissionRepository.findByAssignmentId(assignmentId);

        List<SubmissionDto> submissionDtos = submissions.stream()
                .map(this::mapToSubmissionDto)
                .collect(Collectors.toList());

        // 4. Возвращаем список DTO
        return submissionDtos;
    }

    public void updateSubmission(Submission submission) {
        submissionRepository.save(submission);
    }

    @Transactional
    public SubmissionDto gradeSubmission(Long submissionId, Integer grade, User teacher) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("Submission with id " + submissionId + " not found"));
        Assignment assignment = submission.getAssignment();


        if (!assignment.getTeacher().equals(teacher)) {
            throw new AccessDeniedException("User is not the teacher of this assignment and cannot grade the submission.");
        }

        if (grade != null && (grade < 0 || grade > 100)) { // Пример диапазона 0-100
            throw new IllegalArgumentException("Grade must be between 0 and 100 (or null).");
        }

        submission.setGrade(grade);
        submission.setStatus(SubmissionStatus.GRADED);

        Submission updatedSubmission = submissionRepository.save(submission);
        return mapToSubmissionDto(updatedSubmission);
    }
    @Transactional
    public void deleteSubmission(Long submissionId, User user) throws IOException {
        Submission submission = getSubmissionById(submissionId);

        if (submission.getStudent().equals(user)) {
            fileService.deleteFile(submission.getFileUrl());
            submissionRepository.delete(submission);
        } else {
            throw new AccessDeniedException("User is not authorized to delete this submission.");
        }
    }

    private UserSimpleDto mapToUserSimpleDto(User user) {
        if (user == null) return null;
        return UserSimpleDto.builder()
                .id(user.getId())
                // Убедитесь, что у User есть метод getFullName() или соответствующие поля
                .fullName(user.getFullName()) // или user.getFirstName() + " " + user.getLastName()
                .email(user.getEmail())
                .build();
    }

    private AssignmentSimpleDto mapToAssignmentSimpleDto(Assignment assignment) {
        if (assignment == null) return null;
        return AssignmentSimpleDto.builder()
                .id(assignment.getId())
                .title(assignment.getTitle())
                .build();
    }

    public SubmissionDto mapToSubmissionDto(Submission submission) {
        if (submission == null) return null;
        return SubmissionDto.builder()
                .id(submission.getId())
                .fileUrl(submission.getFileUrl()) // Может быть, здесь нужен полный URL? fileService.getFileUrl(submission.getFileUrl())
                .status(submission.getStatus())
                .grade(submission.getGrade())
                .student(mapToUserSimpleDto(submission.getStudent()))
                .assignment(mapToAssignmentSimpleDto(submission.getAssignment()))
                .build();
    }
}