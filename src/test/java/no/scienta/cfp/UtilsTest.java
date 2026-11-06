package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.TimeZone;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class UtilsTest {

    @Test
    void isEmpty() {
        assertTrue(Utils.isEmpty(null));
        assertTrue(Utils.isEmpty(""));
        assertTrue(Utils.isEmpty("   "));
        assertFalse(Utils.isEmpty("a"));
    }

    @Test
    void isNumeric() {
        assertTrue(Utils.isNumeric("123"));
        assertFalse(Utils.isNumeric("12a"));
        assertFalse(Utils.isNumeric(""));
    }

    @Test
    void capitalize() {
        assertEquals("Workshop", Utils.capitalize("WORKSHOP"));
    }

    // Averages are shown with one decimal, cut off (CFP-61)
    @Test
    void roundsRatingToOneDecimal() {
        assertEquals(4.2, Utils.roundRating(4.25));
        assertEquals(3.6, Utils.roundRating(3.6666));
        assertEquals(4.5, Utils.roundRating(4.5));
    }

    @Test
    void usShortIsTheReviewerUiFormatInUtc() {
        assertEquals("11/4/26, 1:05 PM", usShort("2026-11-04T13:05:00Z"));
        assertEquals("11/4/26, 12:00 AM", usShort("2026-11-04T00:00:00Z"));
        assertEquals("11/4/26, 12:00 PM", usShort("2026-11-04T12:00:00Z"));
        assertEquals("1/9/26, 9:07 AM", usShort("2026-01-09T09:07:00Z"));
    }

    // D2: newer JDKs put U+202F before AM/PM; the spec wants one ordinary space
    @Test
    void usShortUsesAnOrdinarySpaceBeforeAmPm() {
        String s = usShort("2026-11-04T13:05:00Z");
        assertEquals(' ', s.charAt(s.length() - 3));
    }

    // the committee spreadsheet's date format; pinned so a JDK upgrade (new CLDR data) can't change it unnoticed
    @Test
    void norwegianDateIsMediumNbNoInOsloTime() {
        assertEquals("4. nov. 2026", Utils.norwegianDate(Date.from(Instant.parse("2026-11-04T12:00:00Z"))));
        assertEquals("1. jan. 2027", Utils.norwegianDate(Date.from(Instant.parse("2026-12-31T23:30:00Z"))));
    }

    private static String usShort(String instant) {
        return Utils.usShort(Date.from(Instant.parse(instant)));
    }

    @Test
    void isWellFormedEmail() {
        assertTrue(Utils.isWellFormedEmail("x-0808034cb556@example.test"));
        assertTrue(Utils.isWellFormedEmail("ada.lovelace+cfp@mail.example.co.uk"));
        assertTrue(Utils.isWellFormedEmail(" \u00a0ADA@Example.com\t"));
        assertFalse(Utils.isWellFormedEmail(null));
        assertFalse(Utils.isWellFormedEmail(""));
        assertFalse(Utils.isWellFormedEmail("@example.com"));
        assertFalse(Utils.isWellFormedEmail("ada@localhost"));
        assertFalse(Utils.isWellFormedEmail("a b@c.d"));
        assertFalse(Utils.isWellFormedEmail("a@b@c.d"));
    }

    @Test
    void trimSpaces() {
        assertEquals("Ada", Utils.trimSpaces(" \u00a0\tAda\u2003\n"));
        assertEquals("", Utils.trimSpaces("\u00a0"));
    }

    @Disabled("flaky on Jenkins")
    @Test
    void nowIsCurrentTime() {
        SimpleDateFormat f = new SimpleDateFormat(Utils.DB_DATE_TIME);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        assertEquals(f.format(new Date()), Utils.now());
    }
}
