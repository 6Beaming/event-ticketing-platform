- The PDF does not specify whether a complete tier and section setup can be replaced. We allow replacement only for a scheduled future performance with no ticket records, including cancelled tickets. If a performance has no ticket records and is still scheduled in the future, the operation will replace its old tiers and assignments atomically.

- If any ticket has ever been sold, even if later cancelled, the full replacement will be rejected immediately, so that existing tickets keep their original tier_code and face_value; they will never be altered or orphaned.

- The PDF requires an email but does not define duplicate-email behavior. MyTix treats email as a unique account contact value, checks availability during entry, and keeps the `Users.email` constraint as final protection.

- “Recently attended” means a completed performance within the previous 365 days. A reviewer must have held a non-cancelled ticket when that performance occurred, and can review that performance only once.

- We assume that a tier price cannot be changed if any ticket that belongs to this tier has been sold.

- We assume that a past performance cannot be configured.

## Report assumptions

- Query and report ranges are entered as inclusive calendar dates in UTC. The application converts the entered end date to the next day at `00:00` and SQL uses a half-open interval (`>= start` and `< next day`) so the entire displayed end date is included. The PDF asks for dates, months, years, and periods but does not require a time-of-day for query/report input. Performance creation still requires its PDF-mandated date and time.

- R1 interprets its date range as an inclusive performance-date range, using `Performance.date_time`. Ticket count and gross revenue include active tickets at their stored face value, so cancelled/refunded tickets and resale markup are excluded.

- R2 counts a distinct event once in each geographic grouping where it has a performance. A touring event can therefore contribute to more than one country, city, or venue grouping.

- R3 organizer gross revenue uses active tickets at face value. Its city option returns separate organizer rankings for every venue city rather than requiring one city filter.

- R4 counts both original-sale and completed-resale acquisitions as tickets purchased. Counts are grouped by the performance venue's city, and repeat listing attempts for the same ticket by the same owner count once because the requirement refers to tickets listed rather than listing attempts. A customer qualifies in a city only when at least 10 acquisition records fall in the rolling year and the number of distinct tickets listed in that city and year is strictly greater than half that count.

- The PDF says customers identified by R4 must be prohibited but does not define the prohibited actions. MyTix blocks new primary bookings, resale purchases, and new resale listings while a restriction is active. It still permits cancellations and withdrawal of existing listings, and does not automatically withdraw listings that were active before the restriction. Running R4 refreshes `possible_scalper` flags, and each blocked operation recalculates the SQL rule so enforcement does not depend on the report having been run first. Automatically created flags are closed when the customer no longer qualifies; manual restrictions are not closed by R4.

- R5 treats an order as an original ticket-purchase transaction. Resale transactions are excluded from both the requested-period and venue-city order rankings.

- R6 uses a rolling one-year window ending at the time the report is run. A cancelled ticket is attributed to the customer in its recorded ownership row, and a cancelled performance is attributed to its event organizer.

- R7 treats sellable capacity as unblocked reserved seats plus total general-admission capacity. Its sold count includes active tickets; SQL returns decimal fractions and the terminal formats them as percentages.

- R8 measures the requested top-10 period by the completed resale transaction's `Transactions.transaction_date`, not by when the listing was created. A completed resale must have both a sold listing and its linked `resale` transaction; markup is measured against the ticket’s stored face value.

- R9 defines a noun phrase as a contiguous sequence of at least two adjective or noun tokens identified by the bundled English OpenNLP part-of-speech model. Phrases are lowercased, counted by occurrence, and limited to the ten highest counts per event; no graphical word cloud is produced.

## Organizer toolkit assumptions

- The PDF leaves the toolkit strategy open. MyTix first selects completed performances from the previous 24 months in the requested city, with venue capacity within 25% of the target. Exact-genre matches rank ahead of performances in another genre of the same segment. Three comparable performances is the minimum treated as a normal-confidence pool.

- When fewer than three primary matches exist, the toolkit expands to completed same-genre or same-segment performances from any city within the previous 36 months and 50% of the requested capacity. This may mix different local markets because the schema has no metro-area, exchange-rate, or currency model. If no comparable tier data exists at all, the documented rule-based fallback is three value/mid/premium tiers: 50% at $60, 30% at $100, and 20% at $150.

- Historical tiers are ranked from lowest to highest price. The most common tier count is recommended; if tier counts tie in frequency, the smaller count wins. Capacity shares are averaged by price rank, normalized, rounded to one decimal place, and the rounding remainder is applied to the largest tier so the displayed shares total exactly 100%.

- Toolkit revenue means organizer primary-ticket revenue from `Tickets.face_value`; resale proceeds are excluded. Blocked reserved seats are excluded from sellable capacity. The displayed historical revenue is the average primary-ticket revenue of the comparable performances with the recommended tier count.

- The optional revenue-change estimate is a heuristic, not a causal forecast. It separately averages capacity and sell-through for historical tiers within the entered price band around the current and proposed prices, projects revenue at each price, and reports proposed minus current revenue. Small samples and broad price bands should be treated as low-confidence estimates.
