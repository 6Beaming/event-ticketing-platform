# MyTix assumptions

This file records implementation decisions that are not specified by `Database design project (MyTix).pdf`.

## Accounts and operations

- Email addresses are unique account identifiers. Availability is checked during entry, with the `Users.email` constraint providing final protection.

- “Recently attended” means a completed performance within the previous 365 days.

- A complete pricing setup may be replaced only for a scheduled future performance with no ticket records, including cancelled tickets. Once any ticket has existed, its stored tier and face value are preserved.

## Search assumptions

- “Upcoming” means a scheduled performance whose date and time are later than the current UTC time.

- `GeneralAdmissionCapacity.remaining_capacity` is the authoritative GA availability counter. Booking and cancellation operations update it in the same transaction as the related ticket changes.

- Q1 uses a default radius of 50 km and Haversine great-circle distance with an Earth radius of 6,371 km. When sorted by distance, sold-out performances remain visible with no cheapest available price; when sorted by price, they appear after performances with available tickets.

- Q2 approximates adjacent postal codes using a shared first three characters because the schema has no postal-boundary data.

- Q3 can return multiple venues for the same address because `Venue.address` is not unique.

- Q5 calculates price and availability only from the requested seating type. Performances with no matching available inventory are excluded.

- Query and report ranges are inclusive UTC calendar dates. The application converts the end date to the following day at `00:00` and SQL uses `>= start` and `< next day`.

## Report assumptions

- R1 applies its date range to `Performance.date_time`. It counts active tickets at face value, excluding cancelled tickets, refunds, and resale markup.

- R2 counts a distinct event once in every geographic grouping where it has a performance, so a touring event can appear in multiple locations.

- R3 gross revenue is active-ticket face value. The city view produces separate rankings for each venue city.

- R4 treats original purchases and completed-resale acquisitions as purchases. A ticket listed repeatedly by the same owner counts once. Identified customers cannot make primary bookings, buy resale listings, or create new listings; they may still cancel tickets or withdraw existing listings. Automatic restrictions are refreshed by R4 and before guarded operations.

- R5 counts original ticket-purchase orders and excludes resale transactions.

- R6 uses the rolling year ending when the report runs. Ticket cancellations are attributed to the recorded customer, and performance cancellations to the event organizer.

- R7 counts active tickets as sold. SQL returns fractions and the terminal displays percentages.

- R8 uses the completed resale transaction date for period filtering. A resale is completed only when a sold listing has a linked resale transaction; markup is calculated from the ticket’s face value.

- R9 defines a noun phrase as at least two consecutive adjective or noun tokens identified by the bundled English OpenNLP model. Phrases are lowercased, counted by occurrence, and limited to the ten most frequent per event.

## Organizer toolkit assumptions

- The primary comparable pool contains completed performances from the previous 24 months in the requested city and within 25% of the requested venue capacity. Exact-genre matches rank before same-segment matches. Three comparables is the normal-confidence minimum.

- With fewer than three primary matches, the pool expands to any city, 36 months, and 50% capacity tolerance while retaining the genre or segment match. This can mix local markets because the schema has no currency, exchange-rate, or metro-area model.

- With no usable historical tier data, the fallback is three value/mid/premium tiers: 50% at $60, 30% at $100, and 20% at $150.

- Historical tiers are ranked from lowest to highest price. The most common tier count is selected; a frequency tie selects the smaller count. Average capacity shares are normalized and rounded to total exactly 100%.

- Toolkit revenue is primary-ticket face value and excludes resale proceeds. Historical revenue is averaged across comparables using the recommended tier count.

- The optional revenue-change result is a heuristic. It projects current and proposed revenue from historical tiers inside the entered price bands and reports proposed minus current revenue; small samples are low confidence.
