# MyTix User Manual

## 1. What MyTix can do

MyTix is a terminal-based Java application backed by MySQL 8. It supports customer and organizer profiles, events, performances, performance-specific pricing, reserved and general-admission inventory, booking, cancellation, capped resale, ownership histories, attendance reviews, Q1-Q7 searches, R1-R9 reports, and organizer pricing recommendations.

The sample names, addresses, emails, card details, events, and sales are fictional and are intended only for this database project.

## 2. Requirements

- MySQL 8 running locally or on a reachable server
- A MySQL account that can create and change the selected database
- Java Development Kit 17 or newer
- Bash for `run.sh` (Git Bash, WSL, macOS, or Linux)
- The supplied MySQL Connector/J and OpenNLP JAR files in `lib/`

## 3. Configure the database connection

Copy `config.properties.example` to `config.properties` and enter the database URL, user, and password for your MySQL instance. Do not commit the real configuration file.

The terminal shows `Database: CONNECTED` when startup succeeds. If it shows `OFFLINE`, use main-menu option `10`, check the displayed target and last error, then enter `R` to reconnect.

Common causes of connection failure are a stopped MySQL service, the wrong port or database name, an invalid user/password, or a missing permission.

## 4. Initialize and run

Use a clean database and follow this exact order:

1. Execute `sql/schema.sql` in MySQL 8.
2. Execute `sql/load.sql` in the same database.
3. From the project root, run `./run.sh`.

`schema.sql` creates the tables and constraints. `load.sql` creates the complete deterministic sample dataset. `run.sh` compiles the Java source and starts the terminal. No manual insert or code edit is required.

If you need a clean restart, run `sql/drop.sql`, then repeat the three steps above.

At the main menu, type the number of an area and press Enter. Type `0` to go back or exit. After a result, press Enter to continue. Required calendar dates use `YYYY-MM-DD`. Only the creation of a new performance requires an exact date and time, in `YYYY-MM-DD HH:mm` format.

## 5. Understand results and validation

Every operation begins with a status:

| Status | Meaning |
|---|---|
| `SUCCESS` | The operation finished and committed. |
| `INVALID_INPUT` | A value has the wrong format or range. Correct it and retry. |
| `NOT_FOUND` | The requested ID or record does not exist. |
| `CONFLICT` | Current data makes the action impossible, such as a sold seat or insufficient GA capacity. |
| `FORBIDDEN` | A business rule rejects the action, such as a late cancellation or a restricted customer booking. |
| `DATABASE_ERROR` | MySQL rejected the operation or the connection failed. No partial mutation should remain. |

Blank optional fields are shown in the prompt. For comma-separated input, do not repeat an ID. Money is displayed in dollars and percentages are clearly labelled.

## 6. Sample data IDs

The sample data is regenerated relative to the date on which `sql/load.sql` runs, but these identifier ranges and special cases are stable on a fresh load.

### 6.1 Identifier ranges

| Data | IDs | Useful purpose |
|---|---:|---|
| Organizers | 1001-1005 | Create events, view managed-event sales, and group organizer reports |
| Customers | 2001-2100 | Profiles, histories, booking, resale, reviews, and customer reports |
| Payment records | 2101-2200 | Used automatically by purchases |
| Venues | 3001-3008 | Event performances, location queries, and pricing |
| Segments | 4001-4003 | 4001 Music, 4002 Arts, 4003 Sports |
| Genres | 4101-4106 | 4101 Rock, 4102 Pop, 4103 Musical, 4104 Comedy, 4105 Basketball, 4106 Hockey |
| Artists/teams | 4201-4215 | Event billing order |
| Events | 5001-5020 | Reports, reviews, performances, and toolkit history |
| Performances | 6001-6060 | Inventory, booking, cancellation, Q4-Q7, and reports |
| Original orders | 7001-7320 | Customer history and sales reports |
| Tickets | 8001-8960 | Cancellation, resale, ownership, and R8 |

### 6.2 Stable cases to demonstrate features

