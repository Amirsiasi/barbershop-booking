package at.barberbook.chat;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByShopIdAndPhoneOrderByCreatedAtAscIdAsc(Long shopId, String phone);

    /** Newest messages of the shop, used to build the conversation list. */
    List<ChatMessage> findByShopIdOrderByCreatedAtDescIdDesc(Long shopId, Pageable page);
}
