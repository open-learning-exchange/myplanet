import re

file_path = 'app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt'
with open(file_path, 'r') as file:
    content = file.read()

# Make sure we show edit and delete buttons when the user can edit/delete
# The current code has showHideButtons() which is called inside showShareButton() ??? Let's check where it is called

match = re.search(r'showHideButtons\(news, holder\)', content)
if not match:
    # Add showHideButtons to onBindViewHolder
    content = content.replace(
        'configureEditDeleteButtons(holder, news)',
        'configureEditDeleteButtons(holder, news)\n                showHideButtons(news, holder)'
    )

# Fix canEdit and canDelete logic
# We shouldn't rely solely on configureEditDeleteButtons which has hardcoded ID checks that might be overridden or wrong
# In configureEditDeleteButtons:
content = content.replace(
    'if (news.sharedBy == currentUser?._id && !fromLogin && !nonTeamMember && teamName.isEmpty()) {',
    'if (canDelete(news)) {'
)
content = content.replace(
    'if (news.userId == currentUser?._id || news.sharedBy == currentUser?._id) {',
    'if (canDelete(news)) {'
)


with open(file_path, 'w') as file:
    file.write(content)
