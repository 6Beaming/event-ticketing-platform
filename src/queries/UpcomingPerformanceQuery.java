package queries;

public final class UpcomingPerformanceQuery {

    private final int performanceId;
    private final String title;
    private final String venueName;
    private final String city;
    private final double distanceKm;
    private final double cheapestAvailablePrice;


    public UpcomingPerformanceQuery(
            int performanceId,
            String title,
            String venueName,
            String city,
            double distanceKm,
            double cheapestAvailablePrice
    ) {
        this.performanceId = performanceId;
        this.title = title;
        this.venueName = venueName;
        this.city = city;
        this.distanceKm = distanceKm;
        this.cheapestAvailablePrice = cheapestAvailablePrice;
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


    public double getDistanceKm() {
        return distanceKm;
    }


    public double getCheapestAvailablePrice() {
        return cheapestAvailablePrice;
    }
}