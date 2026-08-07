package toolkit;

import java.time.LocalDateTime;

public class ComparablePerformance {

    private final int performanceId;
    private final String title;
    private final String genreName;
    private final String segmentName;
    private final int venueId;
    private final String venueName;
    private final String city;
    private final int venueCapacity;
    private final LocalDateTime dateTime;
    private final int matchRank;
    private final String selectionReason;


    public ComparablePerformance(
            int performanceId,
            String title,
            String genreName,
            String segmentName,
            int venueId,
            String venueName,
            String city,
            int venueCapacity,
            LocalDateTime dateTime,
            int matchRank,
            String selectionReason
    ) {
        this.performanceId = performanceId;
        this.title = title;
        this.genreName = genreName;
        this.segmentName = segmentName;
        this.venueId = venueId;
        this.venueName = venueName;
        this.city = city;
        this.venueCapacity = venueCapacity;
        this.dateTime = dateTime;
        this.matchRank = matchRank;
        this.selectionReason = selectionReason;
    }


    public int getPerformanceId() {
        return performanceId;
    }

    public String getTitle() {
        return title;
    }

    public String getGenreName() {
        return genreName;
    }

    public String getSegmentName() {
        return segmentName;
    }

    public int getVenueId() {
        return venueId;
    }

    public String getVenueName() {
        return venueName;
    }

    public String getCity() {
        return city;
    }

    public int getVenueCapacity() {
        return venueCapacity;
    }

    public LocalDateTime getDateTime() {
        return dateTime;
    }

    public int getMatchRank() {
        return matchRank;
    }

    public String getSelectionReason() {
        return selectionReason;
    }
}
