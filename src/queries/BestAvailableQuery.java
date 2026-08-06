package queries;

public final class BestAvailableQuery {

    private final String sectionName;
    private final String rowName;
    private final int startSeat;
    private final int endSeat;
    private final double totalPrice;


    public BestAvailableQuery(
            String sectionName,
            String rowName,
            int startSeat,
            int endSeat,
            double totalPrice
    ) {
        this.sectionName = sectionName;
        this.rowName = rowName;
        this.startSeat = startSeat;
        this.endSeat = endSeat;
        this.totalPrice = totalPrice;
    }


    public String getSectionName() {
        return sectionName;
    }


    public String getRowName() {
        return rowName;
    }


    public int getStartSeat() {
        return startSeat;
    }


    public int getEndSeat() {
        return endSeat;
    }


    public double getTotalPrice() {
        return totalPrice;
    }
}