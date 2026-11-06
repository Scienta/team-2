package no.scienta.cfp;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WorkshopController {

    private final WorkshopService service;

    public WorkshopController(WorkshopService service) {
        this.service = service;
    }

    @PostMapping("/workshops/{id}/registrations")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrationDto register(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return service.register(id, body);
    }

    @GetMapping("/workshops/{id}/registrations")
    public List<RegistrationDto> registrations(@PathVariable Long id) {
        return service.registrations(id);
    }
}
