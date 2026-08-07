package queries;

import java.time.LocalDateTime;

public final class FilteredPerformanceQuery {

    private final int performanceId;
    private final String title;
    private final String venueName;
    private final String city;
    private final String segment;
    private final String genre;
    private final LocalDateTime dateTime;
    private final double cheapestPrice;
    private final int availableTickets;


    public FilteredPerformanceQuery(
            int performanceId,
            String title,
            String venueName,
            String city,
            String segment,
            String genre,
            LocalDateTime dateTime,
            double cheapestPrice,
            int availableTickets
    ) {
        this.performanceId = performanceId;
        this.title = title;
        this.venueName = venueName;
        this.city = city;
        this.segment = segment;
        this.genre = genre;
        this.dateTime = dateTime;
        this.cheapestPrice = cheapestPrice;
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


    public String getCity() {
        return city;
    }


    public String getSegment() {
        return segment;
    }


    public String getGenre() {
        return genre;
    }

    public LocalDateTime getDateTime() {
        return dateTime;
    }


    public double getCheapestPrice() {
        return cheapestPrice;
    }


    public int getAvailableTickets() {
        return availableTickets;
    }

}
