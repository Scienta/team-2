package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import no.scienta.cfp.SchedulingRules.AlreadyHere;
import no.scienta.cfp.SchedulingRules.Candidate;
import no.scienta.cfp.SchedulingRules.Ok;
import no.scienta.cfp.SchedulingRules.Outcome;
import no.scienta.cfp.SchedulingRules.Violation;
import no.scienta.cfp.Venue.Place;
import no.scienta.cfp.Venue.Room;
import no.scienta.cfp.Venue.Slot;

/**
 * The scheduling rules and their order, D5 to D10 and D13 in workflow/specs/scheduling.md.
 */
class SchedulingRulesTest {

    private static final Place R1_S1 = new Place(Room.R1, Slot.S1);
    private static final Place R2_S1 = new Place(Room.R2, Slot.S1);

    @Test
    void anAcceptedTalkGoesIntoAFreePlace() {
        assertEquals(new Ok(), decide(talk(1, "ACCEPTED", "PRESENTATION", 10), R1_S1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUBMITTED", "WAITLISTED", "REJECTED", "WITHDRAWN"})
    void onlyAcceptedTalks(String status) {
        assertEquals(new Violation(SchedulingRules.NOT_ACCEPTED), decide(talk(1, status, "PRESENTATION", 10), R1_S1));
    }

    @ParameterizedTest
    @CsvSource({
            "WORKSHOP, S1, false",
            "WORKSHOP, S2, false",
            "WORKSHOP, S3, false",
            "WORKSHOP, S4, true",
            "WORKSHOP, S5, false",
            "PRESENTATION, S4, true",
            "LIGHTNING_10, S4, true",
            "LIGHTNING_20, S5, true"
    })
    void aWorkshopNeedsTheLongSlot(String format, Slot slot, boolean fits) {
        Outcome outcome = decide(talk(1, "ACCEPTED", format, 10), new Place(Room.R1, slot));
        assertEquals(fits ? new Ok() : new Violation(SchedulingRules.WRONG_SLOT), outcome);
    }

    @Test
    void theSamePlaceAgainChangesNothing() {
        Candidate talk = talk(1, "ACCEPTED", "PRESENTATION", 10);
        assertEquals(new AlreadyHere(), SchedulingRules.decide(talk, R1_S1, Optional.of(R1_S1), true, List.of()));
    }

    @Test
    void aTalkIsOnTheProgrammeOnlyOnce() {
        Candidate talk = talk(1, "ACCEPTED", "PRESENTATION", 10);
        Place elsewhere = new Place(Room.R3, Slot.S5);
        Outcome outcome = SchedulingRules.decide(talk, R1_S1, Optional.of(elsewhere), false, List.of());
        assertEquals(new Violation(SchedulingRules.ELSEWHERE), outcome);
    }

    @Test
    void onePlaceHoldsOneTalk() {
        Candidate talk = talk(1, "ACCEPTED", "PRESENTATION", 10);
        Outcome outcome = SchedulingRules.decide(talk, R1_S1, Optional.empty(), true, List.of());
        assertEquals(new Violation(SchedulingRules.TAKEN), outcome);
    }

    @ParameterizedTest
    @CsvSource({
            "10, '',   10, '',   main speaker in both talks",
            "10, '',   20, 10,   main speaker is a co-speaker of the other",
            "10, 30,   30, '',   co-speaker is the other's main speaker",
            "10, 30,   20, 30,   co-speaker in both talks"
    })
    void nobodySpeaksInTwoRoomsAtOnce(long main, String co, long otherMain, String otherCo, String scenario) {
        Candidate talk = talk(1, "ACCEPTED", "PRESENTATION", main, co);
        Candidate other = talk(2, "ACCEPTED", "PRESENTATION", otherMain, otherCo);
        Outcome outcome = SchedulingRules.decide(talk, R1_S1, Optional.empty(), false, List.of(other));
        assertEquals(new Violation(SchedulingRules.CLASH), outcome, scenario);
    }

    @Test
    void differentPeopleInTheSameSlotIsFine() {
        Candidate talk = talk(1, "ACCEPTED", "PRESENTATION", 10, "11");
        Candidate other = talk(2, "ACCEPTED", "PRESENTATION", 20, "21");
        assertEquals(new Ok(), SchedulingRules.decide(talk, R1_S1, Optional.empty(), false, List.of(other)));
    }

    @Test
    void aNotAcceptedTalkInItsOwnPlaceIsAViolation() {
        Candidate withdrawn = talk(1, "WITHDRAWN", "PRESENTATION", 10);
        Outcome outcome = SchedulingRules.decide(withdrawn, R1_S1, Optional.of(R1_S1), true, List.of());
        assertEquals(new Violation(SchedulingRules.NOT_ACCEPTED), outcome);
    }

    @Test
    void aWorkshopInItsOwnWrongPlaceIsAViolation() {
        Candidate workshop = talk(1, "ACCEPTED", "WORKSHOP", 10);
        Outcome outcome = SchedulingRules.decide(workshop, R1_S1, Optional.of(R1_S1), true, List.of());
        assertEquals(new Violation(SchedulingRules.WRONG_SLOT), outcome);
    }

    @Test
    void notAcceptedComesBeforeTheWrongSlot() {
        assertEquals(new Violation(SchedulingRules.NOT_ACCEPTED), decide(talk(1, "SUBMITTED", "WORKSHOP", 10), R1_S1));
    }

    @Test
    void elsewhereComesBeforeTakenAndClash() {
        Candidate talk = talk(1, "ACCEPTED", "PRESENTATION", 10);
        Candidate other = talk(2, "ACCEPTED", "PRESENTATION", 10);
        Place elsewhere = new Place(Room.R1, Slot.S2);
        Outcome outcome = SchedulingRules.decide(talk, R1_S1, Optional.of(elsewhere), true, List.of(other));
        assertEquals(new Violation(SchedulingRules.ELSEWHERE), outcome);
    }

    @Test
    void takenComesBeforeClash() {
        Candidate talk = talk(1, "ACCEPTED", "PRESENTATION", 10);
        Candidate other = talk(2, "ACCEPTED", "PRESENTATION", 10);
        Outcome outcome = SchedulingRules.decide(talk, R1_S1, Optional.empty(), true, List.of(other));
        assertEquals(new Violation(SchedulingRules.TAKEN), outcome);
    }

    @Test
    void candidateFromATalkHasItsMainSpeakerAndCoSpeakers() {
        TalkDto dto = new TalkDto();
        dto.id = 7L;
        dto.status = "ACCEPTED";
        dto.format = "WORKSHOP";
        dto.speakerId = 10L;
        dto.coSpeakerIds.addAll(List.of(11L, 12L));
        assertEquals(new Candidate(7, "ACCEPTED", "WORKSHOP", Set.of(10L, 11L, 12L)), Candidate.of(dto));
    }

    @Test
    void anotherRoomInTheSameSlotDoesNotMakeThePlaceTaken() {
        Candidate talk = talk(1, "ACCEPTED", "PRESENTATION", 10);
        Candidate inR1 = talk(2, "ACCEPTED", "PRESENTATION", 20);
        assertEquals(new Ok(), SchedulingRules.decide(talk, R2_S1, Optional.empty(), false, List.of(inR1)));
    }

    private static Outcome decide(Candidate talk, Place target) {
        return SchedulingRules.decide(talk, target, Optional.empty(), false, List.of());
    }

    private static Candidate talk(long id, String status, String format, long mainSpeaker) {
        return talk(id, status, format, mainSpeaker, "");
    }

    private static Candidate talk(long id, String status, String format, long mainSpeaker, String coSpeaker) {
        Set<Long> people = coSpeaker.isEmpty() ? Set.of(mainSpeaker) : Set.of(mainSpeaker, Long.parseLong(coSpeaker));
        return new Candidate(id, status, format, people);
    }
}
