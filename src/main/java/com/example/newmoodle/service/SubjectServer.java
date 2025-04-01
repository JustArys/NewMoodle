package com.example.newmoodle.service;

import com.example.newmoodle.model.Section;
import com.example.newmoodle.model.Subject;
import com.example.newmoodle.repository.SubjectRepository;
import io.swagger.v3.oas.annotations.servers.Server;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class SubjectServer {
    private final SubjectRepository subjectRepository;
    private final SectionService sectionService;

    public Subject saveSubject(String name) {
        var subject = Subject.builder()
                .name(name);
        return subjectRepository.save(subject.build());
    }
    public List<Subject> getSubjects(){
        return subjectRepository.findAll();
    }

    public Subject setSection(Long id, Long sectionId) {
        Subject subject = getSubjectById(id);
        Section section = sectionService.getSectionById(sectionId);
        if (subject.getSection() == null) {
            subject.setSection(new ArrayList<>());
        }
        subject.getSection().add(section);
        return subjectRepository.save(subject);
    }

    public Subject getSubjectById(Long id) {
        return subjectRepository.findById(id).orElseThrow(()
                -> new NoSuchElementException(String.format("Subject with id '%d' not found", id)));
    }

    public Subject updateSubject(Long id,Subject subject) {
        Subject subject1 = subjectRepository.getReferenceById(id);
        subject1.setName(subject.getName());
        return subjectRepository.save(subject);
    }
}
