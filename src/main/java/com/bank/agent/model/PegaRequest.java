package com.bank.agent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

/**
 * Request body sent to Pega DX API when a message arrives from bank MQ (inbound flow).
 * Adjust fields to match your Pega case type data model.
 */
@Data
@Builder
@Jacksonized
public class PegaRequest {

    /** Pega case type ID, e.g. "BANK-TRANSACTION-WORK-PAYMENT" */
    private String caseTypeID;

    /** Arbitrary content mapped from the inbound MQ message */
    private PegaContent content;

    @Data
    @Builder
    @Jacksonized
    public static class PegaContent {
        private String transactionId;
        private String transactionType;
        private String amount;
        private String currency;
        private String sourceAccount;
        private String destinationAccount;
        private String rawPayload;
    }
}
