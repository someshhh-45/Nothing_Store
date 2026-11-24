package com.eshop.Ecommerce.Exception;

import com.eshop.Ecommerce.Payload.APIResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Preserve intentionally safe, actionable HTTP errors from signup and verification.
    // In particular, a missing SMTP provider must not become an opaque "503 Service Unavailable".
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException e) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", e.getStatusCode().value());
        body.put("error", e.getStatusCode().toString());
        body.put("message", e.getReason() == null ? "Request could not be completed" : e.getReason());
        return ResponseEntity.status(e.getStatusCode()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String,String> > myMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("status", "400");
        errorResponse.put("error", "Validation Failed");
        e.getBindingResult().getFieldErrors().forEach(error ->
                errorResponse.put(error.getField(), error.getDefaultMessage())
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<APIResponse> myResourceNotFoundException(ResourceNotFoundException e){
        String message = e.getMessage();
        APIResponse apiResponse = new APIResponse(message,false);
        return new ResponseEntity<>(apiResponse, HttpStatus.NOT_FOUND);
    }
    @ExceptionHandler(APIException.class)
    public ResponseEntity<APIResponse> myAPIException(APIException e){
        String message = e.getMessage();
        APIResponse apiResponse = new APIResponse(message,false);
        return new ResponseEntity<>(apiResponse, HttpStatus.BAD_REQUEST);
    }
}
