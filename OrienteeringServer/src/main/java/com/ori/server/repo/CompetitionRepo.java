package com.ori.server.repo;

import com.ori.server.entity.Competition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompetitionRepo extends JpaRepository<Competition, Long> {
    List<Competition> findByOwnerIdOrderByIdDesc(Long ownerId);

    Optional<Competition> findByJoinKey(String joinKey);
}
