#!/bin/bash
#alias python="/usr/local/Cellar/python/2.7.11/bin/python"
#export PYTHONPATH=/usr/local/lib/python2.7/site-packages:$PYTHONPATH
export PATH=/usr/local/Cellar/python/2.7.11/bin:${PATH}
echo "which PYTHON : "
which python
#echo "PYTHONPATH: "
#echo $PYTHONPATH
#echo $USER
cd /Users/yihed/models/syntaxnet
echo $1 | syntaxnet/demo.sh
