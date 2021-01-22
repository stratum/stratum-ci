#!/usr/bin/env bash
# 
# Copyright 2020-present Open Networking Foundation
# 
# SPDX-License-Identifier: Apache-2.0
# 
csvlist=`ls -d $1/*.csv`

now=$(date +"%Y-%m-%d_%H_%M")
tv_result=$1/tv_result_${now}.csv
consolidated_report=$1/fabric_tna_hw_results_${now}.csv

>${tv_result}
echo "test_suite,passed_cases,failed_cases" >>${tv_result}
for entry in $csvlist
do
	pass=0
	fail=0
	test=$(basename $entry)
	test=${test%.csv}
	IFS=','
	[ ! -f $entry ] && { echo "$entry file not found"; exit 99; }
	while read test_name result
	do
		if [ $result == "true" ]; then
			pass=$((pass+1))
		else
			fail=$((fail+1))
		fi
	done < $entry
	echo "$test,$pass,$fail" >> ${tv_result}
done

>${consolidated_report}
echo "planned_cases,passed_cases,failed_cases" >>${consolidated_report}
IFS=','
[ ! -f ${tv_result} ] && { echo "$entry file not found"; exit 99; }
pass_count=0
failed_count=0
total_planned=0
while read test_name pass fail
do 
	pass_count=$((pass_count+pass))
	fail_count=$((fail_count+fail))
done < $tv_result
total_planned=$((pass_count+fail_count))
echo "$total_planned,$pass_count,$fail_count">>$consolidated_report
