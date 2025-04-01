package com.example.newmoodle.service;

import com.example.newmoodle.model.Role;
import com.example.newmoodle.model.User;
import com.example.newmoodle.repository.ConfirmationTokenRepository;
import com.example.newmoodle.repository.RefreshTokenRepository;
import com.example.newmoodle.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.NonUniqueObjectException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ConfirmationTokenService confirmationTokenService;
    private final ConfirmationTokenRepository confirmationTokenRepository;

    public UserDetailsService userDetailsService() {
        return username -> userRepository.findUserByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    public User getAuthenticatedUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
    public ResponseEntity<?> confirmEmail(String token) {
        var confirmationToken = confirmationTokenRepository.findConfirmationTokenByConfirmationToken(token);
        User user = confirmationToken.orElseThrow().getUser();
        if (user.isEnabled()) return ResponseEntity.ok("email already verified");
        user.setEnabled(true);
        userRepository.save(user);
        return ResponseEntity.ok("email successfully verified");
    }

    public User saveUser(User user) {
        if (userRepository.existsByEmail(user.getEmail()))
            throw new NonUniqueObjectException("", null, user.getEmail());
        userRepository.save(user);
        confirmationTokenService.sendConfirmationToken(user);
        return user;
    }

    public User findUserById(Long id) {
        return userRepository.findById(id).orElseThrow(()
                -> new NoSuchElementException(String.format("User with id '%d' not found", id)));
    }

    public User findUserByEmail(String email) {
        return userRepository.findUserByEmail(email).orElseThrow(()
                -> new NoSuchElementException(String.format("User with email '%d' not found", email)));
    }

    public User updateUserRole(User user, Role role){
        user.setRole(role);
        return userRepository.save(user);
    }

    public boolean checkRoles(Long id, Role role) {
        User user = findUserById(id);
        return user.getRole().equals(role);
    }

    public void updateUserRole(Long id, Role role){
        User user = findUserById(id);
        user.setRole(role);
        userRepository.save(user);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

}
