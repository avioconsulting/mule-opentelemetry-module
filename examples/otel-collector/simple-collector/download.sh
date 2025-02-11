#!/bin/bash

if [ -z "$1" ]; then
  echo "Error: No version provided."
  exit 1
fi

VERSION=$1
echo "Downloading otel-contrib collector version $VERSION"
OTEL_FILE="otelcol-contrib_${VERSION}_linux_amd64.tar.gz"
OTEL_URL="https://github.com/open-telemetry/opentelemetry-collector-releases/releases/download/v0.119.0/${OTEL_FILE}"
curl --proto '=https' --tlsv1.2 -fOL $OTEL_URL
tar -xvf $OTEL_FILE