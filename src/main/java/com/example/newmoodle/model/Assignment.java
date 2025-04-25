package com.example.newmoodle.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "assignment")
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class Assignment {
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Id
    @Column(name = "assignment_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "due_date", nullable = false)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime dueDate;

    @ManyToOne
    @JoinColumn(name = "section_id", nullable = false)
    private Section section;

    @ManyToOne
    @JoinColumn(name = "teacher_id", nullable = false)
    private User teacher; // Creator of the assignment


    @Column(name = "file_path")
    private String filePath;

    @JsonIgnore
    @OneToMany(
            mappedBy = "assignment", // Указывает на поле 'assignment' в сущности Submission
            cascade = CascadeType.REMOVE, // <-- ДОБАВИТЬ ЭТО
            orphanRemoval = true          // <-- ДОБАВИТЬ ЭТО (Важно для согласованности при удалении из коллекции)
            // fetch = FetchType.LAZY // Обычно LAZY по умолчанию для OneToMany
    )
    private List<Submission> submissions;


}
