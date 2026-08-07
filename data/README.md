#  Sample Data

Generate the dataset from the repository root with:

```sh
sh run.sh --generate-data
```
or 
```
mysql -u root -p database_name < sql/load.sql
```

The command rewrites `data/sample-data.sql`, which `sql/load.sql` loads in
one execution. 

## Time-relative validity

Performances, purchases, cancellations, refunds, resale activity, ownership
transfers, and reviews use `UTC_TIMESTAMP()` relative to load time to keep sample dates valid at load time.





## Sample data IDs

The deterministic generator reserves the following entity ranges.

### Entity ID ranges

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
| Purchase orders | `7001`-`7320` |
| Tickets | `8001`-`8960` |

### Customer IDs category

Customer categories overlap because one customer can participate in several
ticketing workflows.

| Testing category | Customer IDs | Testing purpose |
|---|---|---|
| Customers who have submitted reviews | `2001`, `2002`, `2009`-`2011`, `2013`-`2015`, `2021`-`2023`, `2029`-`2031`, `2039`-`2041`, `2053`, `2054`, `2060`, `2065`-`2067`, `2078`-`2080`, `2093`-`2095` | Use option **6 -> 2** to retrieve review history. These 29 customers own the 30 generated review rows; customer `2010` has two reviews. |
| Attended performance  without review| `2011` | Has attended completed performance `6003` without reviewing it, so option **6 -> 1** can submit a new review. This customer also has an existing review for performance `6034`. |
| Future reserved-ticket example | `2001` | Owns tickets for scheduled performance `6001`; also has an existing review for completed performance `6003`. |
| Near-deadline general-admission example | `2002` | Owns tickets for scheduled performance `6002`, which occurs within seven days; also has an existing review for performance `6003`. |
| Customer-initiated cancellation history | `2086`-`2097` | Each customer cancelled one ticket before the seven-day deadline. |
| Initial frequent resale sellers | `2001`, `2002` | Each purchased 12 Toronto tickets (plus 3 Vancouver tickets) and created seven Toronto resale listings: three sold, two withdrawn, and two active. |
| Completed resale buyers | `2050`-`2052`, `2060`-`2062`, `2099`, `2100` | Customers who acquired tickets through completed resale transactions. |
| Twice-resold ticket ownership chain | `2003` -> `2099` -> `2100` | Ownership history for ticket `8007`, which was resold twice. |
| Possible scalpers | `2001`, `2002` | Meet the rolling-year R4 SQL rule and have seeded current restrictions; new bookings, resale purchases, and resale listings are prohibited. |

### Performance IDs category



#### Complete ticket and lifecycle coverage

| Lifecycle and ticket category | Performance IDs | Meaning |
|---|---|---|
| Scheduled, no tickets | `6006`, `6013` | The only performances with no ticket-sale history. Their complete pricing setup can be replaced safely through option **3 -> 3**. |
| Scheduled, with ticket sales | `6001`, `6002`, `6014`-`6018`, `6020`, `6023`, `6026`, `6029`, `6032`, `6035`, `6038`, `6040`, `6042`, `6044`, `6046`, `6048`, `6050`, `6052`, `6054`, `6056` | Future performances with ticket-sale history. Full pricing replacement must be rejected, including when sold tickets were later cancelled. |
| Scheduled, with active resale listings | `6001`, `6002` | Performance `6001` has listed tickets `8001` and `8003`; performance `6002` has listed tickets `8002` and `8005`. Use option **5 -> 2** to view them by performance. |
| Completed, with ticket sales | `6003`-`6005`, `6007`-`6012`, `6019`, `6021`, `6022`, `6024`, `6025`, `6027`, `6028`, `6030`, `6031`, `6033`, `6034`, `6036`, `6037`, `6039`, `6041`, `6043`, `6045`, `6047`, `6049`, `6051`, `6053`, `6055`, `6057`, `6059` | Historical performances with ticket-sale history, including the sold-out and low-sell-through examples identified below. |
| Organizer-cancelled after sales | `6058`, `6060` | Performances whose sold tickets were cancelled and refunded after organizer cancellation. |

#### Test-purpose performance IDs


| Testing category | Stable ID | Testing purpose |
|---|---:|---|
| Pricing configuration, mixed reserved/GA venue | `6006` | Future scheduled performance with existing tiers but no tickets; option **3 -> 3** can safely replace its pricing and section assignments. |
| Pricing configuration, reserved-only venue | `6013` | Future scheduled performance with existing tiers but no tickets; option **3 -> 3** can safely replace its pricing and section assignments. |
| Tier-price and seat-availability controls | `6001` | More than seven days away; tiers `P1` and `P2` have sales while `P3` has no sales; also provides consecutive and nonconsecutive reserved-seat availability. |
| Near-deadline booking/cancellation rejection | `6002` | Scheduled five days away with sold reserved and general-admission tickets. |
| Partial customer-cancellation history | `6014`, `6015`, `6016` | Scheduled performances containing both active and cancelled ticket rows. |
| Completed and sold out | `6003`, `6007`, `6019` | Historical sell-through and inventory examples across different cities and months. |
| Completed with low sell-through | `6004`, `6022`, `6025` | Historical performances below 25% sell-through across different cities and months. |
| Organizer-cancelled | `6058`, `6060` | Cancelled-performance, refund, and historical inventory examples. |

