package com.example.newmoodle.service;

import com.example.newmoodle.model.Assignment;
import com.example.newmoodle.model.Section;
import com.example.newmoodle.model.User;
import com.example.newmoodle.model.request.AssignmentDto;
import com.example.newmoodle.model.response.ApiError;
import com.example.newmoodle.repository.AssignmentRepository;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final SectionService sectionService;
    private final FileService fileService;

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

    public void deleteAssignment(Long id){
        assignmentRepository.deleteById(id);
    }
    
}
