package com.example.newmoodle.service;

import com.example.newmoodle.model.Role;
import com.example.newmoodle.model.Section;
import com.example.newmoodle.model.User;
import com.example.newmoodle.model.response.ApiError;
import com.example.newmoodle.repository.SectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SectionService {

    private final SectionRepository sectionRepository;
    private final UserService userService;


    public Section createSection(String section) {
        var section1 = Section.builder()
                .name(section)
                .build();
        return sectionRepository.save(section1);
    }

    public Section getSectionById(Long id) {
        return sectionRepository.findById(id).orElseThrow(()
                -> new IllegalArgumentException(String.format("Section with id '%d' not found", id)));
    }

    public void setTeacher(Long userId, Long sectionId) {
        Section section = getSectionById(sectionId);
        User user = userService.findUserById(userId);
        if(user != null) {
            if (userService.checkRoles(userId, Role.TEACHER)) {
                section.setTeacher(user);
                sectionRepository.save(section);
            } else {
                throw new IllegalArgumentException("User is not a teacher"); // Ошибка 400
            }
        }  else throw new IllegalArgumentException("User is not a teacher"); // Ошибка 400
    }

    @Transactional
    public Section setStudent(Long userId, Long sectionId) {
        Section section = getSectionById(sectionId);
        User user = userService.findUserById(userId);
        if (userService.checkRoles(userId, Role.STUDENT)) {
            if (section.getStudents() == null) {
                section.setStudents(new HashSet<>());
            }
            section.getStudents().add(user);
           return sectionRepository.save(section);
        } else {
                throw new IllegalArgumentException("User is not a Student");
            }
    }

    public List<Section> getAllSections() {
        return sectionRepository.findAll();
    }

    public Section getSectionById(String id) {
        return sectionRepository.findById(Long.parseLong(id)).orElseThrow(()
                -> new IllegalArgumentException(String.format("Section with id '%s' not found", id)));
    }
}
