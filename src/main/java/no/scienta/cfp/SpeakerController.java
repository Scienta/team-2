package no.scienta.cfp;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/speakers")
public class SpeakerController {

    private final ConferenceService service;

    public SpeakerController(ConferenceService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@Valid @RequestBody SpeakerForm form) {
        return service.registerSpeaker(form);
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable Long id) {
        return service.getSpeaker(id);
    }

    // used by the admin UI to fix typos
    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        return service.updateSpeaker(id, text(body, "name"), text(body, "email"), text(body, "bio"));
    }

    // A number or boolean is a 400, not coerced to text as Map<String, String> did (speakers spec D2)
    private static String text(Map<String, Object> body, String field) {
        Object value = body.get(field);
        if (value != null && !(value instanceof String)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be a string");
        }
        return (String) value;
    }

    @GetMapping("/{id}/talks")
    public List<TalkDto> talks(@PathVariable Long id) {
        return service.getTalksForSpeaker(id);
    }

    // admin only, not in the API docs
    @GetMapping("/search")
    public List<Map<String, Object>> search(@RequestParam String q) {
        return service.searchSpeakers(q);
    }
}
