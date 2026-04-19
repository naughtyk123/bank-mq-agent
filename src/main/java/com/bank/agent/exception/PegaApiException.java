package com.bank.agent.exception;

/**
 * Thrown when the Pega DX API returns a non-2xx response.
 */
public class PegaApiException extends RuntimeException {

    public PegaApiException(String message) {
        super(message);
    }

    public PegaApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
