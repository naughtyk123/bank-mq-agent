package com.bank.agent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Response received from Pega DX API after case creation.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PegaResponse {

    /** The Pega case ID assigned to the created case */
    private String ID;

    /** Status returned by Pega (e.g. "New", "Open") */
    private String status;

    /** Business key set on the case */
    private String businessID;
}
