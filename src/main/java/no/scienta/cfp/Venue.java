package no.scienta.cfp;

import java.util.Arrays;
import java.util.Optional;

/**
 * The fixed venue of the programme (workflow/specs/scheduling.md): three rooms and the same five slots in every room.
 * Names are matched exactly, so an unknown or lower-case name is no room or slot at all.
 */
public final class Venue {

    public enum Room { R1, R2, R3 }

    public enum Slot {
        S1("09:00", "10:00"),
        S2("10:15", "11:15"),
        S3("11:30", "12:30"),
        S4("13:30", "15:30"),
        S5("15:45", "16:45");

        private final String start;
        private final String end;

        Slot(String start, String end) {
            this.start = start;
            this.end = end;
        }

        public String start() {
            return start;
        }

        public String end() {
            return end;
        }
    }

    public record Place(Room room, Slot slot) {
    }

    private Venue() {
    }

    public static Optional<Room> room(String name) {
        return Arrays.stream(Room.values()).filter(room -> room.name().equals(name)).findFirst();
    }

    public static Optional<Slot> slot(String name) {
        return Arrays.stream(Slot.values()).filter(slot -> slot.name().equals(name)).findFirst();
    }
}
