#!/bin/bash
# Check if methods are already in TeamsRepository
grep -q "suspend fun getResourceIds(teamId: String): List<String>" app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepository.kt
if [ $? -ne 0 ]; then
  echo "Missing getResourceIds in TeamsRepository.kt"
fi
