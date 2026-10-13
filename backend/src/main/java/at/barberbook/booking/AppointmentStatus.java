package at.barberbook.booking;

public enum AppointmentStatus {
    /** Requested (e.g. via WhatsApp), slot is reserved, barber has not confirmed yet. */
    PENDING,
    CONFIRMED,
    CANCELLED,
    COMPLETED,
    NO_SHOW;

    /** Statuses that occupy the barber's time (mirrors the WHERE clause of the DB exclusion constraint). */
    public boolean blocksTime() {
        return this == PENDING || this == CONFIRMED || this == COMPLETED;
    }
}
