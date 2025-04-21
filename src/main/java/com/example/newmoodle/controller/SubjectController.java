package com.example.newmoodle.controller;

import com.example.newmoodle.service.SubjectServer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/subject")
@RequiredArgsConstructor
public class SubjectController {
    private final SubjectServer subjectServer;

    @PostMapping
    public ResponseEntity<?> createSubject(@RequestParam String name) {
        return ResponseEntity.ok(subjectServer.saveSubject(name));
    }

    @GetMapping
    public ResponseEntity<?> getAllSubjects() {
        return ResponseEntity.ok(subjectServer.getSubjects());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getSubject(@PathVariable Long id) {
        return ResponseEntity.ok(subjectServer.getSubjectById(id));
    }

}
