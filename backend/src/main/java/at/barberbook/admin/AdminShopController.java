package at.barberbook.admin;

import at.barberbook.booking.BookingService;
import at.barberbook.common.Exceptions.BusinessRuleException;
import at.barberbook.common.Exceptions.NotFoundException;
import at.barberbook.shop.Shop;
import at.barberbook.shop.ShopPhoto;
import at.barberbook.shop.ShopPhotoRepository;
import at.barberbook.shop.ShopRepository;
import at.barberbook.staff.CurrentStaff;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Clock;

/**
 * Shop settings for the owner: the photo and the rating shown in the summary card of the booking page.
 * The rating is entered by hand (e.g. from Google), we do not collect reviews ourselves.
 */
@RestController
@RequestMapping("/api/admin/shop")
public class AdminShopController {

    static final int MAX_PHOTO_BYTES = 5 * 1024 * 1024;

    public record ShopSettings(BigDecimal rating, Integer reviewCount, String photoUrl,
                               String phone, String email, String instagram, String whatsapp) {
    }

    /** Contact data for the footer. Every field is optional: an empty one is simply not shown. */
    public record ContactForm(
            @Pattern(regexp = "^$|^[+0-9 ()/-]{6,25}$", message = "Bitte gib eine gültige Telefonnummer ein.") String phone,
            @Email(message = "Bitte gib eine gültige E-Mail-Adresse ein.") @Size(max = 120) String email,
            @Pattern(regexp = "^$|^(https?://(www\\.)?instagram\\.com/)?@?[A-Za-z0-9._]{1,30}/?$",
                    message = "Bitte gib den Instagram-Namen ein, z. B. mrartist.barbershop") String instagram,
            @Pattern(regexp = "^$|^[+0-9 ()/-]{6,25}$", message = "Bitte gib eine gültige WhatsApp-Nummer ein.") String whatsapp) {
    }

    public record RatingForm(
            @DecimalMin(value = "1.0", message = "Die Bewertung liegt zwischen 1,0 und 5,0.")
            @DecimalMax(value = "5.0", message = "Die Bewertung liegt zwischen 1,0 und 5,0.")
            @Digits(integer = 1, fraction = 1, message = "Bitte nur eine Nachkommastelle, z. B. 4,8.") BigDecimal rating,
            @Min(value = 0, message = "Die Anzahl kann nicht negativ sein.")
            @Max(value = 1_000_000, message = "Das sind zu viele Bewertungen.") Integer reviewCount) {
    }

    private final CurrentStaff current;
    private final ShopRepository shops;
    private final ShopPhotoRepository photos;
    private final Clock clock;

    public AdminShopController(CurrentStaff current, ShopRepository shops, ShopPhotoRepository photos, Clock clock) {
        this.current = current;
        this.shops = shops;
        this.photos = photos;
        this.clock = clock;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ShopSettings get() {
        return view(current.shop());
    }

    @PutMapping("/contact")
    @Transactional
    public ShopSettings contact(@Valid @RequestBody ContactForm form) {
        Shop shop = shops.findById(current.shop().getId()).orElseThrow();
        shop.updateContact(blank(form.phone()), blank(form.email()), instagramHandle(form.instagram()),
                blank(form.whatsapp()) == null ? null : BookingService.normalizePhone(form.whatsapp()));
        return view(shop);
    }

    /** "https://instagram.com/mr.artist/", "@mr.artist" or "mr.artist" -> "mr.artist". */
    static String instagramHandle(String raw) {
        String s = blank(raw);
        if (s == null) return null;
        s = s.replaceFirst("^https?://(www\\.)?instagram\\.com/", "").replaceFirst("^@", "").replaceFirst("/$", "");
        return s.isEmpty() ? null : s;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    @PutMapping("/rating")
    @Transactional
    public ShopSettings rating(@Valid @RequestBody RatingForm form) {
        Shop shop = shops.findById(current.shop().getId()).orElseThrow();
        if (form.rating() == null && form.reviewCount() != null && form.reviewCount() > 0) {
            throw new BusinessRuleException("Bitte gib auch die Bewertung an (z. B. 4,8).");
        }
        shop.updateRating(form.rating(), form.rating() == null ? null : form.reviewCount());
        return view(shop);
    }

    /**
     * Uploads the photo as the raw request body (Content-Type image/jpeg, image/png or image/webp).
     * The real type is taken from the file's first bytes, not from the header, so nothing but a real
     * picture can ever be served from our domain.
     */
    @PutMapping("/photo")
    @Transactional
    public ShopSettings upload(HttpServletRequest request) throws IOException {
        byte[] data = readLimited(request.getInputStream());
        String type = detectImageType(data);
        if (type == null) throw new BusinessRuleException("Bitte lade ein Foto als JPG, PNG oder WebP hoch.");

        Long shopId = current.shop().getId();
        ShopPhoto photo = photos.findById(shopId).orElseGet(() -> new ShopPhoto(shopId));
        photo.replace(type, data, clock.instant());
        photos.saveAndFlush(photo);
        return view(current.shop());
    }

    @DeleteMapping("/photo")
    @Transactional
    public ShopSettings deletePhoto() {
        Long shopId = current.shop().getId();
        if (!photos.existsById(shopId)) throw new NotFoundException("Kein Foto vorhanden.");
        photos.deleteById(shopId);
        photos.flush();
        return view(current.shop());
    }

    // ---- helpers ----------------------------------------------------------------------------------

    private ShopSettings view(Shop shop) {
        String url = photos.findUpdatedAt(shop.getId())
                .map(at -> "/api/shops/" + shop.getSlug() + "/photo?v=" + at.toEpochMilli())
                .orElse(null);
        return new ShopSettings(shop.getRating(), shop.getReviewCount(), url,
                shop.getPhone(), shop.getEmail(), shop.getInstagram(), shop.getWhatsapp());
    }

    private static byte[] readLimited(InputStream in) throws IOException {
        byte[] data = in.readNBytes(MAX_PHOTO_BYTES + 1);
        if (data.length == 0) throw new BusinessRuleException("Es wurde kein Foto mitgeschickt.");
        if (data.length > MAX_PHOTO_BYTES) throw new BusinessRuleException("Das Foto ist zu groß (höchstens 5 MB).");
        return data;
    }

    /** JPEG, PNG or WebP by their magic numbers; anything else (SVG, HTML, PDF ...) -> null. */
    static String detectImageType(byte[] d) {
        if (d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (d.length >= 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G'
                && d[4] == 0x0D && d[5] == 0x0A && d[6] == 0x1A && d[7] == 0x0A) return "image/png";
        if (d.length >= 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') return "image/webp";
        return null;
    }
}
