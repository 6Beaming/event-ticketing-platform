package reports;

import java.math.BigDecimal;

public final class TicketRevenueReport {

    private final String city;
    private final int ticketsSold;
    private final BigDecimal grossRevenue;

    public TicketRevenueReport(
            String city,
            int ticketsSold,
            BigDecimal grossRevenue
    ) {
        this.city = city;
        this.ticketsSold = ticketsSold;
        this.grossRevenue = grossRevenue;
    }

    public String getCity() {
        return city;
    }

    public int getTicketsSold() {
        return ticketsSold;
    }

    public BigDecimal getGrossRevenue() {
        return grossRevenue;
    }
}