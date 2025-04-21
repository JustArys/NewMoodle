package com.example.newmoodle.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSimpleDto {
    private Long id;
    private String fullName; // Matches your User entity
    private String email;    // Matches your User entity
}