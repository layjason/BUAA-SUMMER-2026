package io.github.layjason.mayoistar.repository;

import io.github.layjason.mayoistar.entity.social.Follow;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FollowRepository extends JpaRepository<Follow, String> {

    boolean existsByFollowerIdAndFollowedId(String followerId, String followedId);

    Optional<Follow> findByFollowerIdAndFollowedId(String followerId, String followedId);

    Page<Follow> findByFollowerIdOrderByCreatedAtDesc(String followerId, Pageable pageable);

    Page<Follow> findByFollowedIdOrderByCreatedAtDesc(String followedId, Pageable pageable);

    void deleteByFollowerIdAndFollowedId(String followerId, String followedId);
}
