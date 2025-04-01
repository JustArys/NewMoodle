package com.example.newmoodle.controller;

import com.example.newmoodle.service.SectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/section")
@RequiredArgsConstructor
public class SectionController {
    private final SectionService sectionService;


    @GetMapping
    public ResponseEntity<?> getAllSections() {
        return ResponseEntity.ok(sectionService.getAllSections());
    }

    @GetMapping("/{sectionId}")
    public ResponseEntity<?> getSection(@PathVariable Long sectionId) {
        return ResponseEntity.ok(sectionService.getSectionById(sectionId));
    }

    @PostMapping
    public ResponseEntity<?> createSection(@RequestParam String name) {
        return ResponseEntity.ok(sectionService.createSection(name));
    }

    @PatchMapping("/{sectionId}/teacher")
    public ResponseEntity<?> setTeacher(@PathVariable Long sectionId, @RequestParam Long userId) {
        sectionService.setTeacher(userId, sectionId);
        return ResponseEntity.ok("Successfully set teacher");
    }

    @PatchMapping("/{sectionId}/student")
    public ResponseEntity<?> setStudent(@PathVariable Long sectionId, @RequestParam Long userId) {

        return ResponseEntity.ok( sectionService.setStudent(userId, sectionId)  );
    }


}
