package com.ori.server.repo;

import com.ori.server.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepo extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    /** 按会话令牌查用户（鉴权用） */
    Optional<User> findByToken(String token);
}
