#!/usr/bin/env bash

# Copyright 2019-present Open Networking Foundation
#
# SPDX-License-Identifier: LicenseRef-ONF-Member-Only-1.0

# make-test.sh - run one or more make targets
set -eu -o pipefail

# performs docker login if the job defines $DOCKERHUB_USERNAME and
# $DOCKERHUB_PASSWORD variables
DOCKERHUB_USERNAME=${DOCKERHUB_USERNAME:-}
DOCKERHUB_PASSWORD=${DOCKERHUB_PASSWORD:-}
if [[ ! -z "$DOCKERHUB_USERNAME" &&  ! -z "$DOCKERHUB_PASSWORD" ]]; then
  echo $DOCKERHUB_PASSWORD | docker login --username ${DOCKERHUB_USERNAME} --password-stdin
fi

# when not running under Jenkins, use current dir as workspace, a blank project
# name
WORKSPACE=${WORKSPACE:-.}
GERRIT_PROJECT=${GERRIT_PROJECT:-}

# Fixes to for golang projects to support GOPATH
# If $DEST_GOPATH is not an empty string:
# - set create GOPATH, and destination directory within in
# - set PATH to include $GOPATH/bin and the system go binaries
# - symlink from $WORKSPACE/$GERRIT_PROJECT to new location in $GOPATH
# - start tests in that directory

DEST_GOPATH=${DEST_GOPATH:-}
if [ ! -z "$DEST_GOPATH" ]; then
  export GOPATH=${GOPATH:-~/go}
  mkdir -p "$GOPATH/src/$DEST_GOPATH"
  export PATH=$PATH:/usr/lib/go-1.12/bin:/usr/local/go/bin:$GOPATH/bin
  test_path="$GOPATH/src/$DEST_GOPATH/$GERRIT_PROJECT"
  ln -r -s "$WORKSPACE/$GERRIT_PROJECT" "$test_path"
else
  test_path="$WORKSPACE/$GERRIT_PROJECT"
fi

# Use "test" as the default target, can be a space separated list
MAKE_TEST_TARGETS=${MAKE_TEST_TARGETS:-test}

# Default to fail on the first test that fails
MAKE_TEST_KEEP_GOING=${MAKE_TEST_KEEP_GOING:-false}

if [ ! -f "$test_path/Makefile" ]; then
  echo "Makefile not found at $test_path!"
  exit 1
else
  pushd "$test_path"

  # we want to split the make targets apart on spaces, so:
  # shellcheck disable=SC2086
  if [ "$MAKE_TEST_KEEP_GOING" = "true" ]; then
    make -k $MAKE_TEST_TARGETS
  else
    make $MAKE_TEST_TARGETS
  fi

  popd
fi

