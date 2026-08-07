package toolkit;

public class PricingRecommendationInput {

    private final int genreId;
    private final String city;
    private final int venueCapacity;
    private final double capacityTolerancePct;
    private final int lookbackMonths;
    private final int maxComparables;


    public PricingRecommendationInput(
            int genreId,
            String city,
            int venueCapacity,
            double capacityTolerancePct,
            int lookbackMonths,
            int maxComparables
    ) {
        this.genreId = genreId;
        this.city = city;
        this.venueCapacity = venueCapacity;
        this.capacityTolerancePct = capacityTolerancePct;
        this.lookbackMonths = lookbackMonths;
        this.maxComparables = maxComparables;
    }


    public int getGenreId() {
        return genreId;
    }


    public String getCity() {
        return city;
    }


    public int getVenueCapacity() {
        return venueCapacity;
    }


    public double getCapacityTolerancePct() {
        return capacityTolerancePct;
    }


    public int getLookbackMonths() {
        return lookbackMonths;
    }


    public int getMaxComparables() {
        return maxComparables;
    }
}