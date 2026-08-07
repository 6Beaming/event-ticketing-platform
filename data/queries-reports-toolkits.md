# Queries, Reports, and Organizer Toolkit

This is a TA-facing input guide for main-menu options **7**, **8**, and **9**.
The examples use the deterministic records loaded by `sql/load.sql`. For a
broader description of the generated dataset and stable IDs, see
`data/README.md`.

## Before running the examples

From the repository root:

1. Run `sql/schema.sql` in MySQL.
2. Run `sql/load.sql` in MySQL.
3. Run `sh run.sh`.
4. Confirm that the main menu displays `Database: CONNECTED`.

The sample SQL calculates dates from `UTC_TIMESTAMP()` when `sql/load.sql` is
run. Therefore, performance, order, cancellation, listing, and review dates
move with the load date.

Use these input conventions:

- Query and report date format: `YYYY-MM-DD`; the entered end date is inclusive.
- For past-period report examples, `2000-01-01` is a safe start and today's date
  is a safe end.
- The concrete report examples below show `2026-08-07` as the end date. Replace
  it with today's date after reloading the sample data.
- Performance creation still uses `YYYY-MM-DD HH:mm` because a performance has
  a specific date and time.
- City, segment, genre, and section-type filters use exact database text.
  Examples include `Toronto`, `Music`, `Rock`, `reserved`, and `general`.
- At each `Press Enter to continue...` prompt, press Enter to return to the
  current menu.

## Useful sample-data values

| Purpose | Value |
|---|---|
| Toronto location | latitude `43.643500`, longitude `-79.379100` |
| Toronto postal-code group | `M5J 2X2` (`M5J` prefix) |
| Exact Toronto address | `40 Bay Street` |
| Search city | `Toronto` |
| Search segment / genre | `Music` / `Rock` |
| Segment IDs | Music `4001`, Arts & Theatre `4002`, Sports `4003` |
| Genre IDs | Rock `4101`, Pop `4102`, Musical `4103`, Comedy `4104`, Basketball `4105`, Hockey `4106` |
| Seat-map and best-available performance | `6001` |
| Sold-out completed performances | `6003`, `6007`, `6019` |
| Under-25% completed performances | `6004`, `6022`, `6025` |
| Possible scalper customers | `2001`, `2002` |
| Reviewed events | `5001`-`5010` |

Performance `6001` is scheduled 30 days after the data load at Harbourfront
Arena. It has reserved and general-admission sections, available consecutive
seats, blocked seats, sold tickets, and all three price tiers. This makes it a
useful input for Q6 and Q7.

## 7. Searches (Q1-Q7)

### 7 -> 1 (Q1): Nearby performances ranked by distance

Use the coordinates of Harbourfront Arena. Press Enter at the radius prompt to
use the default 50 km.

```text
Latitude: 43.643500
Longitude: -79.379100
Maximum distance in km (default 50): <press Enter>
Select option: 1
```

Expected: success. Upcoming Toronto performances within 50 km are returned in
increasing distance order. To exercise the other required rankings, repeat the
same inputs with `Select option: 2` for cheapest price ascending and `Select
option: 3` for cheapest price descending.

### 7 -> 2 (Q2): Postal-code search

```text
Enter postal code: M5J 2X2
```

Expected: success. Upcoming performances at venues whose first three postal
code characters are `M5J` are returned. This includes the three nearby Toronto
venues `3001`-`3003` when they have scheduled performances.

### 7 -> 3 (Q3): Exact-address search

```text
Enter address: 40 Bay Street
```

Expected: success. Venue `3001`, Harbourfront Arena, and its upcoming
performances are returned. The address comparison is exact.

### 7 -> 4 (Q4): Date range and minimum availability

Q4 first asks which Q1-Q3 location search to refine. This postal-code example
assumes the data was loaded on 2026-08-07:

```text
Select option: 2
Enter postal code: M5J 2X2
Start date (YYYY-MM-DD): 2026-08-08
End date (YYYY-MM-DD, inclusive): 2026-12-06
Minimum available tickets: 1
```

