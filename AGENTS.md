
## Realm Object Copying

*   **Avoid unnecessary `copyFromRealm()` calls.**
    *   When working within a `withRealm` block or a transaction, prefer working with managed (attached) Realm objects if the data is only used locally within that block.
    *   Use the `*Attached` variants of repository methods (e.g., `queryListAttached`, `findAttachedByField`) when you have an open `Realm` instance and do not need to pass the objects to another thread.
*   **Use `copyFromRealm()` only when necessary.**
    *   Use `copyFromRealm()` (or the standard repository methods that do it implicitly) when returning data to the UI, crossing thread boundaries, or when the Realm instance will be closed.
    *   Always document why a copy is being made if it's not obvious (e.g., "returning to UI").
