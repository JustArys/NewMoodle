package com.example.newmoodle.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
// Optional: import java.time.LocalDateTime; if you add timestamps

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedbackDto {
    private Long id; // Assuming Feedback entity has an ID
    private String aiGenerated;
    private SubmissionSimpleDto submission; // Nested DTO
    private UserSimpleDto teacher;      // Nested DTO (using updated UserSimpleDto)
    // Optional: Add other relevant fields from Feedback entity
    // private LocalDateTime createdAt;
}