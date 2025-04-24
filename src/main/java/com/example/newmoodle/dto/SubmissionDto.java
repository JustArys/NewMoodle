package com.example.newmoodle.dto;

import com.example.newmoodle.model.SubmissionStatus; // Убедитесь, что импорт корректен
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmissionDto {
    private Long id;
    private String fileUrl;
    private SubmissionStatus status;
    private Integer grade; // Добавляем оценку
    private UserSimpleDto student; // Используем UserSimpleDto
    private AssignmentSimpleDto assignment; // Используем AssignmentSimpleDto
}