| IDs/values | What they demonstrate |
|---|---|
| Venue 3001-3003, postal `M5J 2X2`, coordinates `43.643500, -79.379100` | Toronto location searches |
| Exact address `40 Bay Street` | Q3 exact-address search |
| Performance 6001 | Mixed reserved/GA inventory, sold and unsold seats, blocked seats, active resale, Q6, and Q7 |
| Performance 6002 | Future performance inside the seven-day cancellation boundary |
| Performances 6003, 6007, 6019 | Sold-out cases |
| Performances 6004, 6022, 6025 | Below-25% sell-through cases |
| Performances 6006, 6013 | Future scheduled performances with no ticket history; pricing can be replaced |
| Performances 6058, 6060 | Organizer-cancelled cases |
| Customers 2001, 2002 | R4 possible-scalper examples; booking and resale actions are restricted after R4 synchronization |
| Customers 2086-2097 | Customer cancellation examples for R6 |
| Customer 2011 and performance 6003 | An attended performance available for a new review on a fresh load |
| Ticket 8001 | Active reserved ticket with an active listing at its resale cap |
| Ticket 8002 | Active GA ticket with an active listing at its resale cap |
| Ticket 8004 | Future unlisted reserved ticket, suitable for customer cancellation; its owner may be restricted from resale |
| Ticket 8006 | Near-deadline ticket whose customer cancellation should be rejected |
| Ticket 8007 | Ticket with two completed ownership transfers |
| Tickets 8133-8135, 8139-8141 | Completed resale examples |
| Tickets 8136, 8137, 8142, 8143 | Withdrawn listing examples |

IDs created by a new operation are printed by the terminal and are not part of the fixed ranges above.

> You do not have to memorize every ID. Several menus let you discover valid data before changing it. For example, before booking reserved seats, use main option `4`, then submenu option `1`, enter performance `6001`, and copy an available row and seat from the displayed list. Before resale, use `5 -> 1` to list a customer's current tickets and `5 -> 2` to list active resale tickets for a performance. A fresh `sql/load.sql` restores all documented examples.

## 7. User profiles — main option 1

| Submenu | Use | Required input | Result |
|---:|---|---|---|
| 1 | Create customer profile | name, address, unique email, birth date, card number, card-holder name, expiry date, billing postal/ZIP | New customer ID |
| 2 | Create organizer profile | name, address, unique email, birth date | New organizer ID |
| 3 | View customer profile | customer ID | Profile, account status, masked card, expiry, billing postal/ZIP |
| 4 | Deactivate user profile | active user ID, then exact confirmation `DEACTIVATE` | Personal fields are anonymized; sales and ownership history remain |
| 5 | View customer order and ticket history | customer ID | Orders, tickets, venue, price, current/ended ownership, cancellation, and refund |

The terminal checks email uniqueness and validates each profile and payment field. Deactivation is not physical deletion.

## 8. Organizer events and performances — main option 2

| Submenu | Use | Required input | Result |
|---:|---|---|---|
| 1 | Create event with billing | organizer ID, genre ID, title, optional description, resale-cap multiplier (default 1.20), artist/team count, each artist/team ID and unique billing rank | New event ID |
| 2 | Add performance | event ID, venue ID, `YYYY-MM-DD HH:mm` | New performance ID |
| 3 | Update event resale cap | event ID, multiplier at least 1.00 | New cap used for future listings |
| 4 | View managed events and sales | organizer ID | Event/performance status, original sales, refunds, resales, and transaction details |

The date/time prompt is necessary here because two performances may occur on the same day at different times. Search and report ranges do not require a time.

Example event creation inputs are organizer `1001`, genre `4101`, one artist/team `4201`, and billing rank `1`. The terminal prints the new event ID; use that returned ID if you then add a performance.

## 9. Performance pricing and inventory — main option 3

| Submenu | Use | Required input | Result |
|---:|---|---|---|
| 1 | View reserved inventory | performance ID | Section, row, seat, tier, price, blocked flag, and state |
| 2 | View general-admission inventory | performance ID | Section, tier, price, total, remaining, and sold |
| 3 | Configure all tiers and sections | eligible performance ID, tier count (minimum 2), unique tier codes/prices, one tier for every displayed section | Complete replacement summary and section map |
| 4 | Update one tier price | performance ID, tier code, positive price | Updated future selling price |
| 5 | Block reserved seat | performance ID, row, seat number | Seat becomes unavailable |
| 6 | Unblock reserved seat | performance ID, row, seat number | Seat becomes available if not sold |

Use performance `6006` or `6013` to demonstrate full pricing replacement because each is scheduled, future, and has no ticket history. Every configured tier must be assigned to at least one section, and every venue section must receive a tier. Use performance `6001` for read-only inventory examples. A sold seat cannot be made available by unblocking it.

## 10. Ticket booking and cancellation — main option 4

### 10.1 View and book reserved seats

