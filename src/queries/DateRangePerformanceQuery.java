package queries;

public final class DateRangePerformanceQuery {

    private final int performanceId;
    private final String title;
    private final String venueName;
    private final String postalCode;
    private final int availableTickets;


    public DateRangePerformanceQuery(
            int performanceId,
            String title,
            String venueName,
            String postalCode,
            int availableTickets
    ) {
        this.performanceId = performanceId;
        this.title = title;
        this.venueName = venueName;
        this.postalCode = postalCode;
        this.availableTickets = availableTickets;
    }


    public int getPerformanceId() {
        return performanceId;
    }


    public String getTitle() {
        return title;
    }


    public String getVenueName() {
        return venueName;
    }


    public String getPostalCode() {
        return postalCode;
    }


    public int getAvailableTickets() {
        return availableTickets;
    }
}