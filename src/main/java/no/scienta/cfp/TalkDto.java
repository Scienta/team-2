package no.scienta.cfp;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * A talk as the API returns it. Also used internally all over the place.
 */
public class TalkDto {

    public Long id;
    public String title;
    public String format;
    public String level;
    public Long speakerId;
    public Integer durationMinutes;
    public String status;
    public Date submittedAt;
    @JsonSerialize(using = ToStringSerializer.class)  // yyyy-MM-dd as Jackson 2 wrote it; Jackson 3 writes a timestamp
    public java.sql.Date submittedOn;  // the reporting tool wants a plain date
    public Double averageRating;
    public int ratingCount;
    public List<Long> coSpeakerIds = new ArrayList<>();
}
