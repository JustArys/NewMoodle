package com.example.newmoodle.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Builder
@Data
@Table
@AllArgsConstructor
@NoArgsConstructor
public class Subject {
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Id
    @Column(name = "subject_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @OneToMany(cascade = CascadeType.ALL) // remove mappedBy
    @JoinColumn(name = "subject_id") // add join column
    private List<Section> section = new ArrayList<>();

}
