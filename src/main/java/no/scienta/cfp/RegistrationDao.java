package no.scienta.cfp;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class RegistrationDao {

    private final JdbcTemplate jdbc;

    private final RowMapper<RegistrationDto> mapper = (rs, rowNum) -> {
        RegistrationDto r = new RegistrationDto();
        r.id = rs.getLong("id");
        r.workshopId = rs.getLong("talk_id");
        r.name = rs.getString("name");
        r.email = rs.getString("email");
        return r;
    };

    public RegistrationDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Returns the new id, or null if this email is already registered for the workshop.
     */
    public Long insert(long talkId, String name, String email, String emailKey) {
        List<Long> ids = jdbc.queryForList(
                "INSERT INTO workshop_registration (talk_id, name, email, email_key) VALUES (?, ?, ?, ?) "
                        + "ON CONFLICT (talk_id, email_key) DO NOTHING RETURNING id",
                Long.class, talkId, name, email, emailKey);
        return ids.isEmpty() ? null : ids.get(0);
    }

    public int count(long talkId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM workshop_registration WHERE talk_id = ?", Integer.class, talkId);
        return n == null ? 0 : n;
    }

    public List<RegistrationDto> findByWorkshop(long talkId) {
        return jdbc.query("SELECT id, talk_id, name, email FROM workshop_registration WHERE talk_id = ? ORDER BY id", mapper, talkId);
    }
}
