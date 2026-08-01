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

**Why:** A ticket keeps one identity while its owner may change through resale. Ownership periods preserve the complete ownership history and identify the current owner without overwriting earlier owners.



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

Show that a `TicketCancellation` may have zero or one `Refund`, while every refund belongs to exactly one cancellation.

Move ticket-level cancellation details such as cancellation date, reason, and cancelling user out of `Ticket` if they appear there. The ticket's active/cancelled status may remain.

**Why:** Cancellation and refund are separate events with their own facts. Separate entities preserve who cancelled which ownership period and avoid unrelated nullable attributes on `Ticket`.
