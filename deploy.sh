#!/bin/bash

set -e

sbt clean pack

ssh dev@superbee.accur8.net << EOF
  cd /opt/locus
  mkdir -p _bak/(date --iso-8601)
  cp -R config _bak/(date --iso-8601)/
  cp -R lib _bak/(date --iso-8601)/
  supervisorctl stop locus
EOF

rsync -vrP --delete target/pack/lib/ dev@superbee.accur8.net:/opt/locus/lib/
ssh dev@superbee.accur8.net "supervisorctl start locus"
