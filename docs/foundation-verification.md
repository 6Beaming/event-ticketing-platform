# July 29-31 Foundation Verification

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
