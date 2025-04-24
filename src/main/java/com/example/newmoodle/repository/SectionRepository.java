package com.example.newmoodle.repository;

import com.example.newmoodle.model.Section;
import com.example.newmoodle.model.Subject;
import com.example.newmoodle.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface
SectionRepository extends JpaRepository<Section, Long> {
    List<Section> findByStudentsContains(User student);

    boolean existsBySubject(Subject subject);
}
