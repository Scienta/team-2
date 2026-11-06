package no.scienta.cfp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Speakers, talks and ratings.
 *
 * TODO split this up (CFP-140)
 */
@Service
public class ConferenceService {

    private static final Logger log = LoggerFactory.getLogger(ConferenceService.class);

    private static final int MAX_CO_SPEAKERS = 2;

    private static final Map<String, Integer> DURATIONS = new HashMap<>();

    static {
        DURATIONS.put("LIGHTNING_10", 10);
        DURATIONS.put("LIGHTNING_20", 20);
        DURATIONS.put("PRESENTATION", 45);
        DURATIONS.put("WORKSHOP", 120);
        // DURATIONS.put("KEYNOTE", 60);  // keynotes are invited, not submitted (2021)
    }

    private final SpeakerDao speakerDao;
    private final TalkDao talkDao;
    private final RatingDao ratingDao;
    private final NotificationService notifications;

    public ConferenceService(SpeakerDao speakerDao, TalkDao talkDao, RatingDao ratingDao,
                             NotificationService notifications) {
        this.speakerDao = speakerDao;
        this.talkDao = talkDao;
        this.ratingDao = ratingDao;
        this.notifications = notifications;
    }

    // ---------------------------------------------------------------- speakers

    @Transactional
    public Map<String, Object> registerSpeaker(SpeakerForm form) {
        requireSpeakerNameAndEmail(form.getName(), form.getEmail());
        if (speakerDao.emailTaken(form.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "email already registered");
        }
        long id;
        try {
            id = speakerDao.insert(form.getName(), form.getEmail(), form.getBio());
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "email already registered");
        }
        log.info("registered speaker " + id + " (" + form.getEmail() + ")");
        notifications.speakerRegistered(form.getEmail(), form.getName());
        Map<String, Object> speaker = new LinkedHashMap<>();
        speaker.put("id", id);
        speaker.put("name", form.getName());
        speaker.put("email", form.getEmail());
        speaker.put("bio", form.getBio());
        return speaker;
    }

    public Map<String, Object> getSpeaker(long id) {
        Map<String, Object> speaker = speakerDao.findById(id);
        if (speaker == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "speaker not found");
        }
        return speaker;
    }

    @Transactional
    public Map<String, Object> updateSpeaker(long id, String name, String email, String bio) {
        Map<String, Object> existing = getSpeaker(id);
        requireSpeakerNameAndEmail(name, email);
        if (bio != null && bio.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bio too long");
        }
        if (!email.equalsIgnoreCase((String) existing.get("email")) && speakerDao.emailTaken(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "email already registered");
        }
        speakerDao.update(id, name, email, bio);
        return getSpeaker(id);
    }

    // workflow/specs/speakers.md D1, D3, D4: a real name and a real e-mail, on registration and on update
    private static void requireSpeakerNameAndEmail(String name, String email) {
        if (name == null || Utils.trimSpaces(name).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        if (!Utils.isWellFormedEmail(email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid email");
        }
    }

    public List<Map<String, Object>> searchSpeakers(String q) {
        return speakerDao.search(q);
    }

    // ---------------------------------------------------------------- talks

    @Transactional
    public TalkDto submitTalk(TalkForm form) {
        if (form.title == null || form.title.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title is required");
        }
        if (form.format == null || !DURATIONS.containsKey(form.format)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "format must be one of " + DURATIONS.keySet());
        }
        if (form.level == null || !Arrays.asList("BEGINNER", "INTERMEDIATE", "ADVANCED").contains(form.level)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown level");
        }
        if (form.speakerId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "speakerId is required");
        }
        if (!speakerDao.exists(form.speakerId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "speaker " + form.speakerId + " does not exist");
        }

        int duration;
        switch (form.format) {
            case "LIGHTNING_10":
                duration = 10;
                break;
            case "LIGHTNING_20":
                duration = 20;
                break;
            case "WORKSHOP":
                duration = 120;
                break;
            default:
                duration = 45;
        }

        Long id = checkSubmissionLimit(form, duration);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "a speaker can submit at most 3 talks");
        }
        Map<String, Object> speaker = speakerDao.findById(form.speakerId);
        notifications.talkSubmitted((String) speaker.get("email"), form.title);
        return getTalk(id);
    }

    /**
     * Checks that the speaker is below the limit of 3 talks.
     */
    private Long checkSubmissionLimit(TalkForm form, int duration) {
        return talkDao.insertIfBelow(3, form.title, form.format, duration, form.level, form.speakerId,
                System.currentTimeMillis());
    }

    public TalkDto getTalk(long id) {
        TalkDto talk = talkDao.findById(id);
        if (talk == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "talk not found");
        }
        addRatings(talk);
        return talk;
    }

    public List<TalkDto> getTalksForSpeaker(long speakerId) {
        if (speakerDao.findById(speakerId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "speaker not found");
        }
        List<TalkDto> talks = talkDao.findBySpeaker(speakerId);
        addRatings(talks);
        return talks;
    }

    public List<TalkDto> listTalks(String sort, Long speakerId) {
        if (sort != null && !sort.equals("rating")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sort must be 'rating'");
        }
        List<TalkDto> talks = speakerId == null ? talkDao.findAll() : talkDao.findBySpeaker(speakerId);
        addRatings(talks);
        if ("rating".equals(sort)) {
            List<TalkDto> rated = new ArrayList<>();
            List<TalkDto> unrated = new ArrayList<>();
            for (TalkDto t : talks) {
                if (t.ratingCount > 0) {
                    rated.add(t);
                } else {
                    unrated.add(t);
                }
            }
            rated.sort((a, b) -> {
                int c = Double.compare(b.averageRating, a.averageRating);
                if (c != 0) {
                    return c;
                }
                c = Integer.compare(b.ratingCount, a.ratingCount);
                if (c != 0) {
                    return c;
                }
                return Long.compare(a.id, b.id);
            });
            rated.addAll(unrated);
            return rated;
        }
        return talks;
    }

    private void addRatings(List<TalkDto> talks) {
        List<Long> ids = new ArrayList<>();
        for (TalkDto t : talks) {
            ids.add(t.id);
        }
        Map<Long, Map<String, Object>> stats = ratingDao.statsFor(ids);
        for (TalkDto t : talks) {
            if (stats.containsKey(t.id)) {
                applyRatings(t, stats.get(t.id));
            }
        }
    }

    private void addRatings(TalkDto talk) {
        applyRatings(talk, ratingDao.stats(talk.id));
    }

    private void applyRatings(TalkDto talk, Map<String, Object> stats) {
        int count = ((Number) stats.get("cnt")).intValue();
        long total = ((Number) stats.get("total")).longValue();
        talk.ratingCount = count;
        talk.averageRating = count == 0 ? null : calculateAverage(total, count);
    }

    // 1 decimal, half up (4.25 -> 4.3)
    private Double calculateAverage(long total, int count) {
        return BigDecimal.valueOf(total).divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP).doubleValue();
        // return Utils.roundRating((double) total / count);
    }

    // ---------------------------------------------------------------- acceptance

    private static final List<String> DECISIONS = Arrays.asList("ACCEPTED", "REJECTED", "WAITLISTED");

    @Transactional
    public TalkDto decide(long talkId, Map<String, Object> body) {
        if (!talkDao.lock(talkId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "talk not found");
        }
        Object status = body.get("status");
        if (!(status instanceof String) || !DECISIONS.contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be one of " + DECISIONS);
        }
        TalkDto talk = getTalk(talkId);
        if (!allowed(talk.status, (String) status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "a " + talk.status + " talk can't become " + status);
        }
        if (status.equals("ACCEPTED") && talk.ratingCount < 2) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "a talk needs ratings from at least 2 reviewers to be accepted");
        }
        talkDao.updateStatus(talkId, (String) status);
        return getTalk(talkId);
    }

    @Transactional
    public TalkDto withdraw(long talkId, Map<String, Object> body) {
        if (!talkDao.lock(talkId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "talk not found");
        }
        Object raw = body.get("speakerId");
        if (!(raw instanceof Integer || raw instanceof Long) || ((Number) raw).longValue() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "speakerId must be a positive whole number");
        }
        long speakerId = ((Number) raw).longValue();
        TalkDto talk = getTalk(talkId);
        if (speakerId != talk.speakerId.longValue()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "only the talk's speaker can withdraw it");
        }
        if (!allowed(talk.status, "WITHDRAWN")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "a " + talk.status + " talk can't be withdrawn");
        }
        talkDao.updateStatus(talkId, "WITHDRAWN");
        return getTalk(talkId);
    }

    /**
     * The acceptance state machine (workflow/specs/acceptance.md). Anything not listed, including a repeat, is refused.
     */
    private static boolean allowed(String from, String to) {
        switch (from) {
            case "SUBMITTED":
                return true;
            case "WAITLISTED":
                return !to.equals("WAITLISTED");
            case "ACCEPTED":
                return to.equals("WITHDRAWN");
            default:
                return false;
        }
    }

    // ---------------------------------------------------------------- co-speakers

    @Transactional
    public TalkDto addCoSpeaker(long talkId, long speakerId) {
        lockTalkAndCheckSpeaker(talkId, speakerId);
        TalkDto talk = getTalk(talkId);
        if (talk.speakerId == speakerId) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "the main speaker can't also be a co-speaker");
        }
        if (talk.coSpeakerIds.contains(speakerId)) {
            return talk;
        }
        if (talk.coSpeakerIds.size() >= MAX_CO_SPEAKERS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "a talk can have at most " + MAX_CO_SPEAKERS + " co-speakers");
        }
        talkDao.addCoSpeaker(talkId, speakerId);
        return getTalk(talkId);
    }

    @Transactional
    public TalkDto removeCoSpeaker(long talkId, long speakerId) {
        lockTalkAndCheckSpeaker(talkId, speakerId);
        if (talkDao.removeCoSpeaker(talkId, speakerId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "not a co-speaker of this talk");
        }
        return getTalk(talkId);
    }

    private void lockTalkAndCheckSpeaker(long talkId, long speakerId) {
        if (!talkDao.lock(talkId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "talk not found");
        }
        if (!speakerDao.exists(speakerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "speaker not found");
        }
    }

    // ---------------------------------------------------------------- statistics

    public Map<String, Object> statistics() {
        List<TalkDto> talks = listTalks(null, null);
        Map<String, Integer> byFormat = new LinkedHashMap<>();
        Map<String, Integer> byLevel = new LinkedHashMap<>();
        Map<String, Integer> byStatus = new LinkedHashMap<>();
        List<Long> speakersWithTalks = new ArrayList<>();
        int minutes = 0;
        double ratingSum = 0;
        int rated = 0;
        for (TalkDto t : talks) {
            byFormat.put(t.format, byFormat.getOrDefault(t.format, 0) + 1);
            byLevel.put(t.level, byLevel.getOrDefault(t.level, 0) + 1);
            byStatus.put(t.status, byStatus.getOrDefault(t.status, 0) + 1);
            if (!speakersWithTalks.contains(t.speakerId) && speakerDao.findById(t.speakerId) != null) {
                speakersWithTalks.add(t.speakerId);
            }
            minutes += t.durationMinutes;
            if (t.averageRating != null) {
                ratingSum += t.averageRating;
                rated++;
            }
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("speakers", speakerDao.count());
        stats.put("speakersWithTalks", speakersWithTalks.size());
        stats.put("talks", talks.size());
        stats.put("byFormat", byFormat);
        stats.put("byLevel", byLevel);
        stats.put("byStatus", byStatus);
        stats.put("totalHours", minutes / 60);
        stats.put("averageRating", rated == 0 ? null : Utils.roundRating(ratingSum / rated));
        stats.put("generated", Utils.now());
        return stats;
    }

    // ---------------------------------------------------------------- ratings

    @Transactional
    public Map<String, Object> rate(long talkId, long reviewerId, Map<String, Object> body) {
        if (reviewerId < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reviewerId must be positive");
        }
        if (!talkDao.exists(talkId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "talk not found");
        }
        Object score = body.get("score");
        if (!(score instanceof Integer)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "score must be a whole number");
        }
        int s = (Integer) score;
        if (s < 1 || s > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "score must be 1-5");
        }
        String now = Utils.now();
        ratingDao.save(talkId, reviewerId, s, now);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("talkId", talkId);
        result.put("reviewerId", reviewerId);
        result.put("score", s);
        result.put("ratedAtDisplay", Utils.usShort(Utils.parse(now)));
        return result;
    }
}
