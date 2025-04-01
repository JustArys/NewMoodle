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
        try{String file = fileService.saveFile(assignment.getFile());
        var assignment1 = Assignment.builder()
                .title(assignment.getTitle())
                .dueDate(assignment.getDueDate())
                .description(assignment.getDescription())
                .teacher(teacher)
                .filePath(file)
                .section(sectionService.getSectionById(sectionId))
                .build();
        return assignmentRepository.save(assignment1);
        } catch (IOException e) {
            // Логируем ошибку
            System.err.println("Error saving file: " + e.getMessage());
            // Пробрасываем исключение, чтобы контроллер мог его обработать
            throw new IOException("Failed to save file", e);
        } catch (Exception e) {
            // Логируем ошибку
            System.err.println("Error creating assignment: " + e.getMessage());
            // Пробрасываем исключение, чтобы контроллер мог его обработать
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
