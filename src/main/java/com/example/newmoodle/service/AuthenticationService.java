package com.example.newmoodle.service;

import com.example.newmoodle.model.User;
import com.example.newmoodle.model.request.SignInRequest;
import com.example.newmoodle.model.request.SignUpRequest;
import com.example.newmoodle.model.response.JwtAuthenticationResponse;
import com.example.newmoodle.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthenticationService{
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    private final RefreshTokenRepository refreshTokenRepository;

    public JwtAuthenticationResponse signup(SignUpRequest request) {

        if (userService.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }

        var user = User.builder()
                .email(request.getEmail())
                .fullName(request.getFullName())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .enabled(true)
                .build();


        userService.saveUser(user);

        return JwtAuthenticationResponse.builder()
                .accessToken(jwtService.generateAccessToken(user))
                .refreshToken(jwtService.generateRefreshToken(user).getRefreshToken())
                .build();
    }

    public JwtAuthenticationResponse signin(SignInRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        var user = userService.findUserByEmail(request.getEmail());
        refreshTokenRepository.deleteById(user.getId());
        return JwtAuthenticationResponse.builder()
                .accessToken(jwtService.generateAccessToken(user))
                .refreshToken(jwtService.generateRefreshToken(user).getRefreshToken())
                .build();
    }

    public JwtAuthenticationResponse refresh(String refreshToken) {
        User user = refreshTokenRepository.findByRefreshToken(refreshToken).orElseThrow(
                () -> new NoSuchElementException("incorrect refresh token")).getUser();
        refreshTokenRepository.deleteByRefreshToken(refreshToken);
        return JwtAuthenticationResponse.builder()
                .accessToken(jwtService.generateAccessToken(user))
                .refreshToken(jwtService.generateRefreshToken(user).getRefreshToken())
                .build();
    }
}