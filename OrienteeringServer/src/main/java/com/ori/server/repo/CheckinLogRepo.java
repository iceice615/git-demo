package com.ori.server.repo;

import com.ori.server.entity.CheckinLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CheckinLogRepo extends JpaRepository<CheckinLog, Long> {
    List<CheckinLog> findByRaceIdOrderByTsAsc(Long raceId);
}
