# Check if methods are used outside of sync/persistence layers

echo "Checking getShelfData usage..."
grep -rn "getShelfData" app/src/main/java

echo "Checking bulkInsertAchievementsFromSync usage..."
grep -rn "bulkInsertAchievementsFromSync" app/src/main/java

echo "Checking bulkInsertUsersFromSync usage..."
grep -rn "bulkInsertUsersFromSync" app/src/main/java

echo "Checking populateUser usage..."
grep -rn "populateUser" app/src/main/java

echo "Checking parseLeadersJson usage..."
grep -rn "parseLeadersJson" app/src/main/java

echo "Checking createGuestUser usage..."
grep -rn "createGuestUser" app/src/main/java
