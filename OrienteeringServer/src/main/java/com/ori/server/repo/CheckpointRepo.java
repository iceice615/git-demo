package com.ori.server.repo;

import com.ori.server.entity.Checkpoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CheckpointRepo extends JpaRepository<Checkpoint, Long> {
    List<Checkpoint> findByCompetitionIdOrderBySeqAsc(Long competitionId);
}
