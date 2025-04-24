package com.example.newmoodle.controller;

import com.example.newmoodle.model.Role;
import com.example.newmoodle.service.SectionService;
import com.example.newmoodle.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final SectionService sectionService;

    @GetMapping("/authenticated")
    public ResponseEntity<?> getAuthenticatedUserId() {
        return ResponseEntity.ok(userService.getAuthenticatedUser());
    }
    @GetMapping("/{id}")
    public ResponseEntity<?> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(userService.findUserById(id));
    }

    @RequestMapping(value = "/confirmemail", method = {RequestMethod.GET, RequestMethod.POST})
    private ResponseEntity<?> confirmEmail(@RequestParam("token") String token) {
        return userService.confirmEmail(token);
    }

    @PutMapping("/role")
    public ResponseEntity<?> updateRole(@RequestBody Role role) {
        return ResponseEntity.ok(userService.updateUserRole(userService.getAuthenticatedUser(), role));
    }

    @GetMapping("/all")
    public ResponseEntity<?> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @DeleteMapping("/delete")
    public ResponseEntity<?> deleteAccout(){
        userService.deleteUserById(userService.getAuthenticatedUser().getId());
        return ResponseEntity.ok("Account successfully deleted ");
    }

    @GetMapping("/sections")
    public ResponseEntity<?> getAllSections() {
        return ResponseEntity.ok(sectionService.getSectionsByStudent(userService.getAuthenticatedUser()));
    }
}
