package reports;

import java.math.BigDecimal;

public final class SellThroughBucketReport {

    private final String city;
    private final int performanceId;
    private final String title;
    private final BigDecimal sellThroughRate;
    private final String bucket;


    public SellThroughBucketReport(
            String city,
            int performanceId,
            String title,
            BigDecimal sellThroughRate,
            String bucket
    ) {
        this.city = city;
        this.performanceId = performanceId;
        this.title = title;
        this.sellThroughRate = sellThroughRate;
        this.bucket = bucket;
    }


    public String getCity() {
        return city;
    }


    public int getPerformanceId() {
        return performanceId;
    }


    public String getTitle() {
        return title;
    }


    public BigDecimal getSellThroughRate() {
        return sellThroughRate;
    }


    public String getBucket() {
        return bucket;
    }
}