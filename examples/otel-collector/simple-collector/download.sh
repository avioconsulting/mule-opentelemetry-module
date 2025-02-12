#!/bin/bash

# Usage: ./download.sh <release_version>
# Example: ./download.sh 0.119.0

if [ -z "$1" ]; then
  echo "Error: No version provided."
  echo "Usage: $0 <release version>"
  exit 1
fi

VERSION=$1
echo "Downloading otel-contrib collector version $VERSION"
OTEL_FILE="otelcol-contrib_${VERSION}_linux_amd64.tar.gz"
OTEL_URL="https://github.com/open-telemetry/opentelemetry-collector-releases/releases/download/v${VERSION}/${OTEL_FILE}"

echo "Downloading $OTEL_FILE from:"
echo "$OTEL_URL"

curl --proto '=https' --tlsv1.2 -fOL $OTEL_URL
if [ $? -ne 0 ]; then
  echo "Error: Failed to download $OTEL_FILE"
  exit 1
fi

echo "Extracting $OTEL_FILE..."
tar -xvf $OTEL_FILE
if [ $? -ne 0 ]; then
  echo "Error: Failed to extract $OTEL_FILE"
  exit 1
fi

echo "Download and extraction complete."