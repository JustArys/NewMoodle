package com.example.newmoodle.dto;

import com.example.newmoodle.model.SubmissionStatus; // Make sure SubmissionStatus import is correct
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmissionSimpleDto {
    private Long id;
    private String fileUrl; // Represents the S3/R2 key
    private SubmissionStatus status;
    private UserSimpleDto student; // Nested DTO using the updated UserSimpleDto
    // Optional: Add assignment info if needed
    // private Long assignmentId;
    // private String assignmentTitle;
}