1. Choose `4 -> 1` and enter performance `6001`.
2. Note one or more displayed available seats. The table contains the section, row, seat number, tier, and price.
3. Return and choose `4 -> 2`.
4. Enter an active, unrestricted customer ID, then performance `6001`.
5. Enter seats as `Row Seat#`, separated by commas, for example `A 1, A 2`. Use the actual available values shown in step 1.

On success, the terminal prints one transaction ID, the new ticket IDs, and the total. The complete booking commits atomically. If another purchase has taken a seat, no part of this booking is saved.

### 10.2 Book general admission

Choose `4 -> 3`, enter an active unrestricted customer, performance `6001`, one of the displayed GA section names, and a positive quantity. The terminal checks and locks the capacity. A request larger than the remaining capacity returns `CONFLICT` and does not create a partial order.

### 10.3 Cancel customer tickets

Choose `4 -> 4`, enter one or more ticket IDs separated by commas, and optionally enter a reason. Ticket `8004` is a fresh-load success example because its performance is more than seven days away. Ticket `8006` is a rejection example because its performance is within seven days.

The operation cancels all requested tickets or none. On success it closes current ownership, withdraws an active listing if necessary, records cancellation, and refunds the current owner's acquisition price. A ticket can be cancelled only once.

### 10.4 Cancel an organizer's performance

Choose `4 -> 5`, enter the performance ID and an optional reason. The operation marks the performance cancelled, cancels every active ticket, ends active listings and ownerships, and creates refunds in one transaction.

This menu currently does **not** authenticate the operator and does not ask for an organizer ID. It relies on the organizer already associated with the event. This is a known security limitation. A safer version should ask for and verify the organizer ID at minimum; the preferred improvement is an authenticated organizer session with an ownership check and a confirmation step.

## 11. Ticket resale and histories — main option 5

| Submenu | Use | Required input | Result |
|---:|---|---|---|
| 1 | View currently owned tickets | customer ID | Active ticket IDs, performance IDs, and status; use this to find candidates |
| 2 | View available resale tickets | performance ID | Ticket ID, seller customer ID, and listing price |
| 3 | Resale a ticket | seller ID, currently owned active ticket ID, listing price | Listing ID, price, and cap |
| 4 | Withdraw listing | seller ID, actively listed ticket ID | Listing becomes withdrawn |
| 5 | Purchase resale ticket | buyer ID, actively listed ticket ID | Resale transaction and listing IDs, ticket ID, purchase price |
| 6 | View ticket ownership history | ticket ID | Every owner, acquisition transaction/price, listing, and ownership interval |

The submenu label “Resale a ticket” means “list a ticket for resale.” The seller must be the current owner, and the price cannot exceed the event cap applied to the ticket's face value. Seller and buyer must differ. Restricted customers cannot create or buy listings. Use ticket `8007` with `5 -> 6` to see an ownership chain with two transfers; use `8001` or `8002` with `5 -> 2` on its performance to see a current cap-priced listing.

Because a successful sale or withdrawal changes the sample state, discover current candidates with `5 -> 1` and `5 -> 2` instead of assuming that an example remains active after earlier demonstrations.

## 12. Attendance reviews — main option 6

| Submenu | Use | Required input | Result |
|---:|---|---|---|
| 1 | Submit review | customer ID, one displayed eligible performance ID, event rating 1-5, venue rating 1-5, nonblank comment | One review for that customer/performance |
| 2 | View submitted reviews | customer ID | Event, venue, ratings, date, and comment |

The system first prints completed performances that the customer attended and may still review. On a fresh load, customer `2011` can review performance `6003`. The same customer cannot review the same performance twice, cannot review without attendance, and cannot review an ineligible old or incomplete performance.

## 13. Searches Q1-Q7 — main option 7

Search dates are calendar dates in `YYYY-MM-DD`. Where the examples say `LOAD_DATE + N`, calculate the date from the day on which `sql/load.sql` was executed. Exact result rows can change when data or the load day changes, so the expected-result column describes the invariant result shape and special case.

