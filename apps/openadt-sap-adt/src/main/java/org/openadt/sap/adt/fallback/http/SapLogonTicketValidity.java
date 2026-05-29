package org.openadt.sap.adt.fallback.http;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Optional;

/**
 * Reads SAP logon ticket validity (TLV field {@code 0x04}, {@code YYYYMMDDHHmmss} UTF-16LE) without signature verification.
 */
final class SapLogonTicketValidity {
    private static final int TAG_VALIDITY = 0x04;
    private static final int HEADER_LENGTH = 5;
    private static final DateTimeFormatter VALIDITY_FORMAT =
        DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private SapLogonTicketValidity() {
    }

    static boolean isExpired(String rawTicket, Clock clock) {
        return expiresAt(rawTicket)
            .map(expiry -> !expiry.isAfter(clock.instant()))
            .orElse(false);
    }

    static Optional<Instant> expiresAt(String rawTicket) {
        byte[] payload = decodePayload(rawTicket);
        if (payload == null || payload.length < HEADER_LENGTH + 3) {
            return Optional.empty();
        }
        int offset = HEADER_LENGTH;
        while (offset + 3 <= payload.length) {
            int tag = payload[offset] & 0xFF;
            if (tag == 0xFF) {
                break;
            }
            int length = ((payload[offset + 1] & 0xFF) << 8) | (payload[offset + 2] & 0xFF);
            offset += 3;
            if (length < 0 || offset + length > payload.length) {
                break;
            }
            if (tag == TAG_VALIDITY) {
                return parseValidity(payload, offset, length);
            }
            offset += length;
        }
        return Optional.empty();
    }

    private static Optional<Instant> parseValidity(byte[] payload, int offset, int length) {
        if (length < 2 || (length & 1) != 0) {
            return Optional.empty();
        }
        String text = new String(payload, offset, length, StandardCharsets.UTF_16LE).trim();
        if (text.isEmpty()) {
            return Optional.empty();
        }
        try {
            LocalDateTime local = LocalDateTime.parse(text, VALIDITY_FORMAT);
            return Optional.of(local.toInstant(ZoneOffset.UTC));
        } catch (DateTimeParseException ignored) {
            return Optional.empty();
        }
    }

    private static byte[] decodePayload(String rawTicket) {
        if (rawTicket == null || rawTicket.isBlank()) {
            return null;
        }
        String value = rawTicket.trim();
        if (value.startsWith("MYSAPSSO2=")) {
            value = value.substring("MYSAPSSO2=".length()).trim();
        }
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
