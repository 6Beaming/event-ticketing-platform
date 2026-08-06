package reports;

public final class EventPerformanceReport {

    private final String segmentName;
    private final String genreName;
    private final String country;
    private final String city;
    private final String venueName;
    private final int eventCount;
    private final int performanceCount;

    public EventPerformanceReport(
            String segmentName,
            String genreName,
            String country,
            String city,
            String venueName,
            int eventCount,
            int performanceCount
    ) {
        this.segmentName = segmentName;
        this.genreName = genreName;
        this.country = country;
        this.city = city;
        this.venueName = venueName;
        this.eventCount = eventCount;
        this.performanceCount = performanceCount;
    }

    public String getSegmentName() {
        return segmentName;
    }

    public String getGenreName() {
        return genreName;
    }

    public String getCountry() {
        return country;
    }

    public String getCity() {
        return city;
    }

    public String getVenueName() {
        return venueName;
    }

    public int getEventCount() {
        return eventCount;
    }

    public int getPerformanceCount() {
        return performanceCount;
    }
}