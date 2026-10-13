package at.barberbook.shop;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering, Long> {
    List<ServiceOffering> findByShopIdAndActiveTrueOrderBySortOrder(Long shopId);
}
