# Test Resources

This directory contains sample Realm files for testing migrations.

## Files
- `schema-v4.realm.sample`: A placeholder for a Realm file with schema version 4.

## How to generate
To generate a real sample file:
1. Checkout the git commit corresponding to the desired schema version.
2. Run the app on an emulator/device.
3. Extract the `.realm` file from the device (`/data/data/org.ole.planet.myplanet/files/default.realm`).
4. Copy it here with the name `schema-v<VERSION>.realm`.
