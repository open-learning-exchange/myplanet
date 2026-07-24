import re

file_path = "./app/src/main/res/values/colors.xml"
with open(file_path, "r") as f:
    content = f.read()

if '<color name="pending_request_indicator">#9fa0a4</color>' not in content:
    content = content.replace('</resources>', '    <color name="pending_request_indicator">#9fa0a4</color>\n</resources>')
    with open(file_path, "w") as f:
        f.write(content)
