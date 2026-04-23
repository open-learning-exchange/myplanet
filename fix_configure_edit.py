import re
file_path = 'app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt'
with open(file_path, 'r') as file:
    content = file.read()

# Make sure we don't duplicate showHideButtons
content = content.replace('showHideButtons(news, holder)', '')
content = content.replace('configureEditDeleteButtons(holder, news)', 'configureEditDeleteButtons(holder, news)\n                showHideButtons(news, holder)')

with open(file_path, 'w') as file:
    file.write(content)
