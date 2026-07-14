#!/bin/bash
./gradlew app:testDefaultDebugUnitTest --tests *VoicesRepositoryImplTest* --tests *ProgressRepositoryImplTest* --tests *LifeRepositoryImplTest* --tests *TeamsRepositoryImplTest* --no-build-cache
