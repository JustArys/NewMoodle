package com.example.newmoodle.dto;

import com.example.newmoodle.model.Subject;
import com.example.newmoodle.model.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SectionDto {
    private Long sectionId;
    private User teacher;
    private Set<User> students;
    private SubjectDto subject;
    private String name;
}
