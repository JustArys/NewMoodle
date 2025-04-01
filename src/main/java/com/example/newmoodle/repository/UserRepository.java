package com.example.newmoodle.repository;

import com.example.newmoodle.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findUserByEmail(String email);
    boolean existsByEmail(String email);
    Page<User> findAllBy(Pageable pageable);
}