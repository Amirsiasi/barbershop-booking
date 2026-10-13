package at.barberbook.shop;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OpeningHoursRepository extends JpaRepository<OpeningHours, Long> {
    List<OpeningHours> findByShopIdOrderByDayOfWeek(Long shopId);

    Optional<OpeningHours> findByShopIdAndDayOfWeek(Long shopId, int dayOfWeek);
}
