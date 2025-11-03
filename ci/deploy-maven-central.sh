#!/bin/bash

set -euo pipefail

./mvnw -s settings.xml -Pci,central,release \
      clean deploy -U -B
