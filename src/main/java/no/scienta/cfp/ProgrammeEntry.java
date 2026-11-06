package no.scienta.cfp;

import no.scienta.cfp.Venue.Place;

/**
 * A talk in a room and slot, as the API returns it.
 */
public record ProgrammeEntry(String room, String slot, String start, String end, long talkId) {

    public static ProgrammeEntry of(Place place, long talkId) {
        return new ProgrammeEntry(place.room().name(), place.slot().name(), place.slot().start(), place.slot().end(),
                talkId);
    }
}
