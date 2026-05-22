package io.leavesfly.jharness2.storage.repository;

import io.leavesfly.jharness2.storage.entity.UserEntity;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends CrudRepository<UserEntity, Long> {

    @Query("SELECT * FROM users WHERE username = :username")
    Optional<UserEntity> findByUsername(@Param("username") String username);

    @Query("SELECT COUNT(*) > 0 FROM users WHERE username = :username")
    boolean existsByUsername(@Param("username") String username);
}
