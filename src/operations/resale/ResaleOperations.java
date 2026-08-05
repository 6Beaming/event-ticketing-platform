package operations.resale;

import common.OperationResult;
import database.JdbcSupport;
import database.TransactionManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public final class ResaleOperations {
    private final TransactionManager transactions;

    public ResaleOperations(TransactionManager transactions) {
        if (transactions == null) {
            throw new IllegalArgumentException("Transaction manager is required");
        }
        this.transactions = transactions;
    }

    public OperationResult<ResaleListingSummary> listTicket(
            int sellerId,
            int ticketId,
            BigDecimal listingPrice
    ) {
        if (sellerId <= 0 || ticketId <= 0) {
            return OperationResult.invalidInput("Seller and ticket IDs must be positive.");
        }
        if (listingPrice == null || listingPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return OperationResult.invalidInput("Listing price must be positive.");
        }

        return transactions.execute(connection -> {
            if (!lockActiveCustomer(connection, sellerId)) {
                return OperationResult.notFound("Active seller account not found.");
            }
            TicketForListing ticket = lockTicketForListing(connection, ticketId);
            if (ticket == null) {
                return OperationResult.notFound("Ticket not found.");
            }
            if (!"active".equals(ticket.ticketStatus)) {
                return OperationResult.conflict("Only an active ticket can be listed for resale.");
            }
            if (!"scheduled".equals(ticket.performanceStatus)
                    || !ticket.performanceDateTime.isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
                return OperationResult.conflict(
                        "Only a ticket for a scheduled future performance can be listed."
                );
            }
            if (ticket.ownerCustomerId != sellerId) {
                return OperationResult.forbidden(
                        "Only the ticket's current owner can list it for resale."
                );
            }
            if (ticket.ownershipId <= 0) {
                return OperationResult.conflict("Ticket has no current ownership record.");
            }
            if (activeListingExists(connection, ticketId)) {
                return OperationResult.conflict("Ticket already has an active resale listing.");
            }

            BigDecimal capPrice = calculateCapPrice(ticket.faceValue, ticket.resaleCapMultiplier);
            if (listingPrice.compareTo(capPrice) > 0) {
                return OperationResult.conflict(
                        "Listing price exceeds the event cap of $" + capPrice.toPlainString() + "."
                );
            }

            String sql = """
                    INSERT INTO ResaleListing
                        (ticket_id, seller_ownership_id, listing_price, cap_price_at_listing)
                    VALUES (?, ?, ?, ?)
                    """;
            int listingId;
            try (PreparedStatement statement = connection.prepareStatement(
                    sql,
                    Statement.RETURN_GENERATED_KEYS
            )) {
                statement.setInt(1, ticketId);
                statement.setLong(2, ticket.ownershipId);
                statement.setBigDecimal(3, listingPrice);
                statement.setBigDecimal(4, capPrice);
                statement.executeUpdate();
                listingId = JdbcSupport.requireGeneratedIntKey(statement, "resale listing");
            }
            return OperationResult.success(
                    "Ticket listed for resale.",
                    new ResaleListingSummary(listingId, ticketId, listingPrice, capPrice)
            );
        });
    }

    public OperationResult<Void> withdrawListing(int sellerId, int listingId) {
        if (sellerId <= 0 || listingId <= 0) {
            return OperationResult.invalidInput("Seller and listing IDs must be positive.");
        }

        return transactions.execute(connection -> {
            if (!lockActiveCustomer(connection, sellerId)) {
                return OperationResult.notFound("Active seller account not found.");
            }
            String sql = """
                    SELECT rl.status, own.customer_id
                    FROM ResaleListing rl
                    JOIN TicketOwnership own
                      ON own.ownership_id = rl.seller_ownership_id
                    WHERE rl.listing_id = ?
                    FOR UPDATE
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, listingId);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        return OperationResult.notFound("Resale listing not found.");
                    }
                    if (rows.getInt("customer_id") != sellerId) {
                        return OperationResult.forbidden(
                                "Only the seller can withdraw this resale listing."
                        );
                    }
                    if (!"active".equals(rows.getString("status"))) {
                        return OperationResult.conflict(
                                "Only an active unsold listing can be withdrawn."
                        );
                    }
                }
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE ResaleListing SET status = 'withdrawn' WHERE listing_id = ?"
            )) {
                statement.setInt(1, listingId);
                statement.executeUpdate();
            }
            return OperationResult.success("Resale listing withdrawn.");
        });
    }

    public OperationResult<ResalePurchaseSummary> purchaseListing(
            int buyerId,
            int listingId
    ) {
        if (buyerId <= 0 || listingId <= 0) {
            return OperationResult.invalidInput("Buyer and listing IDs must be positive.");
        }

        return transactions.execute(connection -> {
            PaymentSnapshot payment = lockCustomerPayment(connection, buyerId);
            if (payment == null) {
                return OperationResult.notFound(
                        "An active buyer with saved payment information was not found."
                );
            }
            ListingForPurchase listing = lockListingForPurchase(connection, listingId);
            if (listing == null) {
                return OperationResult.notFound("Resale listing not found.");
            }
            if (!"active".equals(listing.listingStatus)) {
                return OperationResult.conflict("Resale listing is no longer active.");
            }
            if (!"active".equals(listing.ticketStatus)) {
                return OperationResult.conflict("The listed ticket is cancelled.");
            }
            if (!"scheduled".equals(listing.performanceStatus)
                    || !listing.performanceDateTime.isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
                return OperationResult.conflict(
                        "The listed ticket is not for a scheduled future performance."
                );
            }
            if (listing.sellerCustomerId == buyerId) {
                return OperationResult.forbidden("A seller cannot buy their own resale listing.");
            }
            if (!listing.currentSellerOwnership) {
                return OperationResult.conflict(
                        "The seller no longer owns this ticket. The listing cannot be purchased."
                );
            }

            int transactionId = insertResaleTransaction(
                    connection,
                    buyerId,
                    listingId,
                    payment
            );
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE ResaleListing SET status = 'sold' WHERE listing_id = ?"
            )) {
                statement.setInt(1, listingId);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE TicketOwnership SET ended_at = UTC_TIMESTAMP() WHERE ownership_id = ?"
            )) {
                statement.setLong(1, listing.sellerOwnershipId);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO TicketOwnership
                        (ticket_id, customer_id, acquired_transaction_id, acquired_listing_id)
                    VALUES (?, ?, ?, ?)
                    """)) {
                statement.setInt(1, listing.ticketId);
                statement.setInt(2, buyerId);
                statement.setInt(3, transactionId);
                statement.setInt(4, listingId);
                statement.executeUpdate();
            }
            return OperationResult.success(
                    "Resale listing purchased and ticket ownership transferred.",
                    new ResalePurchaseSummary(
                            transactionId,
                            listingId,
                            listing.ticketId,
                            listing.listingPrice
                    )
            );
        });
    }

    public static BigDecimal calculateCapPrice(
            BigDecimal faceValue,
            BigDecimal resaleCapMultiplier
    ) {
        if (faceValue == null || resaleCapMultiplier == null) {
            throw new IllegalArgumentException("Face value and resale cap are required");
        }
        return faceValue.multiply(resaleCapMultiplier).setScale(2, RoundingMode.HALF_UP);
    }

    private boolean lockActiveCustomer(Connection connection, int customerId) throws SQLException {
        String sql = """
                SELECT c.user_id
                FROM Customer c
                JOIN Users u ON u.user_id = c.user_id
                WHERE c.user_id = ? AND u.account_status = 'active'
                FOR UPDATE
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, customerId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private PaymentSnapshot lockCustomerPayment(Connection connection, int customerId)
            throws SQLException {
        String sql = """
                SELECT p.payment_info_id, p.card_number, p.card_holder_name,
                       p.expiry_date, p.billing_zip
                FROM Customer c
                JOIN Users u ON u.user_id = c.user_id
                JOIN PaymentInfo p ON p.customer_id = c.user_id
                WHERE c.user_id = ? AND u.account_status = 'active'
                ORDER BY p.payment_info_id
                LIMIT 1
                FOR UPDATE
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, customerId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                return new PaymentSnapshot(
                        rows.getInt("payment_info_id"),
                        rows.getString("card_number"),
                        rows.getString("card_holder_name"),
                        rows.getDate("expiry_date"),
                        rows.getString("billing_zip")
                );
            }
        }
    }

    private TicketForListing lockTicketForListing(Connection connection, int ticketId)
            throws SQLException {
        String sql = """
                SELECT t.status AS ticket_status, t.face_value,
                       p.status AS performance_status, p.date_time,
                       e.resale_cap_pct, own.ownership_id,
                       own.customer_id AS owner_customer_id
                FROM Tickets t
                JOIN Performance p ON p.performance_id = t.performance_id
                JOIN Event e ON e.event_id = p.event_id
                LEFT JOIN TicketOwnership own ON own.current_ticket_id = t.ticket_id
                WHERE t.ticket_id = ?
                FOR UPDATE
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, ticketId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                long ownershipId = rows.getLong("ownership_id");
                boolean ownershipMissing = rows.wasNull();
                int ownerCustomerId = rows.getInt("owner_customer_id");
                return new TicketForListing(
                        rows.getString("ticket_status"),
                        rows.getBigDecimal("face_value"),
                        rows.getString("performance_status"),
                        rows.getTimestamp("date_time").toLocalDateTime(),
                        rows.getBigDecimal("resale_cap_pct"),
                        ownershipMissing ? 0 : ownershipId,
                        ownerCustomerId
                );
            }
        }
    }

    private boolean activeListingExists(Connection connection, int ticketId) throws SQLException {
        String sql = """
                SELECT listing_id
                FROM ResaleListing
                WHERE active_ticket_id = ?
                FOR UPDATE
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, ticketId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private ListingForPurchase lockListingForPurchase(Connection connection, int listingId)
            throws SQLException {
        String sql = """
                SELECT rl.status AS listing_status, rl.ticket_id, rl.seller_ownership_id,
                       rl.listing_price, t.status AS ticket_status,
                       p.status AS performance_status, p.date_time,
                       seller.customer_id AS seller_customer_id,
                       (seller.current_ticket_id = rl.ticket_id) AS current_seller_ownership
                FROM ResaleListing rl
                JOIN Tickets t ON t.ticket_id = rl.ticket_id
                JOIN Performance p ON p.performance_id = t.performance_id
                JOIN TicketOwnership seller
                  ON seller.ownership_id = rl.seller_ownership_id
                WHERE rl.listing_id = ?
                FOR UPDATE
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, listingId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                return new ListingForPurchase(
                        rows.getString("listing_status"),
                        rows.getInt("ticket_id"),
                        rows.getLong("seller_ownership_id"),
                        rows.getBigDecimal("listing_price"),
                        rows.getString("ticket_status"),
                        rows.getString("performance_status"),
                        rows.getTimestamp("date_time").toLocalDateTime(),
                        rows.getInt("seller_customer_id"),
                        rows.getBoolean("current_seller_ownership")
                );
            }
        }
    }

    private int insertResaleTransaction(
            Connection connection,
            int buyerId,
            int listingId,
            PaymentSnapshot payment
    ) throws SQLException {
        String sql = """
                INSERT INTO Transactions
                    (customer_id, payment_info_id, payment_card_number,
                     payment_card_holder_name, payment_expiry_date, payment_billing_zip,
                     transaction_type, listing_id)
                VALUES (?, ?, ?, ?, ?, ?, 'resale', ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(
                sql,
                Statement.RETURN_GENERATED_KEYS
        )) {
            statement.setInt(1, buyerId);
            statement.setInt(2, payment.paymentInfoId);
            statement.setString(3, payment.cardNumber);
            statement.setString(4, payment.cardHolderName);
            statement.setDate(5, payment.expiryDate);
            statement.setString(6, payment.billingZip);
            statement.setInt(7, listingId);
            statement.executeUpdate();
            return JdbcSupport.requireGeneratedIntKey(statement, "resale transaction");
        }
    }

    private static final class PaymentSnapshot {
        private final int paymentInfoId;
        private final String cardNumber;
        private final String cardHolderName;
        private final Date expiryDate;
        private final String billingZip;

        private PaymentSnapshot(
                int paymentInfoId,
                String cardNumber,
                String cardHolderName,
                Date expiryDate,
                String billingZip
        ) {
            this.paymentInfoId = paymentInfoId;
            this.cardNumber = cardNumber;
            this.cardHolderName = cardHolderName;
            this.expiryDate = expiryDate;
            this.billingZip = billingZip;
        }
    }

    private static final class TicketForListing {
        private final String ticketStatus;
        private final BigDecimal faceValue;
        private final String performanceStatus;
        private final LocalDateTime performanceDateTime;
        private final BigDecimal resaleCapMultiplier;
        private final long ownershipId;
        private final int ownerCustomerId;

        private TicketForListing(
                String ticketStatus,
                BigDecimal faceValue,
                String performanceStatus,
                LocalDateTime performanceDateTime,
                BigDecimal resaleCapMultiplier,
                long ownershipId,
                int ownerCustomerId
        ) {
            this.ticketStatus = ticketStatus;
            this.faceValue = faceValue;
            this.performanceStatus = performanceStatus;
            this.performanceDateTime = performanceDateTime;
            this.resaleCapMultiplier = resaleCapMultiplier;
            this.ownershipId = ownershipId;
            this.ownerCustomerId = ownerCustomerId;
        }
    }

    private static final class ListingForPurchase {
        private final String listingStatus;
        private final int ticketId;
        private final long sellerOwnershipId;
        private final BigDecimal listingPrice;
        private final String ticketStatus;
        private final String performanceStatus;
        private final LocalDateTime performanceDateTime;
        private final int sellerCustomerId;
        private final boolean currentSellerOwnership;

        private ListingForPurchase(
                String listingStatus,
                int ticketId,
                long sellerOwnershipId,
                BigDecimal listingPrice,
                String ticketStatus,
                String performanceStatus,
                LocalDateTime performanceDateTime,
                int sellerCustomerId,
                boolean currentSellerOwnership
        ) {
            this.listingStatus = listingStatus;
            this.ticketId = ticketId;
            this.sellerOwnershipId = sellerOwnershipId;
            this.listingPrice = listingPrice;
            this.ticketStatus = ticketStatus;
            this.performanceStatus = performanceStatus;
            this.performanceDateTime = performanceDateTime;
            this.sellerCustomerId = sellerCustomerId;
            this.currentSellerOwnership = currentSellerOwnership;
        }
    }
}
