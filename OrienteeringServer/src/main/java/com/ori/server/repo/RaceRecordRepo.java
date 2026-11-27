package com.ori.server.repo;

import com.ori.server.entity.RaceRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RaceRecordRepo extends JpaRepository<RaceRecord, Long> {
    List<RaceRecord> findByCompetitionIdOrderByIdDesc(Long competitionId);

    List<RaceRecord> findByUserIdOrderByIdDesc(Long userId);
}
