package no.scienta.cfp;

import java.util.List;
import java.util.stream.Collectors;

/**
 * RFC 4180 CSV for the print shop: comma separated, fields quoted only when they have to be, every line ending in a
 * Unix newline.
 */
final class ProgrammeCsv {

    static final String HEADER = "slot,start,end,room,talkId,title,format,speakers";

    private ProgrammeCsv() {
    }

    static String field(String s) {
        if (s == null) {
            return "";
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\r") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    static String line(List<String> fields) {
        return fields.stream().map(ProgrammeCsv::field).collect(Collectors.joining(",")) + "\n";
    }
}
