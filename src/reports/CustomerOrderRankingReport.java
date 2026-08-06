package reports;

public final class CustomerOrderRankingReport {

    private final int customerId;
    private final String customerName;
    private final String city;
    private final int numberOfOrders;


    public CustomerOrderRankingReport(
            int customerId,
            String customerName,
            String city,
            int numberOfOrders
    ) {
        this.customerId = customerId;
        this.customerName = customerName;
        this.city = city;
        this.numberOfOrders = numberOfOrders;
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


    public int getNumberOfOrders() {
        return numberOfOrders;
    }
}