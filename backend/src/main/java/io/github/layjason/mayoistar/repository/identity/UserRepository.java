package io.github.layjason.mayoistar.repository.identity;

import io.github.layjason.mayoistar.entity.identity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, String> {}
