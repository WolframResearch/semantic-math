(* Classifies MSC *)

BeginPackage["MscClassify`"]

createPaperListSparseVecPos::usage="Create list of pos list
and scores for sparse array, and list of msc classes lists"

findNearestClasses::usage = "interprets string, create vec, find nearest classes"
findNearestClassesForSA::usage = "intermediate method used to find msc, used in testing."
cleanMscList::usage = "Clean up list. Preserves length."
getNontrivialIndices::usage= "pick out indices in sparse array above minimum norm"
initialize::usage = "initialize resources, e.g. loading model"

Needs["MscNet`"]

Begin["`Private`"]

$commaSemicolonRegex = RegularExpression["[;,]"];

(*Initialize resources, e.g. loads model.*)
initialize[dirPath_] := Module[{},
	$mscModel = Import[FileNameJoin[{dirPath, $modelFileName}]];
]

(*List version of createPaperListSparseVecPos. 
Returns: two lists, {wordSparseArPosList, mscListList}.*)
createPaperListSparseVecPos[paths_List] := 
 Module[{lists, offset = 0, posList, classList},
  lists = Map[(
      {posList, classList} = createPaperListSparseVecPos[#, offset];
      offset += Length[classList];
      {posList, classList}
      ) &,
    paths];
  lists = Transpose[lists];
  {Join @@ lists[[1]], Join @@ lists[[2]]}
  ]
  
(*Args: file alternating pairs of word-freq data, e.g.
"er,1;imply,12; maxim, 1", and msc classes. Returns: list of pos list
and scores for sparse array, and list of msc classes lists.
Usage: e.g. {wordSparseArPosList, mscListList} = ... *)
createPaperListSparseVecPos[path_String, offset_Integer: 0] := 
 Module[{lines, linesLen, dataLines, mscLines, lists, wordPosList, 
   wordListLenThreshold = 100, paperCounter = 1},
  lines = ReadList[path, "String"];
  linesLen = Length[lines];
  (*odd-numbered lines are word freq data. Even-
  numbered lines classes*)
  dataLines = lines[[1 ;; linesLen ;; 2]];
  mscLines = lines[[2 ;; linesLen ;; 2]];
  If[Length[dataLines] =!= Length[mscLines],
   Print["Length[dataLines]=!=Length[mscLines]"];
   Return[Null, Module];
   ];
  lists = Map[(
      wordPosList = 
       parseWordScorePos[dataLines[[#]], offset + paperCounter];
      If[Length[wordPosList] < wordListLenThreshold,
       Nothing,
       paperCounter++;
       {wordPosList, StringSplit[mscLines[[#]], ","]}
       ]
      ) &
    , Range[Length[dataLines]]
    ];
  lists = Transpose[lists];
  {lists[[1]], cleanMscList[lists[[2]]]}
  ]

(*Clean up list.*)
cleanMscList[mscListList_List] := 
 Module[{curList, mscStrLen = 5, curMscLen, curMsc},
  Map[(curList = #;
     Map[(curMscLen = StringLength[#];
        If[Mod[curMscLen, mscStrLen] === 0 && curMscLen > mscStrLen,
         (*e.g. "37D3037D0537D1037B25"*)
         curMsc = #;
         Sequence @@ 
          Map[StringTake[
             curMsc, {(# - 1)*mscStrLen + 1, #*mscStrLen}] & , 
           Range[curMscLen/mscStrLen]]
         ,
         #
         ]
        ) &,
      curList]
     ) &
   , mscListList]
  ]  

(*filter out trivial vecs with low norm. Not many features, 
obfuscates classification. Args: mx to remove from, sparse. Rows are 
features, columns represent samples. Returns indices to pick, i.e. of 
sufficiently high norm.*)
getNontrivialIndices[sparseMx_SparseArray, 
  projectionMx_: $dInverseUTranspose] := 
 Module[{normThreshold = 0.09, denseMx, counter = 0, pickedIndices},
  denseMx = Transpose[projectionMx.sparseMx];
  Print["Dimensions[denseMx]: ",Dimensions[denseMx]];
  pickedIndices = Map[(
      counter++;
      If[Norm[#] < normThreshold,
       Nothing,
       counter
       ]
      ) &
    , denseMx];
  pickedIndices
  ]
  
(*given string containing word freq data, e.g. "er,1;imply,12; maxim, \
1", get word pos\[Rule] word freq rules. Discard first element, which \
is paper id*)
parseWordScorePos[str_String, paperIndex_Integer] := 
 Module[{dataList, wordList, dataListLen, freqList},
  dataList = StringSplit[str, $commaSemicolonRegex];
  dataListLen = Length[dataList];
  If[EvenQ[dataListLen],
   Print["Length of data list must be odd!"];
   Return[Null, Module];
   ];
  wordList = dataList[[2 ;; dataListLen ;; 2]];
  freqList = ToExpression[dataList[[3 ;; dataListLen ;; 2]]];
  (*form (word-index)->(score) assoc *)
  
  createSparseVecPosAssoc[wordList, freqList, paperIndex]
  ]
  
(* *Evaluation* time, not model building time. Without paperId field*)
parseEvalWordScorePos[str_String] := 
 Module[{dataList, wordList, dataListLen, freqList, 
   paperIndex = 1},
  dataList = StringSplit[str, $commaSemicolonRegex];
  dataListLen = Length[dataList];
  If[OddQ[dataListLen],
   Print["Length of data list must be even!"];
   Return[Null, Module];
   ];
  wordList = dataList[[1 ;; dataListLen ;; 2]];
  freqList = ToExpression[dataList[[2 ;; dataListLen ;; 2]]];
  (*form (word-index)->(score) assoc *)
  
  createSparseVecPosAssoc[wordList, freqList, paperIndex]
  ]
  
(*form (word-index)->(score) assoc *)
createSparseVecPosAssoc[wordList_List, freqList_List, 
  paperIndex_: 1] := 
 Module[{word, wordFreq, wordIndex, wordAdj, 
   wordScore, freqListMean, wordMapScore},
  
  (*form (word-index)->(score) assoc *)
  Map[(
     word = wordList[[#]];
     wordIndex = $wordIndexAssoc[word];
     If[Head[wordIndex] === Missing,
      Nothing
      ,
      wordFreq = freqList[[#]];
      wordAdj = $wordFreqAdjAssoc[word];
      If[Head[wordAdj] === Missing,
       wordAdj = 1.;
       ];
      wordMapScore = $wordScoreMapAssoc[word];
      wordScore = If[Head[wordScore] === Missing,
        4.,
        wordMapScore
        ];
      freqListMean = Mean[freqList];
      wordScore = If[wordScore >= 5 && wordFreq === 1,
        wordAdj*wordScore*wordFreq*3./freqListMean
        ,
        wordAdj*wordScore*wordFreq/freqListMean
        ];
      wordScore = Which[wordScore > 4,
        4.
        ,
        wordScore < 2 && wordMapScore > 0,
        2.
        ,
        True,
        wordScore
        ];
      {wordIndex, paperIndex} -> wordScore
      ]
     ) &,
   Range[Length[wordList]]]
  ]
  
(*Evaluation time*)  
 
(*This is called by Java servlet in app*)
(*
Args: data string, e.g. "er,1;imply,12; maxim, 1".
model: neural net model to filter classes.
Returns: nearest classes list.
*)
findNearestClasses[dataStr_String, model_:$mscModel, numNearest_Integer:8] := 
 Module[{sparseAr, classList, preList},
  sparseAr = 
   SparseArray[
    parseEvalWordScorePos[
     dataStr], {Length[$wordIndexAssoc], 1}];
  (*Can be more generous, can use neural net filter later*)
  numNearest += 4;
  preList = Keys[findNearestClassesForSA[sparseAr, numNearest]];
  (*filter out those that are low on *)
  classList = filterMscListWithNet[preList, model, dataStr];
  
  (******classList[[1;;Min[Length[classList],numNearest]]]*)
  Join[preList, {"14A00"}, classList[[1;;Min[Length[classList],numNearest]]]]
  ]  

(*Filter out less relevant ones based on neural net results.
Args: classList, list of msc classes from nearest-neighbors.
Guaranteed to each have 5 digits.
model: the model to run the predictions on.
Returns: list of filtered classes
*)
filterMscListWithNet[classList_List, model_, data_String] := Module[{topN=3,
	dataVec, predicted},
	
	dataVec = createDataIntegerVec[data, $wordIndexAssoc];
	predicted = model[dataVec];
	(*(*or can just take top 3*)
	preList = Map[(
		StringTake[#, 2]
		)&
		, classList];		
	preList = DeleteDuplicates[preList];	
	topN = Length[preList];*)
	
	predicted = vecToTopMsc[predicted, topN];
	(*Select[classList, (MemberQ[predicted, StringTake[#,2]] )&]***)
	Join[Select[classList, (MemberQ[predicted, StringTake[#,2]] )&], predicted]
	
]

(*Reduces dimension on input vec, a sparse array. Input: sparse
array. Number of nearest vectors to take. 
Returns: association of nearest classes and their scores. *)

findNearestClassesForSA[vec_SparseArray, numNearest_Integer] := 
 Module[{vecReduced, nearestInd, nearestDist, bonusList, 
   nearestToSearch = Max[20, numNearest]},
  
  vecReduced = Transpose[$dInverseUTranspose.vec][[1]];
  {nearestInd, nearestDist} = 
   Transpose[
    Nearest[$v -> {"Index", "Distance"}, vecReduced, nearestToSearch]];
  (***Print[Map[$mscListList[[#]]&,nearestInd]]; don't delete*)
  
  Print["nearestInd: ", nearestInd];
  Print["nearestDist: ", nearestDist];
  Print[Map[$mscListList[[#]] &, nearestInd]];
  (*nearestInd=Map[(
  If[EuclideanDistance[$v[[#]],0.]<0.02,
  Nothing,
  #
  ]
  )&,
  nearestInd];*)
  {nearestInd, bonusList} = 
   computeNearestClasses[nearestInd, numNearest];
  (*bonusList records ones that are underrepresented*)
  (*nearestInd=
  Normal@KeyDrop[nearestInd,{"34L20","34L10","47e05","47E05"}];*)
  
  If[{} === bonusList,
   nearestInd[[1 ;; Min[numNearest, Length[nearestInd]]]]
   ,
   nearestInd = nearestInd[[1 ;; Min[numNearest, Length[nearestInd]]]];
   Join[nearestInd, bonusList]
   ]
  ]
  
(*Compute the nearest classes given list of nearest indices. Gather \
up the most frequent ones. Each class has form e.g. "30C65","58A10", \
but some use e.g. 30x, or 30C, or other abbreviated form 
 51 -> 4, 35 -> 4, 
 
>    83 -> 4, 57 -> 4, 32 -> 5, 47 -> 5, 37 -> 6, 58 -> 6, 34 -> 6
*)
computeNearestClasses[nearestInd_List, numNearest_Integer] := 
 Module[{curMscList, curMscListLenScore, mscListScore, 
   rankCounter = 1, curMscRankScore, freqMap2, freqMap3, 
   freqAssoc5 = <||>, freqList5, gatheredBag = Internal`Bag[], curMsc,
    curMscLen, curFreqAssoc3, freqAssoc3 = <||>, first2, first3, 
   first5, distinctFirst2List, 
   bonusFirst2Assoc = <|"32" -> 1, "34" -> 1, "60" -> 1, "68" -> 1, 
     "81" -> 1, "51" -> 1, "35" -> 1,
     "83" -> 1, "57" -> 1, "47" -> 1, "20" -> 1, "37" -> 1, "58" -> 1|>,
    curMscBonusQ, bonusList, bonusBag = Internal`Bag[], 
   selectedFirst2},
  Scan[(
     curMscList = $mscListList[[#]];
     (*longer lists of different classes, each member weighs less. 
     This can be precomputed!*)
     
     distinctFirst2List = 
      DeleteDuplicates[
       Map[If[StringLength[#] > 1, StringTake[#, 2], Nothing] &, 
        curMscList]];
     curMscListLenScore = 
      1./(4 + Exp[Length[distinctFirst2List]/12]);
     (*rank in indices list*)
     
     curMscRankScore = 1./(2.5 + Exp[rankCounter++/8]);
     mscListScore = curMscListLenScore*curMscRankScore;
     curFreqAssoc3 = <||>;
     Scan[(curMsc = #;
        curMscLen = StringLength[curMsc];
        curMscBonusQ = 
         If[curMscLen > 1 && 
           Head[bonusFirst2Assoc[StringTake[curMsc, 2]]] =!= Missing,
          True,
          False
          ];
        Which[curMscLen > 4,
         first5 = StringTake[curMsc, 5];
         first3 = StringTake[curMsc, 3];
         first2 = StringTake[curMsc, 2];
         Internal`StuffBag[gatheredBag, first5];
         freqAssoc5[first5] = 
          If[Head[freqAssoc5[first5]] === Missing,
           mscListScore,
           freqAssoc5[first5] + mscListScore
           ];
         If[curMscBonusQ, Internal`StuffBag[bonusBag, first5]];
         freqMap3[first3] = If[NumberQ[freqMap3[first3]],
           freqMap3[first3] + mscListScore,
           mscListScore
           ];
         curFreqAssoc3[first3] = 
          If[Head[curFreqAssoc3[first3]] === Missing,
           1,
           curFreqAssoc3[first3] + 1
           ];
         freqMap2[first2] = If[NumberQ[freqMap2[first2]],
           freqMap2[first2] + mscListScore,
           mscListScore
           ];
         ,
         curMscLen > 2,
         first3 = StringTake[curMsc, 3];
         first2 = StringTake[curMsc, 2];
         freqMap3[first3] = If[NumberQ[freqMap3[first3]],
           freqMap3[first3] + mscListScore,
           mscListScore
           ];
         curFreqAssoc3[first3] = 
          If[Head[curFreqAssoc3[first3]] === Missing,
           1,
           curFreqAssoc3[first3] + 1
           ];
         freqMap2[first2] = If[NumberQ[freqMap2[first2]],
           freqMap2[first2] + mscListScore,
           mscListScore
           ];
         ,
         curMscLen > 1,
         first2 = StringTake[curMsc, 2];
         freqMap2[first2] = If[NumberQ[freqMap2[first2]],
           freqMap2[first2] + mscListScore,
           mscListScore
           ];
         ]
        ) &
      , curMscList];
     Scan[(
        freqAssoc3[Keys[#]] = If[Head[freqAssoc3[Keys[#]]] === Missing,
          1,
          freqAssoc3[Keys[#]] + 1
          ]
        ) &,
      Normal[curFreqAssoc3]
      ];
     ) &,
   nearestInd];
  bonusList = DeleteDuplicates[Internal`BagPart[bonusBag, All]];
  bonusList = SortBy[bonusList, -freqAssoc5[#] &];
  freqList5 = Normal[freqAssoc5];
  (*Demand at least two papers agree on 3-digit class, 
  to avoid junk if confidence is low. 
  This is unreliable for 7500 papers, 
  need larger dataset to enable*)
  (*freqList5=Map[(
  If[freqAssoc3[StringTake[Keys[#],3]]>1,
  #,
  Nothing
  ]
  )&,
  freqList5];*)
  
  freqList5 = 
   SortBy[freqList5, {(-Values[#]) &, -freqMap2[
        StringTake[Keys[#], 2]] &, -freqMap3[
        StringTake[Keys[#], 3]] &}];
  (*Length[freqList5] > numNearest *)
  If[{} =!= bonusList && 
    freqAssoc5[bonusList[[1]]] < 
     freqAssoc5[freqList5[[numNearest]]]/1.3,
   (*if below minimum score threshold*)
   bonusList = {}
   ];
  selectedFirst2 = 
   Map[StringTake[Keys[#], 2] &, 
    freqList5[[1 ;; Min[numNearest, Length[freqList5]]]]];
  bonusList = 
   Select[bonusList, ! MemberQ[selectedFirst2, StringTake[#, 2]] &, 1];
   
  bonusList = Map[# -> freqAssoc5[#] &, bonusList];
  {freqList5, bonusList}
  ]  
  
End[]
EndPackage[]  