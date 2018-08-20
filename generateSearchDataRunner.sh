#!/bin/bash

#TO generate data according to config. Be sure to update unpack2.sh to reflect these changes, depending on whether msc, text, or thms are generated.
nice -n 15 java -Xmx31g -cp .:thmProj.jar:lib/*:lib/stanford-postagger-2015-12-09/*:lib/stanford-postagger-2015-12-09/lib/* thmp.runner.GenerateSearchDataRunner "$1" searchDataRunnerConfig.txt

#nice -n 15 java -Xmx31g -cp .:thmProj.jar:lib/*:lib/stanford-postagger-2015-12-09/*:lib/stanford-postagger-2015-12-09/lib/* thmp.runner.GenerateSearchDataRunner tarFileNamesFeb15.txt searchDataRunnerConfig.txt
