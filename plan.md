1. **Remove `OnItemMoveListener` interface and `onItemMove` from `LifeAdapter`**: Since we want `LifeFragment` to rely exclusively on `lifeAdapter.submitList` to update items, we shouldn't have `LifeAdapter` doing manual list manipulation like `newList.removeAt(fromPosition)` and `newList.add(toPosition, movedItem)` within `onItemMove`.
2. **Move drag-and-drop / list manipulation logic to `LifeFragment`**:
    *   Make `LifeFragment` implement `OnItemMoveListener`.
    *   In `LifeFragment.getAdapter()`, pass `this` (the fragment) to `ItemReorderHelper` instead of `lifeAdapter`. Wait, `ItemReorderHelper` constructor expects `OnItemMoveListener`. If we move `OnItemMoveListener` to `LifeFragment`, we can just implement it there.
    *   In `LifeFragment.onItemMove(fromPosition: Int, toPosition: Int)`, update the list order: fetch the current list from `lifeAdapter.currentList`, do the `removeAt`/`add` to create a `newList`, then call `lifeRepository.updateMyLifeListOrder(newList)` to update the DB. Then `refreshList()` which fetches from DB and calls `lifeAdapter.submitList()`, OR just call `lifeAdapter.submitList(newList)` directly to immediately update UI while DB updates in background, but keeping the single source of truth is better. Let's see how `LifeAdapter.onItemMove` currently does it:
        ```kotlin
        override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
            val newList = currentList.toMutableList()
            val movedItem = newList.removeAt(fromPosition)
            newList.add(toPosition, movedItem)
            reorderCallback(newList)
            submitList(newList)
            return true
        }
        ```
    *   Wait, `ListAdapter` is designed to take a new list and calculate the diff. If we drag and drop, and we want smooth animations, we could either use `notifyItemMoved` (not ideal with `ListAdapter` as it calculates diffs) or `submitList`.
    *   Since `submitList` runs diff asynchronously, rapidly calling `submitList` during a drag operation might cause lag or stuttering or weird animation glitches because `ItemTouchHelper` already moves the item visually, and then we submit a new list which triggers a diff.
    *   However, if the instructions say "Ensure `LifeFragment` relies exclusively on `lifeAdapter.submitList` to update items" and "Remove any lingering manual list manipulation methods in the adapter if they exist", it implies `onItemMove` should probably be moved entirely out of the adapter and into the fragment, and the fragment should call `submitList()`.
    *   Let's check `ItemReorderHelper`: it just calls `mAdapter.onItemMove(source.bindingAdapterPosition, target.bindingAdapterPosition)`. We can pass a callback from `LifeFragment` to `ItemReorderHelper` instead of passing the adapter, or implement `OnItemMoveListener` on `LifeFragment`.
    *   Currently, `ItemReorderHelper` takes `OnItemMoveListener`. We can change `LifeFragment` to implement `OnItemMoveListener`.
    *   Then `LifeFragment.onItemMove`:
        ```kotlin
        override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
            val currentList = lifeAdapter.currentList.toMutableList()
            val movedItem = currentList.removeAt(fromPosition)
            currentList.add(toPosition, movedItem)
            lifeAdapter.submitList(currentList)

            // Also update DB
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    lifeRepository.updateMyLifeListOrder(currentList)
                }
            }
            return true
        }
        ```
    *   We also remove `reorderCallback` from `LifeAdapter` as the fragment now handles it.

3. **Modify `LifeAdapter`**:
    *   Remove `implements OnItemMoveListener`.
    *   Remove `override fun onItemMove(...)`.
    *   Remove `reorderCallback` from constructor parameters.

4. **Modify `LifeFragment`**:
    *   Implement `OnItemMoveListener`.
    *   Add `override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean` implementation.
    *   In `getAdapter()`, remove `reorderCallback` from `LifeAdapter` instantiation.
    *   Pass `this` (the fragment) to `ItemReorderHelper` instead of `lifeAdapter`.

5. **Run Pre-Commit Checks**:
    *   Call `pre_commit_instructions` and follow them to ensure tests pass.
