package com.ori.server.repo;

import com.ori.server.entity.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EnrollmentRepo extends JpaRepository<Enrollment, Long> {
    List<Enrollment> findByCompetitionIdOrderByIdAsc(Long competitionId);

    List<Enrollment> findByUserIdOrderByIdDesc(Long userId);

    Optional<Enrollment> findByCompetitionIdAndUserId(Long competitionId, Long userId);

    void deleteByCompetitionIdAndUserId(Long competitionId, Long userId);
}
