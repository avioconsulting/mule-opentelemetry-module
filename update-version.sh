#!/bin/sh

## Run as `sh update-version.sh 1.0.1-SNAPSHOT`
## Use for local only


echo "Changing version to '$1'"
./mvnw versions:set -DnewVersion=$1 -DprocessAllModules=true versions:commit
