#  Sample Data

Generate the dataset from the repository root with:

```sh
sh run.sh --generate-data
```
or 
```
mysql -u root -p database_name < sql/load.sql
```

The command rewrites `data/development-data.sql`, which `sql/load.sql` loads in
one execution. 





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

### Performance IDs category



#### Complete ticket and lifecycle coverage

| Lifecycle and ticket category | Performance IDs | Meaning |
|---|---|---|
| Scheduled, no tickets | `6006`, `6013` | The only performances with no ticket-sale history. Their complete pricing setup can be replaced safely through option **3 -> 3**. |
| Scheduled, with ticket sales | `6001`, `6002`, `6014`-`6018`, `6020`, `6023`, `6026`, `6029`, `6032`, `6035`, `6038`, `6040`, `6042`, `6044`, `6046`, `6048`, `6050`, `6052`, `6054`, `6056` | Future performances with ticket-sale history. Full pricing replacement must be rejected, including when sold tickets were later cancelled. |
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

| Category | Stable ID |
|---|---:|
| Representative reserved ticket | `8001` |
| Representative general-admission ticket | `8002` |
| Ticket with two completed resale transfers | `8007` |

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
`2002` each purchased 12 tickets and listed 7, and both have a current
`possible_scalper` restriction.

## Reviews

- [x] Multiple reviews for at least 10 events
- [x] Ratings and comments containing several meaningful sentences

Each of events `5001`-`5010` has three attendance-backed reviews with event and
venue ratings and three-sentence comments suitable for noun-phrase extraction.
