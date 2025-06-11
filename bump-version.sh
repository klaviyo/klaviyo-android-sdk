#!/bin/bash
# This script bumps the version of a project using a custom gradle task.
# It accepts a semantic version as an argument or prompts the user for it.
# See ./versioning.gradle for implementation.

# Extract current version from strings.xml
STRINGS_XML_PATH="sdk/core/src/main/res/values/strings.xml"
currentVersion=$(xmllint --xpath "string(//string[@name='klaviyo_sdk_version_override'])" "$STRINGS_XML_PATH")

# Check if the version was found
if [[ -z "$currentVersion" ]]; then
  echo "Error: Could not find version_name in $STRINGS_XML_PATH."
  exit 1
fi

# Output the current version
echo "Current version: $currentVersion"

# Check if the next version is provided as an argument
if [[ -z "$1" ]]; then
  # Prompt the user for the next version if not provided
  echo "Enter the next semantic version (e.g. 1.0.0):"
  read -rp "Version: " nextVersion
else
  nextVersion="$1"
fi

# Check if the input is empty
if [[ -z "$nextVersion" ]]; then
  echo "Error: Version cannot be empty."
  exit 1
fi

# Call the Gradle task with the provided version
./gradlew bumpVersion --nextVersion="$nextVersion"

# Check if the Gradle task succeeded
if [[ $? -eq 0 ]]; then
  echo "Version bumped successfully to $nextVersion."
else
  echo "Failed to bump the version."
  exit 1
fi
