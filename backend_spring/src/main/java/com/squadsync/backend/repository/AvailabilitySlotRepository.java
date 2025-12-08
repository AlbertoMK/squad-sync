package com.squadsync.backend.repository;

import com.squadsync.backend.model.AvailabilitySlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AvailabilitySlotRepository extends JpaRepository<AvailabilitySlot, String> {
    List<AvailabilitySlot> findByUserId(String userId);

    List<AvailabilitySlot> findByStartTimeGreaterThanEqualOrderByStartTimeAsc(LocalDateTime startTime);
}
