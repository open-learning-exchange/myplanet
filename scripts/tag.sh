#!/usr/bin/env bash
# Tag last commit as 'latest'.

if [ "$TRAVIS_BRANCH" = "master" -a "$TRAVIS_PULL_REQUEST" = "false" ]; then
  git config --global user.email "leomaxi@outlook.com"
  git config --global user.name "leomaxi"
  # Is this not a build which was triggered by setting a new tag?
    if [ -z "$TRAVIS_TAG" ]; then
      git remote add release "https://${GH_TOKEN}@github.com/open-learning-exchange/myplanet.git"
      PACKAGE_VERSION=$(sed -n 's/.*name="app_version">\([^"]*\).*<\/string>/\1/p' < app/src/main/res/values/versions.xml)
      git push -d release latest
      git tag -d latest
      git tag -a v${PACKAGE_VERSION} -m "$TRAVIS_COMMIT_MESSAGE"
      git push release --tags
      echo -e "Done with tags.\n"
    fi
fi