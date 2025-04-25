package com.example.newmoodle.service;

import com.example.newmoodle.dto.AssignmentsDto;
import com.example.newmoodle.dto.SubjectDto;
import com.example.newmoodle.model.*;
import com.example.newmoodle.model.request.AssignmentDto;
import com.example.newmoodle.model.request.SectionDto;
import com.example.newmoodle.model.request.UserSummaryDto;
import com.example.newmoodle.model.response.ApiError;
import com.example.newmoodle.repository.AssignmentRepository;
import com.example.newmoodle.repository.SectionRepository;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final SectionService sectionService;
    private final FileService fileService;
    private final UserService userService;
    private final SectionRepository sectionRepository;

    public Assignment createAssignment(AssignmentDto assignment, User teacher, Long sectionId) throws IOException {
        String filePath = null;

        try {
            if (assignment.getFile() != null && !assignment.getFile().isEmpty()) {
                try {
                    filePath = fileService.uploadFile(assignment.getFile());
                } catch (IOException e) {
                    System.err.println("Error saving file during assignment creation: " + e.getMessage());
                    // Пробрасываем исключение дальше
                    throw new IOException("Failed to save attached file", e);
                }
            }

            // Создаем сущность Assignment
            var assignmentEntity = Assignment.builder()
                    .title(assignment.getTitle())
                    .dueDate(assignment.getDueDate())
                    .description(assignment.getDescription())
                    .teacher(teacher)
                    .filePath(filePath) // Устанавливаем filePath (может быть null)
                    .section(sectionService.getSectionById(sectionId)) // Убедитесь, что getSectionById обрабатывает случай, когда секция не найдена
                    .build();

            // Сохраняем сущность в репозиторий
            return assignmentRepository.save(assignmentEntity);

        } catch (IOException e) {
            // Этот блок поймает IOException, выброшенное при ошибке загрузки файла
            throw e; // Просто пробрасываем дальше, чтобы контроллер обработал
        } catch (Exception e) {
            // Логируем другие возможные ошибки (например, ошибка поиска секции, ошибка сохранения в БД)
            System.err.println("Error creating assignment: " + e.getMessage());
            // Пробрасываем как RuntimeException или создайте свое кастомное исключение
            throw new RuntimeException("Failed to create assignment", e);
        }
    }
    public Resource downloadFile(Long assignmentId) {
        Assignment assignment = getAssignmentById(assignmentId);
        return fileService.loadFileAsResource(assignment.getFilePath());
    }

    public Assignment getAssignmentById(Long id) {
        return assignmentRepository.findById(id).orElseThrow(()
                -> new NoSuchElementException(String.format("Assignment with id '%d' not found", id)));
    }
    public List<Assignment> getAssignmentsBySectionId(Long sectionId) {
        Section section = sectionService.getSectionById(sectionId);
        return assignmentRepository.findBySection(section);
    }

    @Transactional
    public void deleteAssignment(Long id) {
        Assignment assignment = getAssignmentById(id); // Находим или получаем ошибку

        if (assignment.getFilePath() != null && !assignment.getFilePath().isEmpty()) {
            fileService.deleteFile(assignment.getFilePath());
        }
        assignmentRepository.delete(assignment);
    }
    @Transactional(readOnly = true) // Важно для ленивой загрузки внутри мапперов
    public List<AssignmentsDto> getAssignmentsForCurrentUserStudent() {
        User currentUser = userService.getAuthenticatedUser();

        if (currentUser == null || !Role.STUDENT.equals(currentUser.getRole())) {
            System.out.println("User is not authenticated or not a student. Returning empty assignment list.");
            return Collections.emptyList();
        }

        // Используем ваш метод или JPQL запрос
        List<Section> studentSections = sectionRepository.findByStudentsContains(currentUser);
        // List<Section> studentSections = sectionRepository.findByStudentsContains(currentUser);

        if (studentSections.isEmpty()) {
            return Collections.emptyList();
        }

        List<Assignment> assignments = assignmentRepository.findBySectionInOrderByDueDateAsc(studentSections);

        // Преобразуем в DTO, используя обновленные мапперы
        return assignments.stream()
                .map(this::mapToAssignmentDto)
                .collect(Collectors.toList());
    }
    public AssignmentsDto mapToAssignmentDto(Assignment assignment) {
        if (assignment == null) return null;
        return AssignmentsDto.builder()
                .id(assignment.getId())
                .title(assignment.getTitle())
                .description(assignment.getDescription())
                .dueDate(assignment.getDueDate())
                .filePath(assignment.getFilePath()) // Может требовать обработки FileService
                .section(mapToSectionDto(assignment.getSection())) // Используем маппер для полного SectionDto
                .teacher(mapToUserSummaryDto(assignment.getTeacher()))
                .build();
    }

    // Маппер для Section -> SectionDto (более сложный)
    private SectionDto mapToSectionDto(Section section) {
        if (section == null) return null;

        // Маппинг списка студентов (требует загрузки коллекции section.getStudents())
        // ОСТОРОЖНО: Может вызвать N+1 запросы, если студенты не загружены заранее!
        Set<UserSummaryDto> studentSummaries = section.getStudents().stream()
                .map(this::mapToUserSummaryDto)
                .collect(Collectors.toSet());

        return SectionDto.builder()
                .id(section.getId())
                .name(section.getName())
                .subject(mapToSubjectDto(section.getSubject())) // Требует загрузки section.getSubject()
                .teacher(mapToUserSummaryDto(section.getTeacher())) // Требует загрузки section.getTeacher()
                .students(studentSummaries) // Включаем список студентов
                .build();
    }

    // Маппер для User -> UserSummaryDto
    private UserSummaryDto mapToUserSummaryDto(User user) {
        if (user == null) return null;
        return UserSummaryDto.builder()
                .id(user.getId())
                .fullName(user.getFullName()) // Убедитесь, что метод getFullName() существует
                .build();
    }

    // Маппер для Subject -> SubjectDto
    private SubjectDto mapToSubjectDto(Subject subject) {
        if (subject == null) return null;
        // Убедитесь, что у Subject есть getId() и getName()
        return new SubjectDto(subject.getId(), subject.getName());
    }

}
