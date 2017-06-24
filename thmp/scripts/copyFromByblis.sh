#!/bin/bash

#scp yihed@byblis.wolfram.com:thm/src/thmp/data/allThmWordsList.txt ~/Documents/workspace/Other/src/thmp/data
#scp yihed@byblis.wolfram.com:thm/src/thmp/data/allThmWordsList.dat ~/Documents/workspace/Other/src/thmp/data
scp yihed@byblis.wolfram.com:thm/src/thmp/data/allThmWordsMap.dat ~/Documents/workspace/Other/src/thmp/data
scp yihed@byblis.wolfram.com:thm/src/thmp/data/allThmWordsMap.txt ~/Documents/workspace/Other/src/thmp/data
scp yihed@byblis.wolfram.com:thm/src/thmp/data/termDocumentMatrixSVD.mx ~/Documents/workspace/Other/src/thmp/data
#scp yihed@byblis.wolfram.com:thm/src/thmp/data/CombinedTDMatrix.mx ~/Documents/workspace/Other/src/thmp/data

#CombinedTDMatrix.mx
#scp yihed@byblis.wolfram.com:thm/src/thmp/data/relatedWordsMap.dat ~/Documents/workspace/Other/src/thmp/data
#scp yihed@byblis.wolfram.com:thm/src/thmp/data/relatedWordsMap.txt ~/Documents/workspace/Other/src/thmp/data

#scp yihed@byblis.wolfram.com:thm/src/thmp/data/combinedParsedExpressionList.dat ~/Documents/workspace/Other/src/thmp/data

rm ~/Documents/workspace/Other/src/thmp/data/vecs/*
#mkdir -p ~/Documents/workspace/Other/src/thmp/data/vecs
scp yihed@byblis.wolfram.com:thm/src/thmp/data/vecs/* ~/Documents/workspace/Other/src/thmp/data/vecs

rm ~/Documents/workspace/Other/src/thmp/data/pe/*
#mkdir -p ~/Documents/workspace/Other/src/thmp/data/vecs
scp yihed@byblis.wolfram.com:thm/src/thmp/data/pe/* ~/Documents/workspace/Other/src/thmp/data/pe

rm ~/Documents/workspace/Other/src/thmp/data/mx/*
#mkdir -p ~/Documents/workspace/Other/src/thmp/data/vecs
scp yihed@byblis.wolfram.com:thm/src/thmp/data/mx/* ~/Documents/workspace/Other/src/thmp/data/mx

scp yihed@byblis.wolfram.com:thm/src/thmp/data/searchConfiguration.dat ~/Documents/workspace/Other/src/thmp/data

#scp yihed@byblis.wolfram.com:thm/src/thmp/data/parsedExpressionList.txt ~/Documents/workspace/Other/src/thmp/data

#scp yihed@byblis.wolfram.com:thm/src/thmp/data/allThmsList.txt ~/Documents/workspace/Other/src/thmp/data
