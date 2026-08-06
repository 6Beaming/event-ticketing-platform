package reports;

import java.util.List;

public final class EventNounPhraseReport {

    private final int eventId;
    private final String eventTitle;
    private final List<NounPhraseCount> phrases;


    public EventNounPhraseReport(
            int eventId,
            String eventTitle,
            List<NounPhraseCount> phrases
    ) {
        this.eventId = eventId;
        this.eventTitle = eventTitle;
        this.phrases = phrases;
    }


    public int getEventId() {
        return eventId;
    }


    public String getEventTitle() {
        return eventTitle;
    }


    public List<NounPhraseCount> getPhrases() {
        return phrases;
    }
}