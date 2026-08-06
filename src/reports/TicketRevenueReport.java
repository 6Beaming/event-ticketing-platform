package reports;

import java.math.BigDecimal;

public final class TicketRevenueReport {

    private final String city;
    private final String venueName;
    private final int ticketsSold;
    private final BigDecimal grossRevenue;

    public TicketRevenueReport(
            String city,
            String venueName,
            int ticketsSold,
            BigDecimal grossRevenue
    ) {
        this.city = city;
        this.venueName = venueName;
        this.ticketsSold = ticketsSold;
        this.grossRevenue = grossRevenue;
    }

    public String getCity() {
        return city;
    }

    public String getVenueName() {
        return venueName;
    }

    public int getTicketsSold() {
        return ticketsSold;
    }

    public BigDecimal getGrossRevenue() {
        return grossRevenue;
    }

    public String displayVenueName() {
        return venueName == null ? "-" : venueName;
    }
}