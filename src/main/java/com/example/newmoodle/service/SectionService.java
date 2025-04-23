package com.example.newmoodle.service;


import com.example.newmoodle.model.Role;
import com.example.newmoodle.model.Section;
import com.example.newmoodle.model.Subject;
import com.example.newmoodle.model.User;
import com.example.newmoodle.model.request.CreateSection;
import com.example.newmoodle.model.request.SectionDto;
import com.example.newmoodle.model.request.UserSummaryDto;
import com.example.newmoodle.repository.SectionRepository;
import com.example.newmoodle.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private UserSummaryDto mapUserToSummaryDTO(User user) {
        if (user == null) {
            return null;
        }
        return UserSummaryDto.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .build();
    }

    private SectionDto mapSectionToDTO(Section section) {
        if (section == null) {
            return null;
        }

        Set<UserSummaryDto> studentSummaries = (section.getStudents() == null)
                ? new HashSet<>()
                : section.getStudents().stream()
                .map(this::mapUserToSummaryDTO)
                .collect(Collectors.toSet());

        return SectionDto.builder()
                .id(section.getId())
                .name(section.getName())
                .subjectId((section.getSubject() != null) ? section.getSubject().getId() : null)
                .teacher(mapUserToSummaryDTO(section.getTeacher()))
                .students(studentSummaries)
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


}