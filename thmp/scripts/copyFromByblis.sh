#!/bin/bash

#scp yihed@byblis.wolfram.com:thm/src/thmp/data/allThmWordsList.txt ~/Documents/workspace/SemanticMath/src/thmp/data
scp yihed@byblis67.wolfram.com:thm/src/thmp/data/allThmWordsMap.dat ~/Documents/workspace/SemanticMath/src/thmp/data
scp yihed@byblis67.wolfram.com:thm/src/thmp/data/allThmWordsMap.txt ~/Documents/workspace/SemanticMath/src/thmp/data
##this step takes a while, only enable when matrix was regenerated!
#don't delete#scp yihed@byblis67.wolfram.com:thm/src/thmp/data/termDocumentMatrixSVD.mx ~/Documents/workspace/SemanticMath/src/thmp/data

scp yihed@byblis67.wolfram.com:thm/src/thmp/data/wordThmIndexMMap.dat ~/Documents/workspace/SemanticMath/src/thmp/data

scp yihed@byblis67.wolfram.com:thm/src/thmp/data/paperMetaDataMap.dat ~/Documents/workspace/SemanticMath/src/thmp/data
scp yihed@byblis67.wolfram.com:thm/src/thmp/data/metaDataNameDB.csv ~/Documents/workspace/SemanticMath/src/thmp/data
scp yihed@byblis67.wolfram.com:thm/src/thmp/data/similarThmIndexStr.dat ~/Documents/workspace/SemanticMath/src/thmp/data


#comment below back in when need to copy maps data
scp yihed@byblis67.wolfram.com:thm/src/thmp/data/twoGramsMap.dat ~/Documents/workspace/SemanticMath/src/thmp/data
scp yihed@byblis67.wolfram.com:thm/src/thmp/data/threeGramsMap.dat ~/Documents/workspace/SemanticMath/src/thmp/data
scp yihed@byblis67.wolfram.com:thm/src/thmp/data/twoGramsMap.txt ~/Documents/workspace/SemanticMath/src/thmp/data
scp yihed@byblis67.wolfram.com:thm/src/thmp/data/threeGramsMap.txt ~/Documents/workspace/SemanticMath/src/thmp/data

scp yihed@byblis67.wolfram.com:thm/src/thmp/data/allThmNameScrape.dat ~/Documents/workspace/SemanticMath/src/thmp/data
scp yihed@byblis67.wolfram.com:thm/src/thmp/data/allThmNameScrape.txt ~/Documents/workspace/SemanticMath/src/thmp/data

#CombinedTDMatrix.mx
#scp yihed@byblis.wolfram.com:thm/src/thmp/data/relatedWordsMap.dat ~/Documents/workspace/SemanticMath/src/thmp/data
#scp yihed@byblis.wolfram.com:thm/src/thmp/data/relatedWordsMap.txt ~/Documents/workspace/SemanticMath/src/thmp/data

rm ~/Documents/workspace/SemanticMath/src/thmp/data/vecs/*
#mkdir -p ~/Documents/workspace/SemanticMath/src/thmp/data/vecs
scp yihed@byblis67.wolfram.com:thm/src/thmp/data/vecs/* ~/Documents/workspace/SemanticMath/src/thmp/data/vecs

rm ~/Documents/workspace/SemanticMath/src/thmp/data/pe/*
#mkdir -p ~/Documents/workspace/SemanticMath/src/thmp/data/vecs
scp yihed@byblis67.wolfram.com:thm/src/thmp/data/pe/* ~/Documents/workspace/SemanticMath/src/thmp/data/pe

rm ~/Documents/workspace/SemanticMath/src/thmp/data/mx/*
#mkdir -p ~/Documents/workspace/SemanticMath/src/thmp/data/vecs
scp yihed@byblis67.wolfram.com:thm/src/thmp/data/mx/* ~/Documents/workspace/SemanticMath/src/thmp/data/mx

scp yihed@byblis67.wolfram.com:thm/src/thmp/data/searchConfiguration.dat ~/Documents/workspace/SemanticMath/src/thmp/data
scp yihed@byblis67.wolfram.com:thm/src/thmp/data/literalSearchIndexMap.dat ~/Documents/workspace/SemanticMath/src/thmp/data
scp yihed@byblis67.wolfram.com:thm/src/thmp/data/literalSearchIndexMapKeys.txt ~/Documents/workspace/SemanticMath/src/thmp/data

#scp yihed@byblis.wolfram.com:thm/src/thmp/data/allThmsList.txt ~/Documents/workspace/SemanticMath/src/thmp/data
