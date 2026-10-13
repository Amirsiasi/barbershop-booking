package at.barberbook.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    /** Appointments of the given barbers that block time and overlap [from, to). */
    @Query("""
            select a from Appointment a
            where a.barber.id in :barberIds
              and a.status in (at.barberbook.booking.AppointmentStatus.PENDING,
                               at.barberbook.booking.AppointmentStatus.CONFIRMED,
                               at.barberbook.booking.AppointmentStatus.COMPLETED)
              and a.startsAt < :to and a.endsAt > :from""")
    List<Appointment> findBlocking(@Param("barberIds") Collection<Long> barberIds,
                                   @Param("from") Instant from,
                                   @Param("to") Instant to);

    @Query("""
            select a from Appointment a
            join fetch a.barber join fetch a.service join fetch a.customer
            where a.manageToken = :token""")
    Optional<Appointment> findByToken(@Param("token") UUID token);

    @Query("""
            select a from Appointment a
            join fetch a.barber join fetch a.service join fetch a.customer
            where a.shopId = :shopId and a.startsAt < :to and a.endsAt > :from
            order by a.startsAt""")
    List<Appointment> findForShop(@Param("shopId") Long shopId,
                                  @Param("from") Instant from,
                                  @Param("to") Instant to);

    /** Open requests of a shop, oldest first - the barber's to-do list. */
    @Query("""
            select a from Appointment a
            join fetch a.barber join fetch a.service join fetch a.customer
            where a.shopId = :shopId and a.status = at.barberbook.booking.AppointmentStatus.PENDING
            order by a.startsAt""")
    List<Appointment> findPending(@Param("shopId") Long shopId);

    /** Requests nobody confirmed in time. */
    @Query("""
            select a from Appointment a
            join fetch a.barber join fetch a.service join fetch a.customer
            where a.status = at.barberbook.booking.AppointmentStatus.PENDING and a.pendingUntil <= :now""")
    List<Appointment> findExpiredRequests(@Param("now") Instant now);

    @Query("""
            select a from Appointment a
            join fetch a.barber join fetch a.service join fetch a.customer
            where a.id = :id""")
    Optional<Appointment> findWithDetails(@Param("id") Long id);

    @Query("""
            select a from Appointment a
            join fetch a.barber join fetch a.service join fetch a.customer
            where a.status = at.barberbook.booking.AppointmentStatus.CONFIRMED
              and a.reminderSentAt is null
              and a.startsAt > :now and a.startsAt <= :until""")
    List<Appointment> findDueForReminder(@Param("now") Instant now, @Param("until") Instant until);
}
