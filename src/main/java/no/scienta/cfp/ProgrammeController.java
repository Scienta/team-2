package no.scienta.cfp;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProgrammeController {

    private final ProgrammeService service;

    public ProgrammeController(ProgrammeService service) {
        this.service = service;
    }

    @PutMapping("/programme/{room}/{slot}")
    public ProgrammeEntry schedule(@PathVariable String room, @PathVariable String slot,
                                   @RequestBody Map<String, Object> body) {
        return service.schedule(room, slot, body);
    }

    @GetMapping("/programme/{room}/{slot}")
    public ProgrammeEntry get(@PathVariable String room, @PathVariable String slot) {
        return service.get(room, slot);
    }
}
