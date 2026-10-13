package at.barberbook.common;

import java.util.List;

/** Uniform error body for every failed request (taken over from customer-crud-spring-react). */
public record ApiError(int status, String error, String message, String path, List<ValidationError> validationErrors) {
}
