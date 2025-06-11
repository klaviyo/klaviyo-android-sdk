#!/bin/bash

# Check if the next version is provided as an argument
if [[ -z "$1" ]]; then
  # Prompt the user for the next version if not provided
  read -p "Enter the next semantic version: " nextVersion
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