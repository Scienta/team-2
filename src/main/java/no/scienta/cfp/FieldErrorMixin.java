package no.scienta.cfp;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import org.springframework.boot.jackson.JsonMixin;
import org.springframework.validation.FieldError;

/**
 * Keeps the key order of the entries in a 400's "errors" array as it was on Spring Boot 2.7; Spring 6 changed it.
 */
@JsonMixin(FieldError.class)
@JsonPropertyOrder({"codes", "arguments", "defaultMessage", "objectName", "field", "rejectedValue", "bindingFailure", "code"})
abstract class FieldErrorMixin {
}
