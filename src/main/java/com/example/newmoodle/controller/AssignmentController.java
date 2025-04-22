package com.example.newmoodle.controller;
import com.example.newmoodle.model.Assignment;
import com.example.newmoodle.model.User;
import com.example.newmoodle.model.request.AssignmentDto;
import com.example.newmoodle.service.AssignmentService;
import com.example.newmoodle.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;

@Validated
@RestController
@RequestMapping("/api/v1/assignment")
@RequiredArgsConstructor
public class AssignmentController {

    private final AssignmentService assignmentService;
    private final UserService userService;


    @PostMapping(value = "/{sectionId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createAssignment(
            @PathVariable Long sectionId,
            @RequestParam(required = false) MultipartFile file, // required = false позволяет не передавать файл
            @RequestParam String title,
            @RequestParam String description,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dueDate
    ) {
        try {
            AssignmentDto assignmentDto = AssignmentDto.builder()
                    .title(title)
                    .description(description)
                    .dueDate(dueDate)
                    .file(file)
                    .build();

            User currentUser = userService.getAuthenticatedUser(); // Получаем текущего пользователя
            Assignment createdAssignment = assignmentService.createAssignment(assignmentDto, currentUser, sectionId);
            return ResponseEntity.ok(createdAssignment);
        } catch (IOException e) {
            // Обработка ошибки загрузки файла
            System.err.println("Controller caught IOException: " + e.getMessage());
            // Вернуть клиенту осмысленную ошибку
            return ResponseEntity.status(500).body("Error processing file: " + e.getMessage());
        } catch (Exception e) {
            // Обработка других ошибок (например, секция не найдена, ошибка БД)
            System.err.println("Controller caught Exception: " + e.getMessage());
            // Вернуть клиенту осмысленную ошибку
            return ResponseEntity.status(500).body("Error creating assignment: " + e.getMessage());
        }
    }
    @GetMapping("/{assignmentId}")
    public ResponseEntity<?> getAssignment(@PathVariable Long assignmentId){
        return ResponseEntity.ok(assignmentService.getAssignmentById(assignmentId));
    }

    @GetMapping("/section/{sectionId}")
    public ResponseEntity<?> getAssignments(@PathVariable Long sectionId){
        return ResponseEntity.ok(assignmentService.getAssignmentsBySectionId(sectionId));
    }
    @GetMapping("/{assignmentId}/download") // Новый эндпоинт для скачивания
    public ResponseEntity<Resource> downloadAssignmentFile(@PathVariable Long assignmentId) {
        try {
            Resource file = assignmentService.downloadFile(assignmentId);

            if (file == null) {
                return ResponseEntity.notFound().build();
            }

            String contentType = "application/octet-stream";
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFilename() + "\"")
                    .body(file);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
