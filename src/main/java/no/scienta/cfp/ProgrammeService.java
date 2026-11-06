package no.scienta.cfp;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import no.scienta.cfp.SchedulingRules.AlreadyHere;
import no.scienta.cfp.SchedulingRules.Candidate;
import no.scienta.cfp.SchedulingRules.Ok;
import no.scienta.cfp.SchedulingRules.Outcome;
import no.scienta.cfp.SchedulingRules.Violation;
import no.scienta.cfp.Venue.Place;

/**
 * Puts talks on the programme and looks up what's in a room and slot (workflow/specs/scheduling.md). Locks the talk,
 * then the slot, reads the facts under the locks and leaves the decision to {@link SchedulingRules}.
 */
@Service
public class ProgrammeService {

    private final ProgrammeDao programmeDao;
    private final TalkDao talkDao;

    public ProgrammeService(ProgrammeDao programmeDao, TalkDao talkDao) {
        this.programmeDao = programmeDao;
        this.talkDao = talkDao;
    }

    @Transactional
    public ProgrammeEntry schedule(String room, String slot, Map<String, Object> body) {
        Place target = place(room, slot);
        long talkId = talkId(body);
        if (!talkDao.lock(talkId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "talk not found");
        }
        programmeDao.lockSlot(target.slot());
        Outcome outcome = SchedulingRules.decide(
                Candidate.of(talkDao.findById(talkId)),
                target,
                programmeDao.findByTalk(talkId),
                programmeDao.find(target.room(), target.slot()).isPresent(),
                othersInSlot(target));
        return switch (outcome) {
            case Ok ok -> {
                programmeDao.insert(target.room(), target.slot(), talkId);
                yield ProgrammeEntry.of(target, talkId);
            }
            case AlreadyHere alreadyHere -> ProgrammeEntry.of(target, talkId);
            case Violation violation -> throw new ResponseStatusException(HttpStatus.CONFLICT, violation.reason());
        };
    }

    public ProgrammeEntry get(String room, String slot) {
        Place place = place(room, slot);
        return programmeDao.find(place.room(), place.slot())
                .map(talkId -> ProgrammeEntry.of(place, talkId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "nothing in this room and slot"));
    }

    private List<Candidate> othersInSlot(Place target) {
        return programmeDao.talksInSlot(target.slot(), target.room()).stream()
                .map(talkDao::findById)
                .map(Candidate::of)
                .toList();
    }

    private static Place place(String room, String slot) {
        return Venue.room(room)
                .flatMap(r -> Venue.slot(slot).map(s -> new Place(r, s)))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no such room and slot"));
    }

    private static long talkId(Map<String, Object> body) {
        Object raw = body.get("talkId");
        Optional<Long> talkId = raw instanceof Integer || raw instanceof Long
                ? Optional.of(((Number) raw).longValue()).filter(id -> id >= 1)
                : Optional.empty();
        return talkId.orElseThrow(
                () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "talkId must be a positive whole number"));
    }
}
