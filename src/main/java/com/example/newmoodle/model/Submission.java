package com.example.newmoodle.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Table(name = "submission")
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class Submission {
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Id
    @Column(name = "subject_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;

    @JsonIgnore
    @OneToOne(
            mappedBy = "submission", // Поле 'submission' в сущности Feedback
            cascade = CascadeType.REMOVE, // <-- ДОБАВИТЬ
            orphanRemoval = true          // <-- ДОБАВИТЬ
    )
    private Feedback feedbacks;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(name = "file_url", nullable = false)
    private String fileUrl;

    @Enumerated(EnumType.STRING)
    @Column()
    private SubmissionStatus status;

    private Integer grade;
}
