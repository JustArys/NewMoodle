package com.example.newmoodle.dto; // Или другой подходящий пакет для DTO

import com.example.newmoodle.model.request.SectionDto;
import com.example.newmoodle.model.request.UserSummaryDto; // Используем ваш UserSummaryDto
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentsDto {

    private Long id;
    private String title;
    private String description;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime dueDate;

    private String filePath; // Путь к файлу или URL (возможно,需要 FileService для генерации URL)
    private SectionDto section; // Краткая информация о секции
    private UserSummaryDto teacher; // Информация о преподавателе
}