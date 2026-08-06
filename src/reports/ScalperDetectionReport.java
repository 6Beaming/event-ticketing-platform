package reports;

public final class ScalperDetectionReport {

    private final int customerId;
    private final String customerName;
    private final String city;
    private final int ticketsPurchased;
    private final int ticketsListed;


    public ScalperDetectionReport(
            int customerId,
            String customerName,
            String city,
            int ticketsPurchased,
            int ticketsListed
    ) {
        this.customerId = customerId;
        this.customerName = customerName;
        this.city = city;
        this.ticketsPurchased = ticketsPurchased;
        this.ticketsListed = ticketsListed;
    }


    public int getCustomerId() {
        return customerId;
    }


    public String getCustomerName() {
        return customerName;
    }


    public String getCity() {
        return city;
    }


    public int getTicketsPurchased() {
        return ticketsPurchased;
    }


    public int getTicketsListed() {
        return ticketsListed;
    }
}