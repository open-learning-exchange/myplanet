# The user issue "username is null". Wait, in the screenshot, the nav drawer shows "guest_a" and below it the name might be null.
# "guest_a" is the `_id`, not the `name`?
# In `RealmUser`, what are the fields?
# Wait, look at `populateUser`:
#             val userName = JsonUtils.getString("name", jsonDoc)
# In `insertIntoUsers`:
#         user.apply {
#             _rev = JsonUtils.getString("_rev", jsonDoc)
#             _id = newId
#             name = JsonUtils.getString("name", jsonDoc)
# ...
#             val newFirstName = JsonUtils.getString("firstName", jsonDoc)
#             if (newFirstName.isNotEmpty() || firstName.isNullOrEmpty()) {
#                 firstName = newFirstName
#             }
#
# BUT `populateUser` uses `insertIntoUsers(jsonDoc, it, this.settings)`.
# Wait! In `UserRepositoryImpl`, `settings` inside `insertIntoUsers` is used for what?
# Wait, `insertIntoUsers` doesn't use `settings` parameter AT ALL!
# Oh, it doesn't? Let's check `insertIntoUsers` fully.
