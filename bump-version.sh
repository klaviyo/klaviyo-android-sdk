#!/bin/bash
# This script bumps the version of the project.
# It accepts a semantic version as an argument or prompts the user for it.

# Extract current version from strings.xml
STRINGS_XML_PATH="sdk/core/src/main/res/values/strings.xml"
VERSIONS_PROPERTIES_PATH="versions.properties"

# Extract current build from versions.properties and increment
currentVersion=$(xmllint --xpath "string(//string[@name='klaviyo_sdk_version_override'])" "$STRINGS_XML_PATH")
currentBuild=$(grep '^version.klaviyo.versionCode=' "$VERSIONS_PROPERTIES_PATH" | cut -d'=' -f2)
nextBuild=$((currentBuild + 1))

# Check if the version was found
if [[ -z "$currentVersion" || -z "$currentBuild" ]]; then
  echo "Error: Could not find version_name in $STRINGS_XML_PATH."
  exit 1
fi

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


echo "Changing semantic version number from $currentVersion to $nextVersion"
echo "Auto-incrementing version code from $currentBuild to $nextBuild"

# Update versions.properties
sed -i '' "s/^version.klaviyo.versionCode=$currentBuild$/version.klaviyo.versionCode=$nextBuild/" "$VERSIONS_PROPERTIES_PATH"

# Update strings.xml
sed -i '' "s|<string name=\"klaviyo_sdk_version_override\">$currentVersion</string>|<string name=\"klaviyo_sdk_version_override\">$nextVersion</string>|" "$STRINGS_XML_PATH"

# Update README.md
for module in analytics push-fcm forms location; do
  sed -i '' "s/$module:$currentVersion/$module:$nextVersion/g" "README.md"
done

# Update docs/index.html
sed -i '' "s/$currentVersion/$nextVersion/g" "docs/index.html"

echo "Version bumped successfully to $nextVersion"
