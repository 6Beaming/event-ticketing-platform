package operations.review;

import java.time.LocalDateTime;

public final class CustomerReview {
    private final int performanceId;
    private final int eventRating;
    private final int venueRating;
    private final String comment;
    private final LocalDateTime reviewDate;

    public CustomerReview(
            int performanceId,
            int eventRating,
            int venueRating,
            String comment,
            LocalDateTime reviewDate
    ) {
        this.performanceId = performanceId;
        this.eventRating = eventRating;
        this.venueRating = venueRating;
        this.comment = comment;
        this.reviewDate = reviewDate;
    }

    public int getPerformanceId() {
        return performanceId;
    }

    public int getEventRating() {
        return eventRating;
    }

    public int getVenueRating() {
        return venueRating;
    }

    public String getComment() {
        return comment;
    }

    public LocalDateTime getReviewDate() {
        return reviewDate;
    }
}
