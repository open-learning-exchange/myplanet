import re

file_path = 'app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt'
with open(file_path, 'r') as file:
    content = file.read()

# Let's see the previous `configureEditDeleteButtons` logic and restore it.
# Wait, before my change, what was `configureEditDeleteButtons`?
# ```kotlin
#    private fun configureEditDeleteButtons(holder: VoicesViewHolder, news: RealmNews) {
#        if (news.sharedBy == currentUser?._id && !fromLogin && !nonTeamMember && teamName.isEmpty()) {
#            holder.binding.imgDelete.visibility = View.VISIBLE
#        }
#
#        if (news.userId == currentUser?._id || news.sharedBy == currentUser?._id) {
#            holder.binding.imgDelete.setOnClickListener {
# ```
# It explicitly set `imgDelete.visibility = View.VISIBLE`!
# And it did not set `llEditDelete.visibility = View.VISIBLE`!
# BUT `resetViews` sets `llEditDelete.visibility = View.GONE`.
# So `imgDelete` inside `llEditDelete` would NOT be visible!
# Oh, look at my previous change: I added `showHideButtons(news, holder)` in `onBindViewHolder`
# `showHideButtons` sets `llEditDelete.setVisibility(canEdit(news) || canDelete(news))`
# And `imgEdit.setVisibility(canEdit(news))`
# And `imgDelete.setVisibility(canDelete(news))`
# Let's make sure this works correctly.
