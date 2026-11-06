package no.scienta.cfp;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * Request body for POST /speakers.
 */
public class SpeakerForm {

    @NotBlank
    @JsonDeserialize(using = StrictStringDeserializer.class)
    private String name;

    @NotBlank
    @JsonDeserialize(using = StrictStringDeserializer.class)
    private String email;

    @Size(max = 500)
    private String bio;

    // TODO twitter handle, see CFP-112
    // private String twitter;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }
}
