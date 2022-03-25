# SPDX-FileCopyrightText: 2020 Open Networking Foundation <info@opennetworking.org>
#
# SPDX-License-Identifier: LicenseRef-ONF-Member-Only-1.0

# Makefile for testing JJB jobs in a virtualenv

.PHONY: test clean

SHELL         := /usr/bin/env bash
VENV_DIR      ?= venv-jjb
JJB_VERSION   ?= 3.10.0
JOBCONFIG_DIR ?= job-configs

$(VENV_DIR):
	@echo "Setting up virtualenv for JJB testing"
	virtualenv $@
	$@/bin/pip install jenkins-job-builder==$(JJB_VERSION) pipdeptree

$(JOBCONFIG_DIR):
	mkdir $@

test: $(VENV_DIR) $(JOBCONFIG_DIR)
	source $(VENV_DIR)/bin/activate ; \
	pipdeptree ; \
	jenkins-jobs -l DEBUG test --recursive -o $(JOBCONFIG_DIR) jjb/ ;

clean:
	rm -rf $(VENV_DIR) $(JOBCONFIG_DIR)
