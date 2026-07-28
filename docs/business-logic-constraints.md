# Business Logic Constraint Map

This file is internal design checklist. It does not enforce any rules by itself and is not an additional required submission file. It records how requirements from the project PDF should be divided between the MySQL schema and Java transactions, helping the implementation and tests cover every rule consistently.

The future implementation should keep SQL operations inside the named transaction boundaries and roll back the whole action whenever one check fails.

| Area | Requirement | Database guard | Java and transaction guard |
|---|---|---|---|
| Profiles | A user must be at least 18 years old when an account is created. | Store date of birth and reject impossible dates. | Calculate age at creation time before inserting the profile. |
| Venue inventory | A section is either reserved seating or general admission, never both. | Use a section type and checks for the matching capacity fields. | Route reserved sections to row/seat setup and general-admission sections to capacity setup. |
| Venue inventory | Section names are unique per venue, row names per section, and seat numbers per row. | Make section name unique within a venue, row name unique within a section, and seat number unique within a row. | Resolve the complete venue-section-row-seat identity before inventory changes. |
| Event setup | Each event has an organizer, segment, genre, resale cap, artist, and performance. | Use foreign keys for organizer and taxonomy; use unique event-artist billing order where appropriate. | Create or complete required child records as an event setup transaction. |
| Pricing | Every venue section is assigned to exactly one price tier for each performance. | Use a unique `(performance, section)` assignment referencing a tier of that performance. | Before publishing sales, verify that every section of the performance venue is assigned once. |
| Pricing | A future tier price can change only before any ticket in that tier has been sold. | Keep ticket face value immutable so past sales do not change with a tier. | Lock the tier and its ticket set, verify future time and zero sales, update, then commit. |
| Seat inventory | A sold seat cannot be blocked; only an available seat can be blocked or unblocked. | Keep one performance-seat inventory status. | Lock that inventory row and allow blocking only from available; cancellation is the only sold-to-available path. |
| Booking | A reserved seat cannot be sold twice for the same performance. | Use a unique performance-seat key on active inventory/ticket allocation. | Lock requested inventory rows in a stable order, re-check availability, create the order and tickets, then commit. |
| Booking | General-admission sales cannot exceed the section capacity. | Keep nonnegative capacity/count checks for each performance-section inventory row. | Lock the row and atomically compare the requested quantity with remaining capacity. |
| Cancellations | Only the purchasing customer can cancel a ticket, at least seven days before the performance. | Preserve ticket/order status and cancellation/refund history rather than deleting rows. | Lock ticket and performance, verify the customer and deadline, release inventory, record refund and cancellation, then commit. |
| Cancellations | Only the event organizer can cancel its performance; every active ticket is refunded. | Preserve a performance cancellation record and individual refund status. | Lock the performance, verify organizer ownership, cancel/refund all affected tickets, close their allocations, and keep the performance unsellable. |
| Resale | Only an owned active ticket can be listed, and its price cannot exceed the event cap. | Allow at most one active listing per ticket and constrain prices to positive values. | Lock the ticket, current ownership, and listing; check the calculated cap before creating the listing. |
| Resale | A sold listing transfers ownership once and preserves the full ownership history. | Store append-only ownership periods/transfers and a terminal status for sold listings. | Lock listing and ticket, verify both are still active, create resale payment/order records, append ownership, close listing, then commit. |
| Reviews | A customer may review once per attended performance after it occurs, with ratings from 1 to 5. | Check rating ranges and make `(customer, performance)` unique. | Verify a past performance and a non-cancelled ticket held by the reviewer before inserting event/venue ratings and text. |
| Platform controls | Customers matching the possible-scalper rule must be flagged and prohibited. | Implement the rolling-year calculation in SQL and retain flag history. | Refresh/check the flag before booking and resale actions and reject prohibited customers. |

## Required transaction boundaries

### Booking

1. Start a transaction.
2. Lock the requested reserved-seat rows in a stable order, or lock the general-admission inventory row.
3. Verify the performance is active, the customer is allowed, and all inventory is available.
4. Insert the order, tickets, immutable face values, payment snapshot, and initial ownership records.
5. Mark reserved seats sold or reduce general-admission availability.
6. Commit; roll back the entire booking if any requested ticket cannot be sold.

### Customer cancellation

1. Lock the ticket, current ownership, order, performance, and inventory record.
2. Verify the requester placed the order and the performance is at least seven days away.
3. Close any active resale listing, record cancellation and refund, and release inventory.
4. Commit all changes together.

### Performance cancellation

1. Lock the performance and verify the requester manages its event.
2. Mark the performance cancelled.
3. Close active listings and record cancellation/refund data for every active ticket.
4. Close the corresponding allocations, keep all inventory unsellable because the performance is cancelled, and commit as one transaction.

### Resale purchase

1. Lock the active listing, ticket, and current ownership row.
2. Verify the buyer differs from the seller and neither ticket nor performance is cancelled.
3. Insert the resale order/payment snapshot, close the listing, end prior ownership, and append new ownership.
4. Commit once; a competing buyer must find the listing already closed.

### Tier price update

1. Lock the future performance tier.
2. Check under the same transaction that no ticket was sold from that tier.
3. Update the tier price and commit; otherwise leave the old price unchanged.

## Important design principle

Java validation provides clear messages, but it is not sufficient for inventory safety because two application sessions may act at the same time. Uniqueness constraints, foreign keys, checks, and transactional row locks in MySQL must remain the final protection against double sales and partial updates.
