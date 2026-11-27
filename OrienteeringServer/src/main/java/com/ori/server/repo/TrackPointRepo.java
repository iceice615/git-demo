package com.ori.server.repo;

import com.ori.server.entity.TrackPoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrackPointRepo extends JpaRepository<TrackPoint, Long> {
    List<TrackPoint> findByRaceIdOrderByTsAsc(Long raceId);
}
