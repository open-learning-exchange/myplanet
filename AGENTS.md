# Local Instructions

The following steps verify development tools and perform a basic build.
These steps should be run whenever verifying the environment or testing
build functionality.

## Step 1: verify development tools

Run:

```
./gradlew --console=plain --version | head -n 20
sdkmanager --version
gh --version && gh auth status
qlty --version && qlty auth whoami
```

## Step 2: run a dry-run build (more verbose)

Run:

```
./gradlew assembleDebug --console=plain --dry-run | head -n 40
```

## Step 3: perform an actual build (first 40 lines)

Run:

```
./gradlew assembleDebug --console=plain | head -n 40
```
