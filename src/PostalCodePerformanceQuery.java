package queries;

import java.time.LocalDateTime;

public final class PostalCodePerformanceQuery {

    private final int performanceId;
    private final String title;
    private final String venueName;
    private final String postalCode;
    private final String city;
    private final LocalDateTime dateTime;


    public PostalCodePerformanceQuery(
            int performanceId,
            String title,
            String venueName,
            String postalCode,
            String city,
            LocalDateTime dateTime
    ) {
        this.performanceId = performanceId;
        this.title = title;
        this.venueName = venueName;
        this.postalCode = postalCode;
        this.city = city;
        this.dateTime = dateTime;
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


    public String getCity() {
        return city;
    }


    public LocalDateTime getDateTime() {
        return dateTime;
    }
}