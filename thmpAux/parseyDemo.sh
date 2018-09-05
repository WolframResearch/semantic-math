#!/bin/bash
export PATH=/usr/local/Cellar/python/2.7.11/bin:${PATH}
echo "which PYTHON : "
which python
cd /Users/yihed/models/syntaxnet
echo $1 | syntaxnet/demo.sh
