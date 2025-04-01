package com.example.newmoodle.service;

import com.example.newmoodle.model.response.JwtAuthenticationResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CookieService {

    private ResponseCookie generateResponseCookie(String name, String value, int maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .sameSite("None")
                .secure(true)
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    public void setAuthenticationCookies(HttpServletResponse response, JwtAuthenticationResponse jwtAuthenticationResponse) {
        ResponseCookie refreshTokenResponseCookie = generateResponseCookie("refreshToken",
                jwtAuthenticationResponse.getRefreshToken(), 60 * 60 * 24 * 14);
        response.addHeader("Set-Cookie", refreshTokenResponseCookie.toString());
    }
}