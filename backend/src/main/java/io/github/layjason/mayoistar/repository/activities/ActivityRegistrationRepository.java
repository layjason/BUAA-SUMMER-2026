package io.github.layjason.mayoistar.repository.activities;

import io.github.layjason.mayoistar.entity.activities.ActivityRegistration;
import io.github.layjason.mayoistar.entity.activities.RegistrationStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityRegistrationRepository extends JpaRepository<ActivityRegistration, String> {

    List<ActivityRegistration> findByActivityIdAndStatusIn(String activityId, Collection<RegistrationStatus> statuses);
}
