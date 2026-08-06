package reports;

public final class EventCommentReport {

    private final int eventId;
    private final String eventTitle;
    private final String commentText;


    public EventCommentReport(
            int eventId,
            String eventTitle,
            String commentText
    ) {
        this.eventId = eventId;
        this.eventTitle = eventTitle;
        this.commentText = commentText;
    }


    public int getEventId() {
        return eventId;
    }


    public String getEventTitle() {
        return eventTitle;
    }


    public String getCommentText() {
        return commentText;
    }
}