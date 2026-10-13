package at.barberbook.booking;

public enum AppointmentStatus {
    CONFIRMED,
    CANCELLED,
    COMPLETED,
    NO_SHOW;

    /** Statuses that occupy the barber's time (mirrors the WHERE clause of the DB exclusion constraint). */
    public boolean blocksTime() {
        return this == CONFIRMED || this == COMPLETED;
    }
}
