package at.barberbook.shop;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BarberRepository extends JpaRepository<Barber, Long> {
    List<Barber> findByShopIdAndActiveTrueOrderBySortOrder(Long shopId);
}
