package io.github.layjason.mayoistar.repository;

import io.github.layjason.mayoistar.entity.chat.TeamAnnouncementRead;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TeamAnnouncementReadRepository extends JpaRepository<TeamAnnouncementRead, String> {

    Optional<TeamAnnouncementRead> findByAnnouncementIdAndUserId(String announcementId, String userId);

    List<TeamAnnouncementRead> findByAnnouncementIdInAndUserId(List<String> announcementIds, String userId);

    List<TeamAnnouncementRead> findByAnnouncementId(String announcementId);
}
