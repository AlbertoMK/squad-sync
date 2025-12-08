package com.squadsync.backend.repository;

import com.squadsync.backend.model.UserGamePreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserGamePreferenceRepository extends JpaRepository<UserGamePreference, String> {
    List<UserGamePreference> findByUserId(String userId);

    Optional<UserGamePreference> findByUserIdAndGameId(String userId, String gameId);

    List<UserGamePreference> findByUserIdIn(List<String> userIds);
}
