package at.barberbook.staff;

public enum StaffRole {
    /** Everything: calendar, requests, team, logins, prices. */
    OWNER,
    /** Sees the whole calendar (to help colleagues), but changes only own appointments and blocked times. */
    BARBER
}
