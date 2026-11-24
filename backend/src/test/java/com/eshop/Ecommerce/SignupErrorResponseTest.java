package com.eshop.Ecommerce;

import com.eshop.Ecommerce.Exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

class SignupErrorResponseTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unavailableMailProviderExplainsHowToRecover() {
        var result = handler.handleResponseStatusException(new ResponseStatusException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Signup requires configured SMTP or Gmail API delivery and EMAIL_FROM."));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, result.getStatusCode());
        assertEquals(503, result.getBody().get("status"));
        assertTrue(result.getBody().get("message").toString().contains("EMAIL_FROM"));
    }

    @Test
    void duplicateAccountPreservesConflictStatusAndReason() {
        var result = handler.handleResponseStatusException(
            new ResponseStatusException(HttpStatus.CONFLICT, "Username or email already registered"));
        assertEquals(HttpStatus.CONFLICT, result.getStatusCode());
        assertEquals("Username or email already registered", result.getBody().get("message"));
    }

    @Test
    void unknownExceptionReasonUsesSafeGenericMessage() {
        var result = handler.handleResponseStatusException(
            new ResponseStatusException(HttpStatus.BAD_REQUEST));
        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
        assertEquals("Request could not be completed", result.getBody().get("message"));
    }
}
