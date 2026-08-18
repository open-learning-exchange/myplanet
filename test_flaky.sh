export ANDROID_HOME=/opt/android-sdk; unset ANDROID_SDK_ROOT
for i in {1..3}; do
  echo "Run $i"
  ./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.utils.FileUtilsTest.getOlePath_returnsCorrectPath" --rerun-tasks
done
