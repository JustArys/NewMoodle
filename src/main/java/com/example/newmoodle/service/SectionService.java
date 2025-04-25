package com.example.newmoodle.service;


import com.example.newmoodle.dto.SubjectDto;
import com.example.newmoodle.model.*;
import com.example.newmoodle.model.request.CreateSection;
import com.example.newmoodle.model.request.SectionDto;
import com.example.newmoodle.model.request.UserSummaryDto;
import com.example.newmoodle.repository.AssignmentRepository;
import com.example.newmoodle.repository.SectionRepository;
import com.example.newmoodle.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SectionService {

    private final SectionRepository sectionRepository;
    private final UserService userService;
    private final SubjectRepository subjectRepository;
    private final AssignmentRepository assignmentRepository;
    private final FileService fileService;


    private UserSummaryDto mapUserToSummaryDTO(User user) {
        if (user == null) {
            return null;
        }
        return UserSummaryDto.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .build();
    }

    private SubjectDto mapSubjectToDTO(Subject subject) {
        if (subject == null) {
            return null;
        }
        // Assuming SubjectDto has a constructor like this (or use builder/setters)
        // Also assuming SubjectDto fields are subjectId and name
        return new SubjectDto(subject.getId(), subject.getName());
    }

    private SectionDto mapSectionToDTO(Section section) {
        if (section == null) {
            return null;
        }

        // Map students with null check for the collection itself
        Set<UserSummaryDto> studentSummaries = (section.getStudents() == null)
                ? new HashSet<>() // Return empty set if student collection is null
                : section.getStudents().stream()
                .map(this::mapUserToSummaryDTO) // mapUserToSummaryDTO handles null users within the stream
                .collect(Collectors.toSet());

        // Map teacher (mapUserToSummaryDTO already handles null)
        UserSummaryDto teacherSummary = mapUserToSummaryDTO(section.getTeacher());

        // Map subject with null check before creating SubjectDto
        SubjectDto subjectDto = null; // Initialize as null
        Subject subject = section.getSubject();
        if (subject != null) {
            // Only create SubjectDto if the subject entity is not null
            subjectDto = mapSubjectToDTO(subject); // Use the helper method which also has null check
            // Or directly: subjectDto = new SubjectDto(subject.getId(), subject.getName());
        }

        // Build and return the SectionDto using the builder
        return SectionDto.builder()
                .id(section.getId()) // Assuming field name is sectionId
                .name(section.getName())
                .subject(subjectDto) // Use the potentially null subjectDto
                .teacher(teacherSummary) // Use the potentially null teacherSummary
                .students(studentSummaries) // Use the (never null) set of student summaries
                .build();
    }

    @Transactional
    public SectionDto createSection(CreateSection request) {
        Subject subject = subjectRepository.findById(request.getSubjectId())
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("Subject with id '%d' not found, cannot create section", request.getSubjectId())));

        Section newSection = Section.builder()
                .name(request.getName())
                .subject(subject)
                .students(new HashSet<>())
                .build();

        subject.addSection(newSection);
        Section savedSection = sectionRepository.save(newSection);
        return mapSectionToDTO(savedSection);
    }

    public SectionDto getSectionDTOById(Long id) {
        Section section = findSectionByIdInternal(id);
        return mapSectionToDTO(section);
    }

    public List<SectionDto> getAllSectionDTOs() {
        return sectionRepository.findAll()
                .stream()
                .map(this::mapSectionToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public SectionDto setTeacher(Long userId, Long sectionId) {
        Section section = findSectionByIdInternal(sectionId);
        User user = userService.findUserById(userId);

        if (user == null) {
            throw new IllegalArgumentException(String.format("User with id '%d' not found", userId));
        }
        if (!userService.checkRoles(userId, Role.TEACHER)) {
            throw new IllegalArgumentException("User is not a teacher");
        }

        section.setTeacher(user);
        Section updatedSection = sectionRepository.save(section);
        return mapSectionToDTO(updatedSection);
    }

    @Transactional
    public SectionDto setStudent(Long userId, Long sectionId) {
        Section section = findSectionByIdInternal(sectionId);
        User user = userService.findUserById(userId);

        if (user == null) {
            throw new IllegalArgumentException(String.format("User with id '%d' not found", userId));
        }
        if (!userService.checkRoles(userId, Role.STUDENT)) {
            throw new IllegalArgumentException("User is not a Student");
        }
        if (section.getStudents() == null) {
            section.setStudents(new HashSet<>());
        }

        section.getStudents().add(user);
        Section updatedSection = sectionRepository.save(section);
        return mapSectionToDTO(updatedSection);
    }

    private Section findSectionByIdInternal(Long id) {
        return sectionRepository.findById(id).orElseThrow(()
                -> new IllegalArgumentException(String.format("Section with id '%d' not found", id)));
    }

    public Section getSectionById(Long id) {
        return findSectionByIdInternal(id);
    }

    @Transactional(readOnly = true) // Используем readOnly, так как только читаем данные
    public List<SectionDto> getSectionsByStudent(User student) {
        if (student == null) {
            throw new IllegalArgumentException("Student cannot be null");
        }

        List<Section> sections = sectionRepository.findByStudentsContains(student);
        return sections.stream()
                .map(this::mapSectionToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteSection(Long id) {
        Section section = findSectionByIdInternal(id); // Находим секцию или получаем ошибку

        List<Assignment> assignmentsToDelete = assignmentRepository.findBySection(section);

        // Удаляем связанные задания и их файлы *напрямую*
        for (Assignment assignment : assignmentsToDelete) {
            assignmentRepository.delete(assignment);
            // 1. Удаляем файл задания (если есть)
            if (assignment.getFilePath() != null && !assignment.getFilePath().isEmpty()) {
                // Используем FileService, внедренный в SectionService
                fileService.deleteFile(assignment.getFilePath());
            }

            assignmentRepository.delete(assignment);
        }
        sectionRepository.delete(section);
    }

}