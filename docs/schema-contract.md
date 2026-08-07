# Schema Contract and Foundation Conventions

This document is the shared contract for the Java application, SQL queries,
reports, and deterministic development data. Any schema change must be reviewed
by both members because it can affect every layer of the project.

## Agreed conventions

- SQL identifiers use `snake_case`; Java types use `PascalCase`, and Java
  members use `camelCase`.
- Application timestamps are interpreted in UTC. JDBC connections set
  `serverTimezone=UTC`, and development data uses relative MySQL timestamps so
  future and past test cases remain usable.
- Money uses `DECIMAL(10,2)` in MySQL and `BigDecimal` in Java. Floating-point
  types must not be used for prices, face values, listing prices, or refunds.
- Status and role values use the exact lowercase enum values declared in
  `sql/schema.sql`.
- Users are history-preservingly deactivated: `account_status` becomes
  `deleted`, `deleted_at` is set, and identifying profile/payment values are
  anonymized. Historical keys and business records are retained.
- Every SQL value originating from terminal input is sent through a prepared
  statement.
- Multi-step writes run through the shared transaction helper. A non-success
  operation result or SQL failure rolls back the entire transaction.
- Stored payment details and all development card values are fictional. Full
  card numbers are never displayed by the terminal.
- The assignment requires an email but does not define duplicate-email behavior.
  MyTix treats email as a unique account contact value, checks availability
  during entry, and keeps the `Users.email` constraint as final protection.
- A customer is an adult when their eighteenth birthday is not after the
  current date.

## Transaction-side table map

| Feature | Tables and keys | Required existing records | Rows locked before changes |
|---|---|---|---|
| Customer profile | `Users(user_id)`, `Customer(user_id)`, `PaymentInfo(payment_info_id)` | None | Existing `Users` and `PaymentInfo` rows during deactivation |
| Reserved booking | `Transactions(transaction_id)`, `Tickets(ticket_id)`, `TicketOwnership(ownership_id)`, `PerformanceSeats(performance_seat_id)` | Active customer/payment, scheduled performance, tier, reserved performance seats | Requested `PerformanceSeats`, in stable seat order |
| General-admission booking | The booking tables above plus `GeneralAdmissionCapacity(performance_id, venue_id, section_name)` | Active customer/payment, scheduled performance, tier, GA capacity row | Matching `GeneralAdmissionCapacity` row |
| Ticket cancellation | `Tickets`, `TicketOwnership`, `Transactions`, `Performance`, `ResaleListing`, `TicketCancellation`, `Refund` | Current ownership, eligible performance, active ticket | Ticket, ownership, purchase, performance, active listing, and inventory row |
| Performance cancellation | `Performance`, `Event`, `Tickets`, `TicketOwnership`, `ResaleListing`, `TicketCancellation`, `Refund` | Organizer owns the event | Performance followed by affected ticket, ownership, listing, and inventory rows |
| Resale | `ResaleListing(listing_id)`, `TicketOwnership`, `Tickets`, `Transactions` | Active ticket and current ownership | Ticket, current ownership, then active listing |
| Customer restriction | `CustomerRestriction(restriction_id)` plus purchase/listing history | Customer and report result | Current restriction row and customer row before a restriction change |

Insert prerequisites follow the foreign-key order: user, subtype, payment;
transaction, ticket, ownership; cancellation, refund; and ownership, listing,
resale transaction, transferred ownership.

## Organizer and discovery table map

| Feature | Tables and keys | Contract |
|---|---|---|
| Organizer profile | `Users(user_id)`, `Organizer(user_id)` | Both rows are created atomically with matching `user_role='organizer'`. |
| Taxonomy | `Segment(segment_id)`, `Genre(genre_id)` | An event stores a valid genre; its segment is derived through `Genre.segment_id`. |
| Event and billing | `Event(event_id)`, `ArtistsTeams(artist_id)`, `BillingOrder(event_id, artist_id)` | An event has one organizer, at least one artist/team, and unique positive billing ranks. |
| Performance | `Performance(performance_id)` | A performance belongs to one event and venue and has a date/time and status. |
| Pricing | `PriceTier(performance_id, tier_code)`, `SectionTierAssignment(performance_id, venue_id, section_name)` | A completed setup has at least two positive tiers and assigns every venue section exactly once to a tier of that performance. |
| Reserved inventory | `Section`, `SeatRows`, `Seats`, `PerformanceSeats` | Only reserved-section seats appear in performance-seat inventory. Sold state is derived from an active `Tickets` reference; blocked state is performance-specific. |
| General admission | `Section`, `GeneralAdmissionCapacity` | A GA row starts with its section standing capacity and keeps a nonnegative remaining capacity. |
| Location search | `Venue`, `Performance`, pricing/inventory tables | Coordinates, address, city, postal code, date, price, and available inventory remain queryable and indexed. |
| Reviews | `Reviews(customer_id, performance_id)` plus ticket/ownership history | Ratings are 1-5 and uniqueness is declarative; attendance and recency are checked transactionally in Java. |

## Schema-to-requirement comparison

`sql/schema.sql` represents every entity and relationship required for the
foundation. The database declaratively protects keys, foreign keys, subtype
roles, section types, rating ranges, nonnegative capacities/prices, one current
ownership, one active listing, and reserved-seat double sales.

The following cross-row or time-dependent rules remain Java transaction
responsibilities, as detailed in `docs/business-logic-constraints.md` and
`docs/java-enforced-trigger.md`:

- legal age and exactly one matching user subtype;
- at least one artist and performance per event;
- complete section-to-tier coverage before a performance is saleable;
- ticket tier/face value consistency with the assigned section tier;
- safe seat blocking, GA decrements, and inventory release;
- cancellation authorization, deadline, and full-refund amount;
- resale ownership and cap calculation;
- review attendance/recency and customer-restriction checks.

## Stable sample-data IDs

The complete deterministic sample-data generator reserves these ranges and
representative edge-case IDs:

| Record | Stable ID |
|---|---:|
| Organizers | `1001`-`1005` |
| Customers | `2001`-`2100` |
| Customer payments | `2101`-`2200` |
| Venues | `3001`-`3008` |
| Segments / genres | `4001`-`4003` / `4101`-`4106` |
| Artists and teams | `4201`-`4215` |
| Events | `5001`-`5020` |
| Performances | `6001`-`6060` |
| Consecutive/nonconsecutive availability example | `6001` |
| Fewer-than-seven-days sold example | `6002` |
| Sold-out examples | `6003`, `6007`, `6019` |
| Low-sell-through example | `6004` |
| Organizer-cancelled examples | `6058`, `6060` |
| Purchase orders | `7001`-`7320` |
| Tickets | `8001`-`8960` |
| Reserved and GA representative tickets | `8001`, `8002` |
| Ticket with two resale transfers | `8007` |

## Foundation acceptance examples

- Accept an adult profile and reject an under-18 profile without inserting any
  row.
- Commit a successful multi-step callback and roll back a callback returning a
  validation failure.
- Reject nonexistent organizer, taxonomy, artist, venue, performance, or tier
  identifiers with a clear result status.
- Reject duplicate artist billing ranks and missing/duplicate section
  assignments before executing inserts.
- Return reserved inventory with section, row, seat, tier, price, and
  available/sold/blocked status.
- Return general-admission inventory with total, sold, and remaining counts.
