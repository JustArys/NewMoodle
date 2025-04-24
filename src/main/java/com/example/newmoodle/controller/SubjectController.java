package com.example.newmoodle.controller;

import com.example.newmoodle.service.SubjectServer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;

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
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSubject(@PathVariable Long id) {
        try {
            subjectServer.deleteSubject(id);
            return ResponseEntity.noContent().build(); // 204 No Content
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build(); // 404 Not Found
        } catch (IllegalStateException e) {
            // Ошибка, если предмет нельзя удалить из-за связанных секций
            return ResponseEntity.status(HttpStatus.CONFLICT).build(); // 409 Conflict
        } catch (Exception e) {
            // Логирование ошибки e
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

}
