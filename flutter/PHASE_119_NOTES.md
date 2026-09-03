# Phase 119 — the five missing sync walks

Work in progress. Phase 116 audited reachability and reported, without fixing,
that five `TransactionSyncManager` walks Kotlin runs have no counterpart in the
port: `shelf`, `tablet_users`, `ratings`, `tasks`, `achievements`. This phase
closes them.
