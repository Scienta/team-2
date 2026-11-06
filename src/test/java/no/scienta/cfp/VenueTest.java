package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import no.scienta.cfp.Venue.Room;
import no.scienta.cfp.Venue.Slot;

class VenueTest {

    @ParameterizedTest
    @CsvSource({
            "S1, 09:00, 10:00",
            "S2, 10:15, 11:15",
            "S3, 11:30, 12:30",
            "S4, 13:30, 15:30",
            "S5, 15:45, 16:45"
    })
    void slotsHaveTheTimesOnTheSigns(String name, String start, String end) {
        Slot slot = Venue.slot(name).orElseThrow();
        assertEquals(start, slot.start());
        assertEquals(end, slot.end());
    }

    @ParameterizedTest
    @ValueSource(strings = {"R1", "R2", "R3"})
    void knownRooms(String name) {
        assertEquals(Optional.of(Room.valueOf(name)), Venue.room(name));
    }

    @ParameterizedTest
    @ValueSource(strings = {"r1", "R4", "R0", "S1", " R1", ""})
    void unknownRoomsAreEmpty(String name) {
        assertEquals(Optional.empty(), Venue.room(name));
    }

    @ParameterizedTest
    @ValueSource(strings = {"s1", "S0", "S6", "R1", "S1 ", ""})
    void unknownSlotsAreEmpty(String name) {
        assertEquals(Optional.empty(), Venue.slot(name));
    }
}
