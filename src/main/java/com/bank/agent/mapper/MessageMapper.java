package com.bank.agent.mapper;

import com.bank.agent.model.MessageType;
import com.bank.agent.model.PegaRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps raw MQ message text to a PegaRequest, routing by MessageType.
 *
 * MT  — SWIFT fixed-field text (e.g. :20:, :32A:, :50K:, :59:)
 * MX  — ISO 20022 XML  (e.g. pain.001, pacs.008)
 * FED — Fedwire tag-value text (e.g. {1500}, {2000}, {3400})
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageMapper {

    private final ObjectMapper objectMapper;

    // ── Routing ──────────────────────────────────────────────────────────────

    public PegaRequest toPegaRequest(String rawMessage, MessageType type) {
        return switch (type) {
            case MT  -> parseMtMessage(rawMessage);
            case MX  -> parseMxMessage(rawMessage);
            case FED -> parseFedMessage(rawMessage);
        };
    }

    // ── MT parser (SWIFT MT fixed-field text) ────────────────────────────────

    /**
     * Parses SWIFT MT format.
     * Example fields: :20: (reference), :32A: (date/currency/amount), :50K: (ordering customer),
     *                 :59: (beneficiary), :71A: (charge code)
     *
     * Extend this method to support specific MT types (MT103, MT202, MT515, etc.)
     * by inspecting the message type block {2:I<type>} or {2:O<type>}.
     */
    private PegaRequest parseMtMessage(String rawMessage) {
        log.debug("Parsing MT message");

        PegaRequest.PegaContent content = PegaRequest.PegaContent.builder()
                .transactionId(extractMtField(rawMessage, "20"))
                .transactionType(detectMtType(rawMessage))
                .amount(extractMtAmount(rawMessage))
                .currency(extractMtCurrency(rawMessage))
                .sourceAccount(extractMtField(rawMessage, "50K"))
                .destinationAccount(extractMtField(rawMessage, "59"))
                .rawPayload(rawMessage)
                .build();

        return PegaRequest.builder()
                .caseTypeID("BANK-TRANSACTION-WORK-MT")
                .content(content)
                .build();
    }

    // ── MX parser (ISO 20022 XML) ────────────────────────────────────────────

    /**
     * Parses ISO 20022 XML messages.
     * Uses simple string extraction — replace with a proper JAXB parser
     * (e.g. pacs.008, pain.001) for production use.
     *
     * To add JAXB support:
     *   1. Add com.sun.xml.bind:jaxb-impl to pom.xml
     *   2. Generate classes from ISO 20022 XSD schema files
     *   3. Use JAXBContext.newInstance(FIToFICustomerCreditTransferV08.class)
     */
    private PegaRequest parseMxMessage(String rawMessage) {
        log.debug("Parsing MX (ISO 20022) message");

        PegaRequest.PegaContent content = PegaRequest.PegaContent.builder()
                .transactionId(extractXmlValue(rawMessage, "MsgId"))
                .transactionType(detectMxType(rawMessage))
                .amount(extractXmlValue(rawMessage, "InstdAmt"))
                .currency(extractXmlAttribute(rawMessage, "InstdAmt", "Ccy"))
                .sourceAccount(extractXmlValue(rawMessage, "DbtrAcct"))
                .destinationAccount(extractXmlValue(rawMessage, "CdtrAcct"))
                .rawPayload(rawMessage)
                .build();

        return PegaRequest.builder()
                .caseTypeID("BANK-TRANSACTION-WORK-MX")
                .content(content)
                .build();
    }

    // ── FED parser (Fedwire tag-value) ───────────────────────────────────────

    /**
     * Parses Fedwire format.
     * Fedwire uses numeric tags wrapped in braces: {1500}, {2000}, {3400}, etc.
     *
     * Key tags:
     *   {1510} — Type / sub-type code
     *   {2000} — Amount
     *   {3100} — Sending bank ABA + name
     *   {3400} — Receiving bank ABA + name
     *   {3600} — Business function code
     *   {4200} — Beneficiary account / name
     *   {5000} — Originator account / name
     */
    private PegaRequest parseFedMessage(String rawMessage) {
        log.debug("Parsing FED (Fedwire) message");

        PegaRequest.PegaContent content = PegaRequest.PegaContent.builder()
                .transactionId(extractFedTag(rawMessage, "1520"))   // IMAD
                .transactionType("FEDWIRE")
                .amount(extractFedAmount(rawMessage))
                .currency("USD")   // Fedwire is always USD
                .sourceAccount(extractFedTag(rawMessage, "5000"))
                .destinationAccount(extractFedTag(rawMessage, "4200"))
                .rawPayload(rawMessage)
                .build();

        return PegaRequest.builder()
                .caseTypeID("BANK-TRANSACTION-WORK-FED")
                .content(content)
                .build();
    }

    // ── Outbound serialisation ───────────────────────────────────────────────

    public String toMqPayload(com.bank.agent.model.OutboundRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize outbound request", e);
        }
    }

    // ── MT helpers ───────────────────────────────────────────────────────────

    private String extractMtField(String msg, String tag) {
        Pattern p = Pattern.compile(":" + tag + ":([^\r\n:]+)");
        Matcher m = p.matcher(msg);
        return m.find() ? m.group(1).trim() : null;
    }

    private String extractMtAmount(String msg) {
        // :32A: = YYMMDD + currency (3 chars) + amount
        Pattern p = Pattern.compile(":32A:\\d{6}([A-Z]{3})([\\d,]+)");
        Matcher m = p.matcher(msg);
        return m.find() ? m.group(2).replace(",", ".") : null;
    }

    private String extractMtCurrency(String msg) {
        Pattern p = Pattern.compile(":32A:\\d{6}([A-Z]{3})");
        Matcher m = p.matcher(msg);
        return m.find() ? m.group(1) : null;
    }

    private String detectMtType(String msg) {
        // Block 2 header contains the MT type number
        Pattern p = Pattern.compile("\\{2:[IO](\\d{3})");
        Matcher m = p.matcher(msg);
        return m.find() ? "MT" + m.group(1) : "MT_UNKNOWN";
    }

    // ── MX helpers ───────────────────────────────────────────────────────────

    private String extractXmlValue(String xml, String tag) {
        Pattern p = Pattern.compile("<" + tag + "[^>]*>([^<]+)</" + tag + ">");
        Matcher m = p.matcher(xml);
        return m.find() ? m.group(1).trim() : null;
    }

    private String extractXmlAttribute(String xml, String tag, String attr) {
        Pattern p = Pattern.compile("<" + tag + "[^>]*" + attr + "=\"([^\"]+)\"");
        Matcher m = p.matcher(xml);
        return m.find() ? m.group(1) : null;
    }

    private String detectMxType(String xml) {
        // Root element name identifies the ISO 20022 message type
        Pattern p = Pattern.compile("<Document xmlns=\"[^\"]*:(\\w+\\.\\w+\\.\\w+\\.\\w+)\"");
        Matcher m = p.matcher(xml);
        return m.find() ? m.group(1) : "MX_UNKNOWN";
    }

    // ── FED helpers ──────────────────────────────────────────────────────────

    private String extractFedTag(String msg, String tag) {
        Pattern p = Pattern.compile("\\{" + tag + "\\}([^\r\n{]+)");
        Matcher m = p.matcher(msg);
        return m.find() ? m.group(1).trim() : null;
    }

    private String extractFedAmount(String msg) {
        // {2000} amount is in cents, e.g. {2000}000000010000 = $100.00
        String raw = extractFedTag(msg, "2000");
        if (raw == null || raw.length() < 2) return null;
        try {
            long cents = Long.parseLong(raw.replaceAll("\\D", ""));
            return String.format("%.2f", cents / 100.0);
        } catch (NumberFormatException e) {
            return raw;
        }
    }
}
