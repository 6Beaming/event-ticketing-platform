package operations.inventory;

public final class ReservedSeatLocation {
    private final String rowName;
    private final int seatNumber;

    public ReservedSeatLocation(String rowName, int seatNumber) {
        this.rowName = rowName;
        this.seatNumber = seatNumber;
    }

    public String getRowName() {
        return rowName;
    }

    public int getSeatNumber() {
        return seatNumber;
    }
}
