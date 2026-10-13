package at.barberbook.chat;

import at.barberbook.shop.Shop;

/**
 * Delivers a chat message to a customer's phone.
 *
 * <p>Phase 1: {@link LoggingChatGateway} only logs (messages are visible in the admin simulator).
 * Phase 2: a WhatsApp Cloud API implementation annotated with {@code @Primary} - nothing else changes.
 */
public interface ChatGateway {

    void send(Shop shop, String phone, String text);
}
