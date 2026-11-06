package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class ProgrammeCsvTest {

    @Test
    void plainValuesAreWrittenAsTheyAre() {
        assertEquals("Kotlin", ProgrammeCsv.field("Kotlin"));
        assertEquals("  leading spaces", ProgrammeCsv.field("  leading spaces"));
        assertEquals("Ærlig talt", ProgrammeCsv.field("Ærlig talt"));
    }

    @Test
    void emptyAndNullAreEmptyFields() {
        assertEquals("", ProgrammeCsv.field(""));
        assertEquals("", ProgrammeCsv.field(null));
    }

    @Test
    void commaIsQuoted() {
        assertEquals("\"Kotlin, really fast\"", ProgrammeCsv.field("Kotlin, really fast"));
    }

    @Test
    void quotesAreDoubled() {
        assertEquals("\"Kotlin, \"\"really\"\" fast\"", ProgrammeCsv.field("Kotlin, \"really\" fast"));
        assertEquals("\"Say \"\"hi\"\"\"", ProgrammeCsv.field("Say \"hi\""));
    }

    @Test
    void lineBreaksAreQuotedAndKept() {
        assertEquals("\"a\nb\"", ProgrammeCsv.field("a\nb"));
        assertEquals("\"a\r\nb\"", ProgrammeCsv.field("a\r\nb"));
        assertEquals("\"a\rb\"", ProgrammeCsv.field("a\rb"));
    }

    @Test
    void lineJoinsWithCommasAndEndsWithOneNewline() {
        assertEquals("S1,09:00,10:00,R1,7,Kotlin,PRESENTATION,\"Grace Hopper; Lovelace, Ada\"\n",
                ProgrammeCsv.line(Arrays.asList("S1", "09:00", "10:00", "R1", "7", "Kotlin", "PRESENTATION",
                        "Grace Hopper; Lovelace, Ada")));
    }

    @Test
    void headerIsExactlyAsSpecified() {
        assertEquals("slot,start,end,room,talkId,title,format,speakers", ProgrammeCsv.HEADER);
    }
}
