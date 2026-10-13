package at.barberbook.shop;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering, Long> {

    /** What customers can book. */
    List<ServiceOffering> findByShopIdAndActiveTrueOrderBySortOrder(Long shopId);

    /** Everything, including hidden services - for the admin price list. */
    List<ServiceOffering> findByShopIdOrderBySortOrderAscIdAsc(Long shopId);
}
