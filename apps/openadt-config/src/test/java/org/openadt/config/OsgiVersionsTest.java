package org.openadt.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsgiVersionsTest {
    @Test
    void comparesMicroSegmentNumericallyNotLexicographically() {
        // "3.24.0" vs "3.24.200": string comparison would rank "3.24.0" higher because '2' > '0'.
        assertTrue(OsgiVersions.compare("3.24.200.v20260515-1403", "3.24.0.v20251126-0427") > 0);
    }

    @Test
    void comparesMinorSegment() {
        assertTrue(OsgiVersions.compare("3.24.0.v20260518-1150", "3.23.100.v20251106-1705") > 0);
    }

    @Test
    void comparesMajorSegment() {
        assertTrue(OsgiVersions.compare("4.0.0", "3.99.99") > 0);
    }

    @Test
    void treatsMissingSegmentsAsZero() {
        assertEquals(0, OsgiVersions.compare("3.1", "3.1.0"));
        assertTrue(OsgiVersions.compare("3.1.1", "3.1") > 0);
    }

    @Test
    void usesQualifierOnlyAsTiebreaker() {
        assertTrue(OsgiVersions.compare("3.34.200.v20251220-0953", "3.34.200.v20251111-1421") > 0);
        assertEquals(0, OsgiVersions.compare("3.34.200.v1", "3.34.200.v1"));
    }

    @Test
    void qualifierDoesNotOutrankNumericSegments() {
        assertTrue(OsgiVersions.compare("3.58.0", "3.56.0.v99999999") > 0);
    }

    @Test
    void handlesPlainThreePartVersions() {
        assertTrue(OsgiVersions.compare("3.1.13", "3.1.12") > 0);
        assertTrue(OsgiVersions.compare("1.32.0", "1.31.0") > 0);
    }

    @Test
    void handlesNullAndBlank() {
        assertEquals(0, OsgiVersions.compare(null, null));
        assertEquals(0, OsgiVersions.compare(null, ""));
        assertTrue(OsgiVersions.compare("1.0.0", null) > 0);
    }

    @Test
    void ignoresNonNumericNumericSegments() {
        // Malformed input must not throw; it simply sorts as zero.
        assertEquals(0, OsgiVersions.compare("x.y.z", "0.0.0"));
    }
}
