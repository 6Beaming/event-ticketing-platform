package queries;

public final class SeatMapSummaryQuery {

    private final String sectionName;
    private final String tierCode;
    private final double price;
    private final int available;
    private final int sold;
    private final int blocked;


    public SeatMapSummaryQuery(
            String sectionName,
            String tierCode,
            double price,
            int available,
            int sold,
            int blocked
    ) {
        this.sectionName = sectionName;
        this.tierCode = tierCode;
        this.price = price;
        this.available = available;
        this.sold = sold;
        this.blocked = blocked;
    }


    public String getSectionName() {
        return sectionName;
    }


    public String getTierCode() {
        return tierCode;
    }


    public double getPrice() {
        return price;
    }


    public int getAvailable() {
        return available;
    }


    public int getSold() {
        return sold;
    }


    public int getBlocked() {
        return blocked;
    }

}