| Q | Purpose and example input | Output and expected behavior |
|---:|---|---|
| Q1 | Latitude `43.643500`, longitude `-79.379100`, radius 50 km (or blank for default); sort by distance, lowest price ascending, or descending | Upcoming performances inside the radius, with venue, distance, lowest price, availability, and sold-out state; chosen sort is respected |
| Q2 | Postal code `M5J 2X2` | Upcoming performances whose venue matches the first three normalized characters (`M5J`) |
| Q3 | Exact address `40 Bay Street` | Upcoming performances for every venue at that normalized exact address |
| Q4 | Choose a location mode, use a range such as load date +1 through +121, and a positive minimum availability | Only upcoming performances in the location/date range with at least that many total available tickets |
| Q5 | Example: load date +1 through +180, Toronto, Music, Rock, price 0-500, minimum 1, reserved | Performances satisfying every supplied filter; blank optional filters are ignored; price/availability apply to reserved inventory in this example |
| Q6 | Performance `6001` | One row per section/tier with price, available or remaining count, sold count, and blocked reserved count |
| Q7 | Performance `6001`, group size `4`, no maximum budget | Best available tier and seats/capacity; result states whether reserved seats are consecutive and may use a nonconsecutive fallback |

Q1 retains sold-out performances rather than hiding them. Q4 and Q5 calculate reserved and GA availability without double-counting. Q7 returns no result when no single tier can satisfy the entire group or the budget.

## 14. Reports R1-R9 — main option 8

Report date ranges are inclusive `YYYY-MM-DD` calendar dates; no specific time is required. For a complete historical demonstration, use `2000-01-01` through today's date unless the runbook gives a narrower time-relative window.

| R | Input example | Output and fresh-load expectation |
|---:|---|---|
| R1 | Start `2000-01-01`, end today; group by city, or choose city Toronto for venue detail | Original ticket count and face-value revenue by selected location grouping; Toronto contains several venues |
| R2 | Choose segment/genre and geographic grouping: country, country/city, or country/city/venue | Event and performance counts for the selected rollup |
| R3 | Overall, country, or country/city ranking | Organizers ordered by original face-value ticket revenue with stable tie handling |
| R4 | No additional input; choose report option 4 | The rolling 12-month report evaluates each customer/city group. Customers `2001` and `2002` qualify in Toronto on a fresh load: each purchased 12 tickets and listed 7; their current restrictions are synchronized |
| R5 | Option 1 takes a date range; option 2 needs no further input | Customer ranking by order count in the period, or one-year rankings within each city |
| R6 | Choose customer cancellations or organizer cancellations; no date input | The fixed preceding-year window includes customers `2086-2097` and organizer-cancelled performances `6058` and `6060` on a fresh load |
| R7 | Options 1/2 need no date; option 3 asks for year and month | Sell-through by performance or tier; for the correct generated month, `6003`, `6007`, `6019` are sold out and `6004`, `6022`, `6025` are below 25% |
| R8 | Option 1 needs no date; option 2 takes a date range covering generated resales | Per-event resale statistics and the top 10 events by completed-resale volume; the fresh load contains eight completed resales |
| R9 | Event IDs `5001-5010`, up to 10 phrases | OpenNLP extracts and ranks repeated noun phrases separately for each selected event; events without usable phrases are handled clearly |

R4 has an operational effect: after it runs, a currently qualifying customer is prohibited from booking, listing, and purchasing resale tickets. This is why customers `2001` and `2002` should be used for the R4 demonstration rather than a normal booking demonstration.

## 15. Organizer toolkit — main option 9

### 15.1 Recommend pricing

Enter a genre ID, city, and planned venue capacity. A useful sample is genre `4101` (Rock), city `Toronto`, capacity `120`.

The result explains whether it used primary comparables, widened comparables, or a fallback. It lists the comparable performances, suggested tier count, low-to-high tier prices, and capacity share for each tier. Capacity shares always total 100%.

Primary comparables are completed performances in the same genre and city from the last 24 months with capacity within 25% of the target. If there are too few, the search expands to 36 months, any city, and capacity within 50%. If there is still no usable history, the fallback is:

| Tier | Capacity share | Price |
|---|---:|---:|
| Low | 50% | $60 |
| Middle | 30% | $100 |
| High | 20% | $150 |

### 15.2 Estimate revenue impact

Enter genre, city, capacity, current price, proposed price, and an optional comparison band. Example: capacity `120`, current price `100`, proposed price `120`, band `20`.

The result reports comparable sample sizes, expected sell-through at each price, expected revenue at each price, and estimated change. This is a historical heuristic, not a guaranteed forecast.

## 16. Complete example workflow

This read-heavy workflow is safe on a fresh load and demonstrates how to discover data before a mutation:

