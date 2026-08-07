package queries;

import java.time.LocalDateTime;

public final class DateRangePerformanceQuery {

    private final int performanceId;
    private final String title;
    private final String venueName;
    private final String address;
    private final String postalCode;
    private final String city;
    private final LocalDateTime dateTime;
    private final int availableTickets;
    private final double cheapestPrice;
    private final Double distanceKm;


    public DateRangePerformanceQuery(
            int performanceId,
            String title,
            String venueName,
            String address,
            String postalCode,
            String city,
            LocalDateTime dateTime,
            int availableTickets,
            double cheapestPrice,
            Double distanceKm
    ) {
        this.performanceId = performanceId;
        this.title = title;
        this.venueName = venueName;
        this.address = address;
        this.postalCode = postalCode;
        this.city = city;
        this.dateTime = dateTime;
        this.availableTickets = availableTickets;
        this.cheapestPrice = cheapestPrice;
        this.distanceKm = distanceKm;
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

    public String getAddress() {
        return address;
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


    public int getAvailableTickets() {
        return availableTickets;
    }

    public double getCheapestPrice() {
        return cheapestPrice;
    }

    public Double getDistanceKm() {
        return distanceKm;
    }
}
