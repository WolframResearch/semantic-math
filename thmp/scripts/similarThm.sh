#!/bin/bash

#nice -n 16 java -cp .:dbApp.jar:lib/*:lib/stanford-postagger-2015-12-09/*:lib/stanford-postagger-2015-12-09/lib/* thmp.search.SimilarThmSearch
nice -n 16 java -cp .:thmProj.jar:dbApp.jar:/usr/share/tomcat/webapps/theoremSearchTest/WEB-INF/lib/*:/usr/share/tomcat/lib/* com.wolfram.puremath.dbapp.DBDeploy