1. Run `sql/schema.sql`, `sql/load.sql`, and `./run.sh`.
2. Confirm the main menu says `Database: CONNECTED`.
3. Choose `4 -> 1`; enter performance `6001`; note an available row and seat.
4. Return to the main menu and choose `7 -> 6`; enter `6001`; compare the section/tier totals with the inventory table.
5. Choose `7 -> 7`; enter `6001`, group size `4`, and leave the budget blank; note whether the selection is consecutive.
6. Choose `5 -> 6`; enter ticket `8007`; read the original owner and two resale ownership intervals.
7. Choose `8 -> 4`; confirm customers `2001` and `2002` meet the threshold in Toronto and that restrictions are synchronized.
8. Choose `9`; request a recommendation for genre `4101`, Toronto, capacity `120`; confirm the shares total 100% and read the comparable explanation.
9. Return to the main menu and enter `0` to exit.

For a write demonstration, reload the sample data first, use `4 -> 1` to discover a currently available seat, then use `4 -> 2` with an active customer other than the restricted examples. The new transaction and ticket IDs are printed after commit.

## 17. System limitations

- **No authentication or session identity.** The terminal trusts entered IDs. It cannot prove that the person entering an organizer, customer, or ticket ID owns that account or resource.
- **Organizer cancellation needs stronger security.** The current cancellation prompt asks for the performance ID only. It should at minimum request and verify the organizer ID, and preferably derive that ID from an authenticated organizer session with role and ownership checks.
- **Coursework payment storage.** Fictional card values are stored in MySQL. The system does not integrate a payment processor, tokenization, PCI controls, or chargeback handling.
- **Manual numeric identifiers.** Many operations require exact IDs. Lookup menus reduce this problem for inventory, ownership, and resale, but there is no universal name search or picker.
- **UTC date interpretation.** Date ranges are consistent, but venue-specific time zones and daylight-saving display are not modeled.
- **Simple location matching.** Postal prefixes and exact normalized addresses are not geocoded, and radius search is straight-line distance.
- **Rule-based scalper flag.** The required 10-ticket/more-than-half threshold is useful for demonstration but may flag legitimate users or miss coordinated behavior.
- **Data-dependent analytics.** Query, report, noun-phrase, and toolkit results depend on the loaded data. The documented sample cases describe one fresh generated dataset, not a promise for arbitrary replacement data.
- **Terminal-only deployment.** There is no web/mobile UI, email notification, external payment, horizontal scaling, or production monitoring.

## 18. Possibilities for improvement

1. Add authentication, secure password or identity-provider login, role-based authorization, and resource ownership checks. Sensitive actions such as organizer cancellation should require confirmation and an audit record.
2. Replace raw payment details with payment-provider tokens, encrypt sensitive data, and use managed secrets.
3. Add searchable selectors for organizers, events, performances, customers, tickets, and venues so users rarely need to type an ID.
4. Add venue time zones, local-time display, geocoding, spatial indexing, and better address normalization.
5. Add notifications for purchases, transfers, cancellations, refunds, and approaching performance dates.
6. Turn scalper detection into a review workflow with evidence, expiration, appeal, and additional account/device/payment signals.
7. Improve pricing recommendations with seasonality, artist popularity, day/time, confidence ranges, and measured prediction quality.
8. Add automated end-to-end terminal tests and export queries/reports to CSV or PDF.

## 19. Troubleshooting quick reference

| Problem | Action |
|---|---|
| Terminal says `OFFLINE` | Start MySQL, verify `config.properties`, then use option `10` and enter `R`. |
| A documented row is missing | Restore a fresh dataset with `sql/drop.sql`, `sql/schema.sql`, and `sql/load.sql`; earlier write demonstrations may have changed it. |
| A date-based example is empty | Recalculate the range from the actual load date; do not reuse an old hard-coded year. |
| Booking cannot find a seat | Use `4 -> 1` for the same performance and enter an actually available `Row Seat#`. |
| Resale cannot find a ticket | Use `5 -> 1` for the seller and `5 -> 2` for the performance; confirm the listing is still active and the customer is not restricted. |
| Full pricing replacement is rejected | Use a future scheduled performance with no ticket history, such as `6006` or `6013` on a fresh load. |
| Cancellation is rejected | Confirm the ticket is active and the performance is more than seven days away; `8006` is intentionally too late. |
| R4 changes later operations | Use unrestricted customers for booking/resale, or reload the dataset after the R4 demonstration. |
| Java compilation cannot find a library | Confirm the supplied JARs remain in `lib/` and run from the project root. |
