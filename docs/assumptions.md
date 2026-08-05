- The PDF does not specify whether a complete tier and section setup can be replaced. MyTix allows replacement only for a scheduled future performance with no ticket records, including cancelled tickets. If a performance has no ticket records and is still scheduled in the future, the operation will replace its old tiers and assignments atomically.
If any ticket has ever been sold—even if later cancelled—the full replacement will be rejected immediately.
Existing tickets keep their original tier_code and face_value; they will never be altered or orphaned.
Updating one unsold tier’s price remains a separate operation required later by the PDF.

- The PDF requires an email but does not define duplicate-email behavior. MyTix treats email as a unique account contact value, checks availability during entry, and keeps the `Users.email` constraint as final protection.

- “Recently attended” means a completed performance within the previous 365 days. A reviewer must have held a non-cancelled ticket when that performance occurred, and can review that performance only once.

- The PDF identifies possible scalpers per city but does not list the prohibited actions. MyTix automatically creates a restriction when, in any city during the rolling year, a customer purchased at least 10 tickets and listed more than half of them. The restriction blocks new bookings, resale listings, and resale purchases. It does not block cancellations, listing withdrawals, or attendance reviews.
