package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ConferenceServiceTest {

    private SpeakerDao speakerDao;
    private TalkDao talkDao;
    private RatingDao ratingDao;
    private ConferenceService service;

    @BeforeEach
    void setUp() {
        speakerDao = mock(SpeakerDao.class);
        talkDao = mock(TalkDao.class);
        ratingDao = mock(RatingDao.class);
        service = new ConferenceService(speakerDao, talkDao, ratingDao, mock(NotificationService.class));
    }

    @Test
    void talkWithoutTitleIsRejected() {
        TalkForm form = form();
        form.title = " ";
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.submitTalk(form));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    }

    @Test
    void unknownFormatIsRejected() {
        TalkForm form = form();
        form.format = "KEYNOTE";
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.submitTalk(form));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    }

    @Test
    void unknownSpeakerIsRejected() {
        when(speakerDao.exists(7L)).thenReturn(false);
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.submitTalk(form()));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    }

    @Test
    void emailRegisteredConcurrentlyIsConflictInAnyServerLanguage() {
        SpeakerForm form = new SpeakerForm();
        form.setName("Ada");
        form.setEmail("ada@example.com");
        when(speakerDao.emailTaken("ada@example.com")).thenReturn(false);
        when(speakerDao.insert("Ada", "ada@example.com", null))
                .thenThrow(new DuplicateKeyException("FEIL: duplisert nøkkelverdi bryter unik-skranke"));
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.registerSpeaker(form));
        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
    }

    @Test
    void duplicateEmailIsConflict() {
        SpeakerForm form = new SpeakerForm();
        form.setName("Ada");
        form.setEmail("ada@example.com");
        when(speakerDao.emailTaken("ada@example.com")).thenReturn(true);
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.registerSpeaker(form));
        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
    }

    @Test
    void listingAllTalksFetchesRatingsInOneQuery() {
        when(talkDao.findAll()).thenReturn(Arrays.asList(talk(1L), talk(2L)));
        service.listTalks(null, null);
        verify(ratingDao, times(1)).statsFor(Arrays.asList(1L, 2L));
        verify(ratingDao, never()).stats(anyLong());
    }

    @Test
    void listingASpeakersTalksFetchesRatingsInOneQuery() {
        when(speakerDao.findById(7L)).thenReturn(Collections.singletonMap("id", 7L));
        when(talkDao.findBySpeaker(7L)).thenReturn(Arrays.asList(talk(1L), talk(2L)));
        service.getTalksForSpeaker(7L);
        verify(ratingDao, times(1)).statsFor(Arrays.asList(1L, 2L));
        verify(ratingDao, never()).stats(anyLong());
    }

    private TalkDto talk(long id) {
        TalkDto talk = new TalkDto();
        talk.id = id;
        return talk;
    }

    private TalkForm form() {
        TalkForm form = new TalkForm();
        form.title = "Legacy is a feeling";
        form.format = "PRESENTATION";
        form.level = "INTERMEDIATE";
        form.speakerId = 7L;
        return form;
    }
}
