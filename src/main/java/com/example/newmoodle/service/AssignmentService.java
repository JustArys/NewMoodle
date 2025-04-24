package com.example.newmoodle.service;

import com.example.newmoodle.model.Assignment;
import com.example.newmoodle.model.Role;
import com.example.newmoodle.model.Section;
import com.example.newmoodle.model.User;
import com.example.newmoodle.model.request.AssignmentDto;
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
    }
    @Transactional(readOnly = true)
    public List<Assignment> getAssignmentsForCurrentUserStudent() {
        User currentUser = userService.getAuthenticatedUser();

        // Убедимся, что пользователь - студент
        if (!currentUser.getRole().equals(Role.STUDENT)) {
            // Можно выбросить исключение или вернуть пустой список
            // throw new IllegalStateException("User is not a student");
            System.out.println("User " + currentUser.getEmail() + " is not a student. Returning empty assignment list."); // Логирование
            return Collections.emptyList(); // Возвращаем пустой список, если не студент
        }

        // 1. Найти все секции, где пользователь является студентом
        List<Section> studentSections = sectionRepository.findByStudentsContains(currentUser);

        // 2. Если студент не состоит ни в одной секции, вернуть пустой список
        if (studentSections.isEmpty()) {
            return Collections.emptyList();
        }

        // 3. Найти все задания, связанные с этими секциями
        List<Assignment> assignments = assignmentRepository.findBySectionIn(studentSections);

        return assignments;
    }
}
