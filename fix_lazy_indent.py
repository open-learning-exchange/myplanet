with open('app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationAdapter.kt', 'r') as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    if "private val colorGrey50" in line:
        line = "    " + line.lstrip()
    elif "private val colorGreen50" in line:
        line = "    " + line.lstrip()
    elif "private val colorMultiSelectGrey" in line:
        line = "    " + line.lstrip()
    new_lines.append(line)

with open('app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationAdapter.kt', 'w') as f:
    f.writelines(new_lines)
