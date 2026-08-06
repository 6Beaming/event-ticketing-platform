- The PDF does not specify whether a complete tier and section setup can be replaced. We allow replacement only for a scheduled future performance with no ticket records, including cancelled tickets. If a performance has no ticket records and is still scheduled in the future, the operation will replace its old tiers and assignments atomically.

- If any ticket has ever been sold, even if later cancelled, the full replacement will be rejected immediately, so that existing tickets keep their original tier_code and face_value; they will never be altered or orphaned.

- The PDF requires an email but does not define duplicate-email behavior. MyTix treats email as a unique account contact value, checks availability during entry, and keeps the `Users.email` constraint as final protection.

- “Recently attended” means a completed performance within the previous 365 days. A reviewer must have held a non-cancelled ticket when that performance occurred, and can review that performance only once.

- We assume that a tier price cannot be changed if any ticket that belongs to this tier has been sold.

- We assume that a past performance cannot be configured.

## Report assumptions

- R1 interprets its date range as an inclusive performance-date range, using `Performance.date_time`. Ticket count and gross revenue include active tickets at their stored face value, so cancelled/refunded tickets and resale markup are excluded.

- R2 counts a distinct event once in each geographic grouping where it has a performance. A touring event can therefore contribute to more than one country, city, or venue grouping.

- R3 organizer gross revenue uses active tickets at face value. Its city option returns separate organizer rankings for every venue city rather than requiring one city filter.

- R5 treats an order as an original ticket-purchase transaction. Resale transactions are excluded from both the requested-period and venue-city order rankings.

- R6 uses a rolling one-year window ending at the time the report is run. A cancelled ticket is attributed to the customer in its recorded ownership row, and a cancelled performance is attributed to its event organizer.

- R7 treats sellable capacity as unblocked reserved seats plus total general-admission capacity. Its sold count includes active tickets; reported rates are decimal fractions, where `1.0000` means 100%.

- R8 measures the requested top-10 resale period by `ResaleListing.listed_date`. Completed resale counts include only listings whose status is `sold`; markup is measured against the ticket’s stored face value.

- R9 defines a noun phrase as a contiguous sequence of at least two adjective or noun tokens identified by the bundled English OpenNLP part-of-speech model. Phrases are lowercased, counted by occurrence, and limited to the ten highest counts per event; no graphical word cloud is produced.
