#!/bin/bash

set -x

docker stop stratum || true
docker rm -f stratum || true
