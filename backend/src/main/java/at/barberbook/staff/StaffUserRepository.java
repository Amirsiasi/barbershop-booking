package at.barberbook.staff;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StaffUserRepository extends JpaRepository<StaffUser, Long> {

    Optional<StaffUser> findByUsername(String username);

    Optional<StaffUser> findByBarberId(Long barberId);

    List<StaffUser> findByShopIdOrderByUsername(Long shopId);
}