### Ticket IDs category

Ticket categories overlap because one ticket can participate in several workflows.

| Testing category | Ticket IDs | Brief use |
|---|---|---|
| Representative reserved ticket | `8001` | Active ticket for performance `6001`; currently listed for resale. |
| Representative general-admission ticket | `8002` | Active GA ticket for performance `6002`; currently listed for resale. |
| Future unlisted reserved ticket | `8004` | Owned by customer `2001` for performance `6001`, which is more than seven days away. |
| Near-deadline unlisted GA ticket | `8006` | Owned by customer `2002` for performance `6002`; customer cancellation must be rejected. |
| Active resale listings | `8001`, `8003`, `8002`, `8005` | Visible through option **5 -> 2** for performances `6001` and `6002`. |
| Completed resale transfers | `8133`-`8135`, `8139`-`8141` | Sold once and transferred to their resale buyers. |
| Withdrawn resale listings | `8136`, `8137`, `8142`, `8143` | Listings retained as withdrawn history. |
| Listings priced at the resale cap | `8001`, `8002`, `8007`, `8133`, `8139` | Exact-cap examples for resale reporting and validation. |
| Ticket with two resale transfers | `8007` | Ownership chain `2003` -> `2099` -> `2100`; view with option **5 -> 6**. |
| Customer-cancelled tickets | `8274`, `8277`, …, `8307` (every third ID) | Cancelled before the seven-day deadline and fully refunded. |
| Organizer-cancelled tickets | `8919`-`8933`, `8946`-`8960` | Cancelled and refunded with performances `6058` and `6060`. |

## Generated counts

| Record | Count |
|---|---:|
| Organizers | 5 |
| Adult customers and payment records | 100 each |
| Venues | 8 |
| Cities / countries | 6 / 2 |
| Segments / genres / artists or teams | 3 / 6 / 15 |
| Events / performances | 20 / 60 |
| Purchase orders / tickets | 320 / 960 |
| Ticket cancellations and refunds | 42 each |
| Organizer-cancelled performances | 2 |
| Resale listings | 16: 8 sold, 4 withdrawn, 4 active |
| Reviews | 30 across 10 events |

## Venues and seating

- [x] At least 8 venues across at least 4 cities and 2 countries
- [x] Realistic addresses, postal codes, latitude, and longitude
- [x] At least 3 nearby venues in the same or adjacent postal codes
- [x] Several sections per venue and varied venue capacities
- [x] At least 2 venues with both reserved and general-admission sections

Venues `3001`-`3003` are nearby Toronto venues in the `M5J` postal area.
Capacities vary from 60 to 126, and five venues mix reserved and general
admission inventory.

## Events and performances

- [x] At least 20 events managed by at least 5 organizers
- [x] At least 3 segments, 6 genres, and 15 artists or teams
- [x] Multi-artist events with different billing orders
- [x] At least 60 past and upcoming performances
- [x] A touring event that appears at different venues
- [x] A theatre-style event with many performances at one venue
- [x] Different tier mappings and prices for two performances at one venue

Event `5001` tours across several cities. Event `5002` has 12 performances at
venue `3002`. Performances `6001` and `6002` share venue `3001` but swap tier
assignments and use different prices.

## Pricing and inventory

- [x] At least 2 price tiers for every performance
- [x] Future tiers with no sales and future tiers with existing sales
- [x] Performance-specific blocked seats
- [x] A future performance more than 7 days away with reserved and general-admission availability
- [x] A row with at least 4 consecutive available seats
- [x] A row with only nonconsecutive seats available
- [x] An upcoming performance fewer than 7 days away with sold tickets

Every performance has three tiers. At performance `6001`, `P1` and `P2` have
sales while `P3` has none; Orchestra row A seats 1-4 are consecutive and
available, and only odd seats remain available in Orchestra row B. Performance
`6002` occurs five days away and has sold reserved and general-admission
tickets.

## Customers, orders, and tickets

- [x] At least 100 adult customers with fictional personal and card information
- [x] At least 300 orders containing at least 800 tickets
- [x] Orders distributed across the past 12 months and future performances
- [x] Several customers with at least 2 recent orders across multiple cities
- [x] Past sold-out performances in several cities and months
- [x] Past performances that sold less than 25% of capacity

Performances `6003`, `6007`, and `6019` are sold out. Performances `6004`,
`6022`, and `6025` are below 25% sell-through. These examples cover several
cities and dates.

## Cancellations and resale

- [x] Ticket cancellations by several customers
- [x] At least 2 organizer-cancelled performances in the past year
- [x] Completed, withdrawn, and active resale listings
- [x] Some listings priced exactly at the event resale cap
- [x] At least 1 ticket that changed owners twice
- [x] At least 2 customers who bought 10 or more tickets and listed more than half within the past year

Performances `6058` and `6060` are organizer-cancelled with ticket-level refunds.
Ticket `8007` has two completed ownership transfers. Customers `2001` and
`2002` each purchased 12 Toronto tickets and listed 7 Toronto tickets, and both
have a current `possible_scalper` restriction.

## Reviews

- [x] Multiple reviews for at least 10 events
- [x] Ratings and comments containing several meaningful sentences

Each of events `5001`-`5010` has three attendance-backed reviews with event and
venue ratings and three-sentence comments suitable for noun-phrase extraction.
