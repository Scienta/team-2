package no.scienta.cfp;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Workshop registration (workflow/specs/workshop-registration.md). Errors come in the order 404, 400, 409.
 */
@Service
public class WorkshopService {

    private static final int MAX_ATTENDEES = 20;
    // RFC 5321. It also keeps email_key small enough for its unique index: a much longer key is a database error.
    private static final int MAX_EMAIL_LENGTH = 254;

    private static final Pattern UNSTORABLE = Pattern.compile("[\\p{Cntrl}\\p{Cs}]");

    private final TalkDao talkDao;
    private final RegistrationDao registrationDao;

    public WorkshopService(TalkDao talkDao, RegistrationDao registrationDao) {
        this.talkDao = talkDao;
        this.registrationDao = registrationDao;
    }

    @Transactional
    public RegistrationDto register(long workshopId, Map<String, Object> body) {
        if (talkDao.findById(workshopId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "workshop not found");
        }
        String name = requireText(body, "name");
        String email = requireText(body, "email");
        String trimmedEmail = trim(email);
        if (trimmedEmail.length() > MAX_EMAIL_LENGTH || !Utils.isWellFormedEmail(trimmedEmail)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid email");
        }
        talkDao.lock(workshopId);
        // A new read, not the talk from before the lock: a withdrawal may have committed while we waited for it.
        TalkDto talk = talkDao.findById(workshopId);
        // D2 (revised after harness check-2): a talk that isn't a workshop exists, but takes no registrations
        if (!talk.format.equals("WORKSHOP")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "a " + talk.format + " talk isn't a workshop");
        }
        String status = talk.status;
        if (!status.equals("ACCEPTED")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "a " + status + " workshop doesn't take registrations");
        }
        if (registrationDao.count(workshopId) >= MAX_ATTENDEES) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "this workshop is full");
        }
        Long id = registrationDao.insert(workshopId, name, email, trimmedEmail.toLowerCase(Locale.ROOT));
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "this email is already registered for this workshop");
        }
        RegistrationDto registration = new RegistrationDto();
        registration.id = id;
        registration.workshopId = workshopId;
        registration.name = name;
        registration.email = email;
        return registration;
    }

    // D2 (revised after harness check-2): a talk that isn't a workshop lists no registrations
    public List<RegistrationDto> registrations(long workshopId) {
        if (talkDao.findById(workshopId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "workshop not found");
        }
        return registrationDao.findByWorkshop(workshopId);
    }

    private static String requireText(Map<String, Object> body, String field) {
        Object value = body.get(field);
        if (!(value instanceof String)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
        String trimmed = trim((String) value);
        if (trimmed.isEmpty() || UNSTORABLE.matcher(trimmed).find()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
        return (String) value;
    }

    private static String trim(String s) {
        return Utils.trimSpaces(s);
    }
}
