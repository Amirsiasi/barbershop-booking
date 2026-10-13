package at.barberbook.shop;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface ShopPhotoRepository extends JpaRepository<ShopPhoto, Long> {

    /** Only the timestamp (for the cache-busting URL) - without loading the image bytes. */
    @Query("select p.updatedAt from ShopPhoto p where p.shopId = :shopId")
    Optional<Instant> findUpdatedAt(@Param("shopId") Long shopId);
}
