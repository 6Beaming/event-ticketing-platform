package operations.event;

import java.time.LocalDateTime;

public final class PerformanceInput {
    private final int eventId;
    private final int venueId;
    private final LocalDateTime dateTime;

    public PerformanceInput(int eventId, int venueId, LocalDateTime dateTime) {
        this.eventId = eventId;
        this.venueId = venueId;
        this.dateTime = dateTime;
    }

    public int getEventId() {
        return eventId;
    }

    public int getVenueId() {
        return venueId;
    }

    public LocalDateTime getDateTime() {
        return dateTime;
    }
}
