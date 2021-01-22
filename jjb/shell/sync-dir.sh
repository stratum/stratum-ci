#!/usr/bin/env bash

# Copyright 2019-present Open Networking Foundation
#
# SPDX-License-Identifier: LicenseRef-ONF-Member-Only-1.0

# sync-dir.sh - run build step then sync a directory to a remote server
set -eu -o pipefail

# when not running under Jenkins, use current dir as workspace, a blank project
# name
WORKSPACE=${WORKSPACE:-.}

# run the build command
$BUILD_COMMAND

# sync the files to the target
rsync -rvzh --delete-after --exclude=.git "$WORKSPACE/$BUILD_OUTPUT_PATH" "$SYNC_TARGET_SERVER:$SYNC_TARGET_PATH"
