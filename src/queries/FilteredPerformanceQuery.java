package queries;

public final class FilteredPerformanceQuery {

    private final int performanceId;
    private final String title;
    private final String city;
    private final String segment;
    private final String genre;
    private final double cheapestPrice;
    private final int availableTickets;


    public FilteredPerformanceQuery(
            int performanceId,
            String title,
            String city,
            String segment,
            String genre,
            double cheapestPrice,
            int availableTickets
    ) {
        this.performanceId = performanceId;
        this.title = title;
        this.city = city;
        this.segment = segment;
        this.genre = genre;
        this.cheapestPrice = cheapestPrice;
        this.availableTickets = availableTickets;
    }


    public int getPerformanceId() {
        return performanceId;
    }


    public String getTitle() {
        return title;
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


    public double getCheapestPrice() {
        return cheapestPrice;
    }


    public int getAvailableTickets() {
        return availableTickets;
    }

}