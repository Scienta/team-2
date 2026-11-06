package no.scienta.cfp;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import no.scienta.cfp.Venue.Place;
import no.scienta.cfp.Venue.Slot;

/**
 * Decides whether a talk may go into a place on the programme, from facts read under the locks. Pure: no database, no
 * Spring. The checks run in the order of decision D13 in workflow/specs/scheduling.md.
 */
public final class SchedulingRules {

    static final String NOT_ACCEPTED = "only accepted talks go on the programme";
    static final String WRONG_SLOT = "a workshop needs the long slot, S4";
    static final String ELSEWHERE = "the talk is already on the programme; take it out first";
    static final String TAKEN = "this room and slot already has a talk";
    static final String CLASH = "a speaker of this talk is in another room in this slot";

    /**
     * A talk as the rules see it. {@code people} are the main speaker and the co-speakers.
     */
    public record Candidate(long id, String status, String format, Set<Long> people) {

        public static Candidate of(TalkDto talk) {
            Set<Long> people = new HashSet<>(talk.coSpeakerIds);
            people.add(talk.speakerId);
            return new Candidate(talk.id, talk.status, talk.format, Set.copyOf(people));
        }
    }

    public sealed interface Outcome permits Ok, AlreadyHere, Violation {
    }

    public record Ok() implements Outcome {
    }

    public record AlreadyHere() implements Outcome {
    }

    public record Violation(String reason) implements Outcome {
    }

    private SchedulingRules() {
    }

    /**
     * @param talkIsAt     where the talk is on the programme now, if anywhere
     * @param targetTaken  whether the target place holds a talk
     * @param othersInSlot the talks in the target's slot in the other rooms
     */
    public static Outcome decide(Candidate talk, Place target, Optional<Place> talkIsAt, boolean targetTaken,
                                 List<Candidate> othersInSlot) {
        if (!talk.status().equals("ACCEPTED")) {
            return new Violation(NOT_ACCEPTED);
        }
        if (talk.format().equals("WORKSHOP") && target.slot() != Slot.S4) {
            return new Violation(WRONG_SLOT);
        }
        if (talkIsAt.filter(target::equals).isPresent()) {
            return new AlreadyHere();
        }
        if (talkIsAt.isPresent()) {
            return new Violation(ELSEWHERE);
        }
        if (targetTaken) {
            return new Violation(TAKEN);
        }
        if (othersInSlot.stream().anyMatch(other -> !Collections.disjoint(talk.people(), other.people()))) {
            return new Violation(CLASH);
        }
        return new Ok();
    }
}