After a different load date, use a start one day after the load and an end 121
days after the load.

Expected: success. Matching upcoming performances in the `M5J` postal-code
group are limited to the entered date range and minimum availability. Use
location option `1` to refine the coordinate/distance search, including its
distance or cheapest-price sort, or option `3` to refine an exact-address
search.

### 7 -> 5 (Q5): Combined filters

Every Q5 prompt is optional; press Enter to skip a filter. This full-combination
example assumes the data was loaded on 2026-08-07:

```text
City: Toronto
Segment: Music
Genre: Rock
Start date (YYYY-MM-DD): 2026-08-08
End date (YYYY-MM-DD, inclusive): 2027-02-03
Minimum price: 0
Maximum price: 500
Minimum available tickets: 1
Section type (reserved/general): reserved
```

After a different load date, use a start one day after the load and an end 180
days after the load. `general` can replace `reserved` to test the other section
type.

Expected: success. Matching scheduled Rock performances in Toronto are
returned with venue, date, cheapest available price, and available quantity.
Repeat with only one or two populated fields to verify that filters work in any
combination. When `reserved` or `general` is supplied, cheapest price and
availability are calculated only from available inventory of that type.

### 7 -> 6 (Q6): Seat-map summary

```text
Performance ID: 6001
```

Expected: success. The output lists `Orchestra`, `Balcony`, and `General Floor`
with their tier, price, available count, sold count, and blocked count.

### 7 -> 7 (Q7): Best available consecutive seats

```text
Performance ID: 6001
Number of seats required: 4
Budget (optional): <press Enter>
```

Expected: success. A lowest-price run of four available consecutive reserved
seats in one row is returned. Repeat with a numeric budget to exercise the
optional budget constraint.

## 8. Reports (R1-R9)

### 8 -> 1 -> 1 (R1a): Ticket revenue by city

```text
Select option: 1
Start date (YYYY-MM-DD): 2000-01-01
End date (YYYY-MM-DD, inclusive): 2026-08-07
```

Expected: success. Cities are listed with the number of tickets sold and gross
revenue for orders in the date range.

### 8 -> 1 -> 2 (R1b): Ticket revenue by venue within a city

```text
Select option: 2
Start date (YYYY-MM-DD): 2000-01-01
End date (YYYY-MM-DD, inclusive): 2026-08-07
City: Toronto
```

Expected: success. Toronto venues are listed separately with ticket and gross
revenue totals.

### 8 -> 2 (R2): Event and performance counts

R2 has four groupings and no data prompts after the grouping is selected:

| Menu path | Input at `Select option:` | Expected grouping |
|---|---:|---|
| `8 -> 2 -> 1` | `1` | segment and genre |
| `8 -> 2 -> 2` | `2` | country |
| `8 -> 2 -> 3` | `3` | country and city |
| `8 -> 2 -> 4` | `4` | country, city, and venue |

Expected: success for all four inputs. The output contains total event and
performance counts for the selected grouping.

### 8 -> 3 (R3): Organizer revenue rankings

R3 has three groupings and no data prompts after the grouping is selected:

| Menu path | Input at `Select option:` | Expected grouping |
|---|---:|---|
| `8 -> 3 -> 1` | `1` | overall organizer ranking |
| `8 -> 3 -> 2` | `2` | organizer ranking per country |
| `8 -> 3 -> 3` | `3` | organizer ranking per city |

Expected: success for all three inputs. Organizers `1001`-`1005` are ranked by
gross ticket revenue.

### 8 -> 4 (R4): Potential ticket scalpers

There are no prompts after selecting report `4`.

Expected: success. Customers `2001` and `2002` are returned for Toronto because
each bought 12 Toronto tickets and listed 7 during the rolling past year. The
report refreshes their `possible_scalper` restrictions. New primary bookings,
resale purchases, and new resale listings for those customers return
`FORBIDDEN`; cancellations and listing withdrawals remain available.

### 8 -> 5 -> 1 (R5a): Customer ranking by order count in a period

