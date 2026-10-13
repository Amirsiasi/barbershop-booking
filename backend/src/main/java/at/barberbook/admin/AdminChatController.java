package at.barberbook.admin;

import at.barberbook.chat.ChatBookingService;
import at.barberbook.chat.ChatBookingService.ChatMessageView;
import at.barberbook.chat.ChatBookingService.ConversationView;
import at.barberbook.common.Exceptions.NotFoundException;
import at.barberbook.shop.Shop;
import at.barberbook.shop.ShopRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Chat conversations for the admin area, plus a simulator: the barber (or a developer) can type a message
 * "as a customer" and see exactly what a real WhatsApp customer would get back. Phase 2 adds a webhook
 * that feeds real WhatsApp messages into the same {@link ChatBookingService#handleIncoming} method.
 */
@RestController
@RequestMapping("/api/admin/chat")
public class AdminChatController {

    public record SimulatedMessage(
            @NotBlank(message = "Telefonnummer fehlt.")
            @Pattern(regexp = "^[+0-9 ()/-]{6,25}$", message = "Bitte gib eine gültige Telefonnummer ein.") String phone,
            @Size(max = 60) String profileName,
            @NotBlank(message = "Nachricht fehlt.") @Size(max = 1000) String text) {
    }

    private final AdminProperties props;
    private final ShopRepository shops;
    private final ChatBookingService chat;

    public AdminChatController(AdminProperties props, ShopRepository shops, ChatBookingService chat) {
        this.props = props;
        this.shops = shops;
        this.chat = chat;
    }

    @GetMapping("/conversations")
    public List<ConversationView> conversations() {
        return chat.conversations(shop().getId());
    }

    @GetMapping("/messages")
    public List<ChatMessageView> messages(@RequestParam String phone) {
        return chat.messages(shop().getId(), phone);
    }

    /** Process a message as if it came from WhatsApp and return the whole conversation afterwards. */
    @PostMapping("/simulate")
    public List<ChatMessageView> simulate(@Valid @RequestBody SimulatedMessage msg) {
        Shop shop = shop();
        chat.handleIncoming(shop.getSlug(), msg.phone(), msg.profileName(), msg.text());
        return chat.messages(shop.getId(), msg.phone());
    }

    private Shop shop() {
        return shops.findBySlug(props.shopSlug()).orElseThrow(() -> new NotFoundException("Shop nicht gefunden."));
    }
}
