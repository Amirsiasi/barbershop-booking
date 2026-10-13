package at.barberbook.shop;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BarberRepository extends JpaRepository<Barber, Long> {

    /** Bookable barbers, in the order customers see them. */
    List<Barber> findByShopIdAndActiveTrueOrderBySortOrder(Long shopId);

    /** The whole team including removed (inactive) barbers - for the admin area. */
    List<Barber> findByShopIdOrderBySortOrderAscIdAsc(Long shopId);
}
