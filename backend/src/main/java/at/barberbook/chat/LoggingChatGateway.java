package at.barberbook.chat;

import at.barberbook.shop.Shop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Phase 1 gateway: nothing leaves the server, replies are only logged and stored. */
@Component
public class LoggingChatGateway implements ChatGateway {

    private static final Logger log = LoggerFactory.getLogger(LoggingChatGateway.class);

    @Override
    public void send(Shop shop, String phone, String text) {
        log.info("WhatsApp (simuliert) an {}: {}", phone, text.replace("\n", " / "));
    }
}
