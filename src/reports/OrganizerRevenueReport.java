package reports;

import java.math.BigDecimal;

public final class OrganizerRevenueReport {

    private final int organizerId;
    private final String organizerName;
    private final String country;
    private final String city;
    private final BigDecimal grossRevenue;


    public OrganizerRevenueReport(
            int organizerId,
            String organizerName,
            String country,
            String city,
            BigDecimal grossRevenue
    ) {
        this.organizerId = organizerId;
        this.organizerName = organizerName;
        this.country = country;
        this.city = city;
        this.grossRevenue = grossRevenue;
    }


    public int getOrganizerId() {
        return organizerId;
    }


    public String getOrganizerName() {
        return organizerName;
    }


    public String getCountry() {
        return country;
    }


    public String getCity() {
        return city;
    }


    public BigDecimal getGrossRevenue() {
        return grossRevenue;
    }
}