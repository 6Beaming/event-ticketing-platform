package reports;

import java.math.BigDecimal;

public final class SellThroughReport {

    private final int performanceId;
    private final String title;
    private final String city;
    private final int capacity;
    private final int numSold;
    private final BigDecimal sellThroughRate;


    public SellThroughReport(
            int performanceId,
            String title,
            String city,
            int capacity,
            int numSold,
            BigDecimal sellThroughRate
    ) {
        this.performanceId = performanceId;
        this.title = title;
        this.city = city;
        this.capacity = capacity;
        this.numSold = numSold;
        this.sellThroughRate = sellThroughRate;
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


    public int getCapacity() {
        return capacity;
    }


    public int getNumSold() {
        return numSold;
    }


    public BigDecimal getSellThroughRate() {
        return sellThroughRate;
    }
}