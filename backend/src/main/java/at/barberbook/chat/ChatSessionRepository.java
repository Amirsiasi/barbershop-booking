package at.barberbook.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    Optional<ChatSession> findByShopIdAndPhone(Long shopId, String phone);

    /** A conversation that mentioned a deleted barber continues with "any barber". */
    @Modifying
    @Query("update ChatSession s set s.barberId = null where s.barberId = :barberId")
    int forgetBarber(@Param("barberId") Long barberId);
}
