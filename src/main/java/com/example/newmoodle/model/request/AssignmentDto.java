package com.example.newmoodle.model.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AssignmentDto {
    private String title;
    private String description;
    private LocalDateTime dueDate;
    private MultipartFile file;
}
