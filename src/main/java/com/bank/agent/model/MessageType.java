package com.bank.agent.model;

/**
 * Identifies the source queue / message format of an inbound bank MQ message.
 */
public enum MessageType {

    /** SWIFT MT — fixed-field text format (e.g. MT103, MT202) */
    MT,

    /** ISO 20022 — XML-based format (e.g. pain.001, pacs.008) */
    MX,

    /** Fedwire — US Federal Reserve wire transfer format */
    FED
}
