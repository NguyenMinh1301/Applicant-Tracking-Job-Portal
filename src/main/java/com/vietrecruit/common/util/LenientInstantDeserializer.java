package com.vietrecruit.common.util;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

/**
 * Lenient deserializer for Instant fields that accepts multiple ISO 8601 formats.
 *
 * <p>Accepts:
 *
 * <ul>
 *   <li>Full ISO 8601 with offset: {@code 2026-04-11T11:04:00Z} or {@code
 *       2026-04-11T11:04:00+07:00}
 *   <li>Shortened format without seconds: {@code 2026-04-11T11:04} (assumes Asia/Ho_Chi_Minh
 *       timezone)
 * </ul>
 *
 * <p>Rejects any other format with a clear error message.
 */
public class LenientInstantDeserializer extends JsonDeserializer<Instant> {

    private static final DateTimeFormatter SHORT_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Override
    public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String text = p.getText();
        if (text == null || text.isBlank()) {
            return null;
        }

        text = text.trim();

        // Attempt full ISO 8601 parse first (with offset)
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException ignored) {
            // Fall through to lenient parse
        }

        // Attempt lenient parse: yyyy-MM-dd'T'HH:mm (no seconds, no offset)
        if (text.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}")) {
            try {
                LocalDateTime ldt = LocalDateTime.parse(text, SHORT_FORMAT);
                return ldt.atZone(DEFAULT_ZONE).toInstant();
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException(
                        String.format(
                                "Invalid date/time format: '%s'. Expected ISO-8601 format (e.g.,"
                                        + " '2026-04-11T11:04:00Z') or shortened format (e.g.,"
                                        + " '2026-04-11T11:04')",
                                text),
                        e);
            }
        }

        throw new IllegalArgumentException(
                String.format(
                        "Invalid date/time format: '%s'. Expected ISO-8601 format (e.g.,"
                                + " '2026-04-11T11:04:00Z') or shortened format (e.g.,"
                                + " '2026-04-11T11:04')",
                        text));
    }
}
