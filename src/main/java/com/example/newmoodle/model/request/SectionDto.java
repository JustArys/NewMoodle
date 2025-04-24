package com.example.newmoodle.model.request;


import com.example.newmoodle.dto.SubjectDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SectionDto {

    private Long id;
    private String name;
    private SubjectDto subject;
    private UserSummaryDto teacher;
    private Set<UserSummaryDto> students;

}
