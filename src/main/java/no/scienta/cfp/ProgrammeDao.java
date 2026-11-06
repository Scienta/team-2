package no.scienta.cfp;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import no.scienta.cfp.Venue.Place;
import no.scienta.cfp.Venue.Room;
import no.scienta.cfp.Venue.Slot;

/**
 * Reads and writes the programme: which talk is in which room and slot.
 */
@Repository
public class ProgrammeDao {

    private final JdbcTemplate jdbc;

    public ProgrammeDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Locks the slot, in all three rooms, until the transaction ends.
     */
    public void lockSlot(Slot slot) {
        if (jdbc.queryForList("SELECT slot FROM programme_slot WHERE slot = ? FOR UPDATE", slot.name()).isEmpty()) {
            throw new IllegalStateException("programme_slot has no row for " + slot + ": schema.sql seeds it");
        }
    }

    public Optional<Long> find(Room room, Slot slot) {
        return jdbc.queryForList("SELECT talk_id FROM programme_entry WHERE room = ? AND slot = ?", Long.class,
                room.name(), slot.name()).stream().findFirst();
    }

    public Optional<Place> findByTalk(long talkId) {
        return jdbc.query("SELECT room, slot FROM programme_entry WHERE talk_id = ?",
                (rs, n) -> new Place(Room.valueOf(rs.getString("room")), Slot.valueOf(rs.getString("slot"))),
                talkId).stream().findFirst();
    }

    /**
     * The talks in this slot in the other rooms.
     */
    public List<Long> talksInSlot(Slot slot, Room except) {
        return jdbc.queryForList("SELECT talk_id FROM programme_entry WHERE slot = ? AND room <> ?", Long.class,
                slot.name(), except.name());
    }

    /**
     * Returns the number of rows deleted: 1, or 0 if the place was empty.
     */
    public int delete(Room room, Slot slot) {
        return jdbc.update("DELETE FROM programme_entry WHERE room = ? AND slot = ?", room.name(), slot.name());
    }

    public void insert(Room room, Slot slot, long talkId) {
        jdbc.update("INSERT INTO programme_entry (room, slot, talk_id) VALUES (?, ?, ?)", room.name(), slot.name(),
                talkId);
    }
}
