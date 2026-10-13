package at.barberbook.booking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByShopIdAndPhone(Long shopId, String phone);
}
