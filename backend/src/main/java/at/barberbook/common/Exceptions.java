package at.barberbook.common;

/** Domain exceptions; GlobalExceptionHandler maps each one to an HTTP status. */
public final class Exceptions {

    private Exceptions() {
    }

    /** 404 */
    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }

    /** 409 - e.g. the slot was taken a moment ago. */
    public static class ConflictException extends RuntimeException {
        public ConflictException(String message) {
            super(message);
        }
    }

    /** 422 - request is valid JSON but breaks a business rule (cancel too late, ...). */
    public static class BusinessRuleException extends RuntimeException {
        public BusinessRuleException(String message) {
            super(message);
        }
    }
}
