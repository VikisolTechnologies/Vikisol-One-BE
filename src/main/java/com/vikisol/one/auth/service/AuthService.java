package com.vikisol.one.auth.service;

import com.vikisol.one.auth.dto.*;
import com.vikisol.one.auth.entity.User;
import com.vikisol.one.auth.repository.UserRepository;
import com.vikisol.one.common.exception.BadRequestException;
import com.vikisol.one.security.RoleEnum;
import com.vikisol.one.security.jwt.JwtTokenProvider;
import com.vikisol.one.security.service.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        String token = jwtTokenProvider.generateToken(authentication);
        String refreshToken = jwtTokenProvider.generateTokenFromEmail(request.email());

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadRequestException("User not found"));

        return new AuthResponse(token, refreshToken, user.getEmail(),
                user.getRole().name(), user.getFirstName(), user.getLastName());
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BadRequestException("Email is already registered");
        }

        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .role(request.role() != null ? request.role() : RoleEnum.EMPLOYEE)
                .enabled(true)
                .accountNonLocked(true)
                .build();

        userRepository.save(user);

        String token = jwtTokenProvider.generateTokenFromEmail(user.getEmail());

        return new AuthResponse(token, null, user.getEmail(),
                user.getRole().name(), user.getFirstName(), user.getLastName());
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request, UserPrincipal principal) {
        User user = userRepository.findByEmail(principal.getEmail())
                .orElseThrow(() -> new BadRequestException("User not found"));

        if (!passwordEncoder.matches(request.oldPassword(), user.getPassword())) {
            throw new BadRequestException("Old password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }
}
