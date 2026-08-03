# Java-Enforced Trigger Logic

The following triggers were removed from `sql/schema.sql` because they perform
multi-step business operations that should run inside explicit Java-managed
transactions. The Java implementation must lock the relevant rows, validate the
complete operation, apply every related update, and roll back everything when a
check fails.

## Removed triggers

### `trg_tickets_ga_capacity_ins`

This trigger checked and reduced general-admission capacity when a ticket was
inserted. The Java booking transaction must instead:

1. Lock the matching `GeneralAdmissionCapacity` row with `SELECT ... FOR UPDATE`.
2. Confirm that enough capacity remains for the complete requested quantity.
3. Insert the order with an immutable payment snapshot and insert every ticket
   with its price tier at the time of sale.
4. Reduce `remaining_capacity` by the number of tickets sold.
5. Commit all changes together.

The existing nonnegative capacity check remains the database-level safeguard.

### `trg_tickets_ga_capacity_upd`

This trigger changed general-admission capacity when a ticket's cancellation
status changed. The Java cancellation transaction must instead lock the ticket
and capacity row, verify the cancellation rules, record the cancellation and
refund, restore the released capacity, and commit those changes together.

### `trg_resalelisting_cap_ins`

This trigger compared a resale listing price with the ticket face value and the
event resale cap. The Java listing transaction must instead:

1. Lock the ticket, its current ownership record, and any active listing.
2. Confirm that the requester owns an active ticket.
3. Read the ticket's face value and the event's current resale cap.
4. Reject a listing price above `face_value * resale_cap_pct`.
5. Insert the listing with the seller's `ownership_id` and the calculated
   `cap_price_at_listing` snapshot.
6. Commit only when every check succeeds.

When a resale is purchased, the Java transaction must copy the listing ID into
`TicketOwnership.acquired_listing_id`. The schema then verifies that the resale
transaction, listing, ticket, buyer, and new ownership record all agree.

Reserved-seat double sales are prevented by the unique generated
`Tickets.active_reserved_seat_ref` value, which is enforced atomically by
InnoDB. Section subtypes are enforced declaratively by `Section` checks and
typed foreign keys.
