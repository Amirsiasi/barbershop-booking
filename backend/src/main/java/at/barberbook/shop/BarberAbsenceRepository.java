package at.barberbook.shop;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface BarberAbsenceRepository extends JpaRepository<BarberAbsence, Long> {

    /** All absences of the given barbers that overlap [from, to). */
    @Query("""
            select a from BarberAbsence a
            where a.barberId in :barberIds and a.startsAt < :to and a.endsAt > :from
            order by a.startsAt""")
    List<BarberAbsence> findOverlapping(@Param("barberIds") Collection<Long> barberIds,
                                        @Param("from") Instant from,
                                        @Param("to") Instant to);
}
