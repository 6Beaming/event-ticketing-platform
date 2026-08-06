package queries;

import java.time.LocalDateTime;

public final class AddressPerformanceQuery {

    private final int venueId;
    private final String venueName;
    private final String address;
    private final String city;
    private final String country;
    private final int performanceId;
    private final String title;
    private final LocalDateTime dateTime;


    public AddressPerformanceQuery(
            int venueId,
            String venueName,
            String address,
            String city,
            String country,
            int performanceId,
            String title,
            LocalDateTime dateTime
    ) {
        this.venueId = venueId;
        this.venueName = venueName;
        this.address = address;
        this.city = city;
        this.country = country;
        this.performanceId = performanceId;
        this.title = title;
        this.dateTime = dateTime;
    }


    public int getVenueId() {
        return venueId;
    }


    public String getVenueName() {
        return venueName;
    }


    public String getAddress() {
        return address;
    }


    public String getCity() {
        return city;
    }


    public String getCountry() {
        return country;
    }


    public int getPerformanceId() {
        return performanceId;
    }


    public String getTitle() {
        return title;
    }


    public LocalDateTime getDateTime() {
        return dateTime;
    }
}