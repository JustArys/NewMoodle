package com.example.newmoodle.controller;

import com.example.newmoodle.model.request.CreateSection;
import com.example.newmoodle.model.request.SectionDto;
import com.example.newmoodle.service.SectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/v1/section")
@RequiredArgsConstructor
public class SectionController {

    private final SectionService sectionService;

    @GetMapping
    public ResponseEntity<List<SectionDto>> getAllSections() {
        List<SectionDto> sections = sectionService.getAllSectionDTOs();
        return ResponseEntity.ok(sections);
    }

    @GetMapping("/{sectionId}")
    public ResponseEntity<SectionDto> getSection(@PathVariable Long sectionId) {
        SectionDto section = sectionService.getSectionDTOById(sectionId);
        return ResponseEntity.ok(section);
    }

    @PostMapping
    public ResponseEntity<SectionDto> createSection(@RequestBody CreateSection createSectionRequest) {
        SectionDto newSection = sectionService.createSection(createSectionRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(newSection);
    }

    @PatchMapping("/{sectionId}/teacher")
    public ResponseEntity<SectionDto> setTeacher(@PathVariable Long sectionId, @RequestParam Long userId) {
        SectionDto updatedSection = sectionService.setTeacher(userId, sectionId);
        return ResponseEntity.ok(updatedSection);
    }

    @PatchMapping("/{sectionId}/student")
    public ResponseEntity<SectionDto> setStudent(@PathVariable Long sectionId, @RequestParam Long userId) {
        SectionDto updatedSection = sectionService.setStudent(userId, sectionId);
        return ResponseEntity.ok(updatedSection);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSection(@PathVariable Long id) {
        try {
            sectionService.deleteSection(id);
            return ResponseEntity.noContent().build(); // 204 No Content
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build(); // 404 Not Found
        } catch (Exception e) {
            // Логирование ошибки e (например, при удалении связанных заданий)
            System.err.println("Error deleting section " + id + ": " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}