```text
Select option: 1
Start date (YYYY-MM-DD): 2000-01-01
End date (YYYY-MM-DD, inclusive): 2026-08-07
```

Expected: success. Customers are ranked by their number of orders in the
entered period.

### 8 -> 5 -> 2 (R5b): Customer ranking by city

```text
Select option: 2
```

Expected: success. For the automatic rolling one-year period, qualifying
customers are ranked per venue city. Only customers with at least two orders
are included.

### 8 -> 6 -> 1 (R6a): Customers with the most cancelled tickets

```text
Select option: 1
```

Expected: success. The rolling one-year report contains customer cancellation
history, including customers `2086`-`2097` from the seed data.

### 8 -> 6 -> 2 (R6b): Organizers with the most cancelled performances

```text
Select option: 2
```

Expected: success. The report includes organizer cancellations for seeded
performances `6058` and `6060`.

### 8 -> 7 -> 1 (R7a): Sell-through by performance

```text
Select option: 1
```

Expected: success. Every performance with sellable capacity is shown. Useful
rows to inspect are sold-out performances `6003`, `6007`, and `6019` and
low-sell-through performances `6004`, `6022`, and `6025`.

### 8 -> 7 -> 2 (R7b): Sell-through by price tier

```text
Select option: 2
```

Expected: success. Each performance is separated into its `P1`, `P2`, and `P3`
tier capacity, sold count, and sell-through rate.

### 8 -> 7 -> 3 (R7c): Sold-out and under-25% performances by month

Performance `6003` is sold out and occurs 30 days before the load time. If the
sample data was loaded on 2026-08-07, enter:

```text
Select option: 3
Year: 2026
Month: 7
```

Expected: success. The report contains sold-out performance `6003`. After a
different load date, enter the year and month containing the date 30 days
before the load. Other useful examples are `6007` at 120 days before the load,
`6019` at 210 days before the load, and the low-sell-through IDs listed above.

### 8 -> 8 -> 1 (R8a): Resale statistics per event

```text
Select option: 1
```

Expected: success. Events with completed resales show resale count, average
markup, and fraction sold exactly at the event cap. The seed data contains 8
completed resales.

### 8 -> 8 -> 2 (R8b): Top events by resale volume in a period

```text
Select option: 2
Start date (YYYY-MM-DD): 2000-01-01
End date (YYYY-MM-DD, inclusive): 2026-08-07
```

Expected: success. Up to ten events are returned in descending completed-resale
volume. The period is applied to the linked resale transaction date, which is
the completion time, rather than the earlier listing date.

### 8 -> 9 (R9): Event noun-phrase report

There are no prompts after selecting report `9`.

Expected: success. Events `5001`-`5010` have generated reviews and receive up to
ten popular noun phrases each. Run `sh run.sh` from the repository root so the
application can load `models/en-pos-maxent.bin`.

## 9. Organizer toolkit

Option 9 immediately runs a pricing recommendation before displaying its
one-item submenu. It uses completed performances from the previous 24 months,
the exact city, the selected genre or another genre in the same segment, venue
capacity within 25% of the input, and at most 20 comparable performances.

### 9: Pricing and tier recommendation

```text
Genre ID: 4101
City: Toronto
Venue capacity: 120
```

Expected: success. The Rock/Music input finds completed Toronto performances
with comparable capacities and displays:

- comparable performance count;
- recommended number of tiers;
- recommended capacity percentage for each tier; and
- suggested price for each tier.

After reviewing the recommendation, press Enter. Enter `0` at `Select an
option:` to return to the main menu without running the extra-credit estimate.

### 9 -> 1: Revenue-impact estimate

First enter the same recommendation inputs above. After the recommendation,
press Enter and then enter:

```text
Select an option: 1
Current price: $100
Proposed price: $120
Band width ($): 20
```

Expected: success. The estimate reuses the comparable performances from the
recommendation, finds historical tiers priced from `$100` through `$140`, and
prints the number of comparable tiers, expected sell-through percentage, and
expected revenue at the proposed price.

Toolkit price inputs currently accept positive whole numbers only.
