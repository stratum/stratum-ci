#!/bin/bash

set -x

./stop-stratum.sh
sleep 5
./start-stratum-container.sh $@
