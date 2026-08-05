# July 29-31 Foundation Verification (Historical)

This page records the earlier foundation-only validation. The counts below
were superseded on August 5 by the complete dataset documented in
`data/README.md`; they are retained only as a record of that milestone.

Verified on August 3, 2026 with Java 17.0.12 and an isolated MySQL 8.0.46
instance. The temporary server used a new data directory under `.build`, was
shut down after validation, and its temporary directory was removed.

## Database-independent checks

`testing.FoundationSelfTest` reported 12 passed and 0 failed:

- valid adult and rejected under-18 profiles;
- required fictional payment fields;
- commit on success and rollback on validation/SQL failure;
- safe database error messages;
- reserved and GA inventory calculations;
- duplicate billing rank and invalid event identifiers;
- minimum tiers, unique assignment, and full venue-section coverage;
- deterministic development SQL generation.

## Live MySQL checks

`testing.FoundationDatabaseCheck --reset-database` completed the following on
a clean MyTix database:

1. Executed `sql/drop.sql`.
2. Executed `sql/schema.sql` in one pass.
3. Executed `sql/load.sql`, including its generated development-data source.
4. Created, retrieved, masked, and history-preservingly deactivated a customer.
5. Rejected an under-18 customer and a repeated deactivation.
6. Created an organizer, event, artist billing order, performance, two tiers,
   and complete section assignments.
7. Rejected nonexistent taxonomy and venue IDs.
8. Retrieved six reserved seats with the expected sold/blocked states.
9. Retrieved GA capacity with the expected sold and remaining quantities.
10. Re-executed the three SQL scripts to restore deterministic development data.

The restored database contained 26 tables, 3 users, 3 performances, and 2
tickets with the stable IDs in `docs/schema-contract.md`.

## Reproduction commands

```sh
sh run.sh --generate-data
sh run.sh --self-test
sh run.sh --database-check
sh run.sh
```

`--database-check` is destructive only to the tables in the configured MyTix
development database. Each team member should run it with their own local
credentials before confirming the two member-approval exit criteria in
`plan.md`.

## August 5 complete-data refresh

The complete generator now performs requirement assertions before writing SQL.
The full Java project compiles, generation succeeds, and
`testing.FoundationSelfTest` reports 12 passed and 0 failed.

The clean-load sequence was also verified with an isolated MySQL 8.0.46 server
on a separate port and a new temporary data directory under `.build`. The
configured MyTix database was not touched. `sql/schema.sql` followed by
`sql/load.sql` completed in one execution and returned these core counts:

- 5 organizers, 100 customers, 8 venues, 20 events, and 60 performances;
- 320 purchase orders and 960 tickets;
- 42 ticket cancellations with 42 refunds;
- 16 resale listings in sold, withdrawn, and active states;
- 30 reviews across 10 events.

Requirement queries also confirmed three nearby venues, five mixed-seating
venues, all section-tier assignments, sold and unsold future tiers, consecutive
and nonconsecutive availability cases, three sold-out and three low-sell-through
performances across cities and months, two organizer cancellations, two
possible-scalper examples, and three ownership rows for twice-resold ticket
`8007`. The temporary server was stopped and its generated data directory was
removed after validation.
