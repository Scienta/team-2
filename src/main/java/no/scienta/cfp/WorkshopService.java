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

    private static final Pattern EDGE_WHITESPACE = Pattern.compile("^[\\s\\p{Z}]+|[\\s\\p{Z}]+$");
    private static final Pattern UNSTORABLE = Pattern.compile("[\\p{Cntrl}\\p{Cs}]");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s\\p{Z}@]+@[^\\s\\p{Z}@]+\\.[^\\s\\p{Z}@]+$");

    private final TalkDao talkDao;
    private final RegistrationDao registrationDao;

    public WorkshopService(TalkDao talkDao, RegistrationDao registrationDao) {
        this.talkDao = talkDao;
        this.registrationDao = registrationDao;
    }

    @Transactional
    public RegistrationDto register(long workshopId, Map<String, Object> body) {
        requireWorkshop(workshopId);
        String name = requireText(body, "name");
        String email = requireText(body, "email");
        String trimmedEmail = trim(email);
        if (!EMAIL.matcher(trimmedEmail).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid email");
        }
        talkDao.lock(workshopId);
        // A new read, not the talk from before the lock: a withdrawal may have committed while we waited for it.
        String status = talkDao.findById(workshopId).status;
        if (!status.equals("ACCEPTED")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "a " + status + " workshop doesn't take registrations");
        }
        RegistrationDto registration = new RegistrationDto();
        registration.id = registrationDao.insert(workshopId, name, email, trimmedEmail.toLowerCase(Locale.ROOT));
        registration.workshopId = workshopId;
        registration.name = name;
        registration.email = email;
        return registration;
    }

    public List<RegistrationDto> registrations(long workshopId) {
        requireWorkshop(workshopId);
        return registrationDao.findByWorkshop(workshopId);
    }

    private void requireWorkshop(long id) {
        TalkDto talk = talkDao.findById(id);
        if (talk == null || !talk.format.equals("WORKSHOP")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "workshop not found");
        }
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
        return EDGE_WHITESPACE.matcher(s).replaceAll("");
    }
}
