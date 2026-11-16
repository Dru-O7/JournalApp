package net.engineeringdigest.journalApp.repository;

import net.engineeringdigest.journalApp.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    // lower() matches the users_username_lower_uk index (Spring's IgnoreCase would use upper()).
    @Query("select u from User u where lower(u.username) = lower(:username)")
    Optional<User> findByUsernameCaseInsensitive(@Param("username") String username);

    @Query("select count(u) > 0 from User u where lower(u.username) = lower(:username) and u.id <> :excludeId")
    boolean usernameTakenByOther(@Param("username") String username, @Param("excludeId") UUID excludeId);

    @Query("select count(u) > 0 from User u where lower(u.username) = lower(:username)")
    boolean usernameTaken(@Param("username") String username);

    Page<User> findByWeeklyReportTrue(Pageable pageable);
}
