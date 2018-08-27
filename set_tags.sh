BRANCH="master"

# Are we on the right branch?
if [ "$TRAVIS_BRANCH" = "$BRANCH" ]; then
  
  # Is this not a Pull Request?
  if [ "$TRAVIS_PULL_REQUEST" = false ]; then
    
    # Is this not a build which was triggered by setting a new tag?
    if [ -z "$TRAVIS_TAG" ]; then
      echo -e "Starting to tag commit.\n"

      git config --global user.email "leomaxi@outlook.com"
      git config --global user.name "leomaxi"

      # Add tag and push to master.
      PACKAGE_VERSION=$(sed -n 's/.*name="app_version">\([^"]*\).*<\/string>/\1/p' </app/src/main/res/values/strings.xml)
      git tag -a v${PACKAGE_VERSION} -m "Travis build ${PACKAGE_VERSION} pushed a tag."
      git push origin --tags
      git fetch origin

      echo -e "Done magic with tags.\n"
  fi
  fi
fi