package com.example.newmoodle.controller;
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
            @RequestParam MultipartFile file,
            @RequestParam String title,
            @RequestParam String description,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dueDate
    ) throws IOException {
        AssignmentDto assignmentDto = AssignmentDto.builder()
                .title(title)
                .description(description)
                .dueDate(dueDate)
                .file(file)
                .build();
        return ResponseEntity.ok(assignmentService.createAssignment(assignmentDto, userService.getAuthenticatedUser(), sectionId));
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

            String contentType = "application/octet-stream"; // По умолчанию
            // Можно добавить логику для определения Content-Type на основе расширения файла

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFilename() + "\"")
                    .body(file);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
