package io.github.layjason.mayoistar.repository;

import io.github.layjason.mayoistar.entity.chat.TeamAnnouncement;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TeamAnnouncementRepository extends JpaRepository<TeamAnnouncement, String> {

    Page<TeamAnnouncement> findByTeamIdOrderByPublishedAtDesc(String teamId, Pageable pageable);

    List<TeamAnnouncement> findByTeamId(String teamId);
}
