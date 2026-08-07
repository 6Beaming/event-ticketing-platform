package operations.resale;

public final class OwnedResaleTicket {
    private final int ticketId;
    private final int performanceId;
    private final String status;

    public OwnedResaleTicket(int ticketId, int performanceId, String status) {
        this.ticketId = ticketId;
        this.performanceId = performanceId;
        this.status = status;
    }

    public int getTicketId() {
        return ticketId;
    }

    public int getPerformanceId() {
        return performanceId;
    }

    public String getStatus() {
        return status;
    }
}
