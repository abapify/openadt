package org.openadt.sap.adt.fallback.http;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SapLogonTicketValidityTest {
    @Test
    void detectsExpiredValidityField() throws Exception {
        String ticket = ticketWithValidity("20200101000000");
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        assertTrue(SapLogonTicketValidity.isExpired(ticket, clock));
    }

    @Test
    void acceptsFutureValidityField() throws Exception {
        String ticket = ticketWithValidity("20991231235959");
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        assertFalse(SapLogonTicketValidity.isExpired(ticket, clock));
    }

    @Test
    void unknownFormatIsNotTreatedAsExpired() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        assertFalse(SapLogonTicketValidity.isExpired("not-a-sap-ticket", clock));
    }

    static String ticketWithValidity(String yyyymmddhhmmss) throws Exception {
        byte[] validityValue = yyyymmddhhmmss.getBytes(StandardCharsets.UTF_16LE);
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.write(2);
        payload.write("4103".getBytes(StandardCharsets.US_ASCII));
        payload.write(4);
        payload.write((validityValue.length >> 8) & 0xFF);
        payload.write(validityValue.length & 0xFF);
        payload.write(validityValue);
        payload.write(0xFF);
        payload.write(0);
        payload.write(0);
        return Base64.getEncoder().encodeToString(payload.toByteArray());
    }
}
