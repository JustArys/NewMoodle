package com.example.newmoodle.repository;

import com.example.newmoodle.model.Assignment;
import com.example.newmoodle.model.Section;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {
    List<Assignment> findBySection(Section section);

}
