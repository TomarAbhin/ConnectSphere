package com.connectsphere.auth.repository;

import com.connectsphere.auth.entity.User;
import com.connectsphere.auth.entity.UserRole;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    @Query("""
            SELECT u FROM User u
          WHERE u.active = true
              AND (:role IS NULL OR u.role = :role)
              AND (
                    :query IS NULL OR :query = ''
                    OR LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(COALESCE(u.fullName, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                  )
            ORDER BY u.username
            """)
    List<User> searchActiveUsers(@Param("query") String query, @Param("role") UserRole role);

        @Query("""
          SELECT u FROM User u
          WHERE (:role IS NULL OR u.role = :role)
            AND (
            :query IS NULL OR :query = ''
            OR LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(COALESCE(u.fullName, '')) LIKE LOWER(CONCAT('%', :query, '%'))
          )
          ORDER BY u.username
          """)
        List<User> searchAllUsers(@Param("query") String query, @Param("role") UserRole role);

        long countByActiveTrue();

        long countByActiveFalse();

        long countByLastLoginAtAfter(Instant instant);
}
