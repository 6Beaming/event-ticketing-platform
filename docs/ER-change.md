# Conceptual ER Diagram Changes

## Changes to make

### 1. Add `TicketOwnership`

Add a `TicketOwnership` entity with these conceptual attributes:

- `ownership_id`
- `acquired_at`
- `ended_at`

Show these relationships:

- One `Ticket` has one or more ownership periods over its lifetime; each ownership period belongs to exactly one ticket.
- One `Customer` can have zero or more ownership periods; each ownership period belongs to exactly one customer.
- Each ownership period is created by exactly one `Transaction`.
- A ticket can have at most one current ownership period, represented by an ownership whose `ended_at` is empty.

If the old diagram shows `resale_sale_id`, `resold_count`, or a direct permanent owner on `Ticket`, remove or replace those details with `TicketOwnership`.
- For ownership obtained through resale, also show the relationship to the corresponding `ResaleListing`.

Show that a `ResaleListing` is created from the seller's `TicketOwnership`
period. 

**Why:** A ticket keeps one identity while its owner may change through resale. Ownership periods preserve the complete ownership history and identify the current owner without overwriting earlier owners.These relationships identify both the seller and buyer and prevent an ownership record from being associated with a transaction for a different ticket.

### 2. Add `TicketCancellation` and `Refund`

Add a `TicketCancellation` entity with these conceptual attributes:

- `cancellation_id`
- `cancellation_date`
- `reason`

Show these relationships:

- A `Ticket` may have zero or one cancellation; every cancellation belongs to exactly one ticket.
- A cancellation applies to exactly one `TicketOwnership` period, and an ownership period may have at most one cancellation.
- Every cancellation is performed by exactly one `User`. The user may be a customer or an organizer.

Add a `Refund` entity with these conceptual attributes:

- `refund_id`
- `amount`
- `refund_date`

Connect `Refund` with `TicketCancellation`.

**Why:** Cancellation and refund are separate events with their own facts. Separate entities preserve who cancelled which ownership period and avoid unrelated nullable attributes on `Ticket`. Cancellation and refund are historical events with their own facts and should not be stored as unrelated nullable attributes on `Ticket`.

### 3. Update `User`, `Customer`, and `Organizer`

Keep `Customer` and `Organizer` as subtypes of `User`. Add these attributes to
`User`:

- `user_role`
- `account_status`
- `deleted_at`

Use `user_role` as the subtype discriminator.

**Why:** The role identifies the appropriate user subtype, while account status
and deletion time allow user deletion to be represented without destroying
orders, events, reviews, or ownership history.

### 4. Add `CustomerRestriction`

Add `CustomerRestriction` with:

- `restriction_id`
- `restriction_reason`
- `details`
- `started_at`
- `ended_at`

Connect it with `Customer`.

**Why:** The project requires possible scalpers to be flagged and prohibited.
This entity preserves the restriction history instead of storing only a current
Boolean flag.

### 5. Add the transaction payment snapshot

Keep the relationship between `Transaction` and `PaymentInfo`. Add these
historical payment attributes to `Transaction`:

- `payment_card_number`
- `payment_card_holder_name`
- `payment_expiry_date`
- `payment_billing_zip`

**Why:** A later edit to a customer's saved payment information must not change
the payment details recorded for an older transaction.

### 6. Record the ticket's price tier at sale time

Add `tier_code` to `Ticket` and show its relationship to `PriceTier` for the
ticket's performance.

**Why:** The ticket already preserves its face value, but reports by price tier
also need the original tier identity even if section assignments change later.

### 7. Update `ResaleListing`

Add `cap_price_at_listing` to `ResaleListing` and show the relationship from the
listing to the seller's `TicketOwnership` period.

Do not add the SQL columns `seller_ownership_id` or `active_ticket_id` as
conceptual attributes; represent the seller through the relationship instead.

**Why:** The ownership relationship records who created the listing, and the cap
snapshot preserves the price limit that applied when the listing was created.

### 8. Record who cancelled a performance

Show a `cancelled by` relationship between `Performance` and `Organizer`. Keep
the cancellation date and reason with `Performance`.

**Why:** The database must preserve which organizer performed the cancellation,
not only that the performance became cancelled.
