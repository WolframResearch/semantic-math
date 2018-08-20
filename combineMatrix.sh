#!/bin/bash

#combine projected matrices

#All thm jars built into one:
#nice -n 19 java -cp .:thmProj.jar:lib/*:lib/stanford-postagger-2015-12-09/*:lib/stanford-postagger-2015-12-09/lib/* thmp.search.ProjectionMatrix 0209_001Untarred/0209 0210_001Untarred/0210

rm src/thmp/data/vecs/*
rm src/thmp/data/pe/*
rm src/thmp/data/mx/*

nice -n 1 java -Xmx31g -XshowSettings:all -cp .:thmProj.jar:lib/*:lib/stanford-postagger-2015-12-09/*:lib/stanford-postagger-2015-12-09/lib/* thmp.search.ProjectionMatrix "$1"  #testAugTo2.txt #combineMatrixFileNames.txt

