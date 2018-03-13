(* Utilities for training net for msc classifier *)

BeginPackage["MscNet`"]

createDataIntegerVec::usage = "functions for creating data";

createDataAndLabels::usage = "create data and labels given file path"

createNet::usage = "Create net";
trainNet::usage = "Train net";

vecToMsc::usage = "Given model output, Predict which classes the neural net output likely gives.";
vecToTopMsc::usage = "Given model vec, predict top mscs";

predictMsc::usage = "Given path containing data line, predict msc";

$modelFileName = "msc2March12.wlnet";

Begin["`Private`"]

commaSemicolonReg = RegularExpression["[;,]"];

(*need to load $wordIndexAssoc from mx file before this*)

$mscClassList = {"28", "58", "60", "82", "62", "65", "90", "68", "83", "80", "81", \
"86", "85", "02", "03", "26", "01", "06", "22", "05", "46", "47", \
"08", "45", "42", "43", "40", "41", "35", "13", "32", "14", "31", \
"49", "33", "00", "34", "76", "74", "73", "70", "91", "20", "93", \
"92", "94", "97", "78", "11", "10", "39", "12", "15", "04", "17", \
"16", "19", "54", "57", "30", "51", "50", "53", "52", "55", "37", \
"18", "44"};

(*Create association for classes and their indices*)
createMscListAssoc[mscClassList_List] := Module[{counter = 1},
  Association[Map[(# -> counter++) &
    , mscClassList]
   ]
  ]

$mscIndexAssoc := $mscClassAssoc = createMscListAssoc[$mscClassList];
$mscIndexAssocLen := $mscIndexAssocLen = Length[$mscIndexAssoc];

$indexMscAssoc := $indexMscAssoc =
 AssociationMap[(Values[#] -> Keys[#]) &, $mscIndexAssoc];
 
(******Read data from file, and creates data and labels lists*******)

(*Args: file containing alternating pairs of word-freq data, e.g.
"er,1;imply,12; maxim, 1", and msc classes in subsequent line. 
*)
createDataAndLabelsImpl[filePath_String] := Module[{lines, linesLen, 
	dataLines, mscLines, lists, wordVec, mscVec,
	(*minimum number of words*) wordListLenThreshold = 50},
  lines = ReadList[filePath, "String"];
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
  	  
      wordVec = createDataIntegerVec[dataLines[[#]]];
      
      If[Length[wordVec] < wordListLenThreshold,
       Nothing
       ,
       
       mscVec = createLabelVec[mscLines[[#]]];       
       {wordVec, mscVec}
       ]
      ) &
    , Range[Length[dataLines]]
    ];
    
    lists
 
 ];
 
 createDataAndLabels[filePath_String] := Module[{lists},
 	lists = createDataAndLabelsImpl[filePath];
 	lists = Transpose[lists];
  	{lists[[1]], lists[[2]]}
 ]
 
(*Take list of files containing training data*)
createDataAndLabels[filePaths_List] := Module[{lists},
	lists = Map[(
		Sequence@@createDataAndLabelsImpl[#]	
		)&
		, filePaths];
	lists = Transpose[lists];
	{lists[[1]], lists[[2]]}	
] 

(*Clean up list. *)
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
 
(********DATA********)
(**TRAIN & EVAL TIME**)

(*functions for creating data, i.e. list of integers per msc data sample. 
Input: each string contains one line of the words and their freq. The first
string is the paper string, hence starting at index 2.
Returns: list of integers (variable length)*)
createDataIntegerVec[data_String, wordIndexAssoc_:$wordIndexAssoc] := Module[{dataList},
  (*Each data string has form "0708.0309;scien,2;compatible almost \
	complex,2;projecti,6;"*)
  
  dataList = StringSplit[data, commaSemicolonReg];
  dataList = dataList[[2 ;; -1 ;; 2]];
  Map[(
     If[Head[wordIndexAssoc[#]] =!= Missing,
      wordIndexAssoc[#]
      ,
      Nothing
      ]
     ) &, dataList]
  ]

(******Data labels********)

(**TRAIN TIME**)
(*Create labels. Input: string e.g. "60K35,82C22 ", "53C55,53C10,53C15". 
Returns fixed length vec of 1's and 0's.*)
createLabelVec[labelStr_String] := 
 Module[{labelList, zeroVec, mscIndex},
  labelList = StringSplit[labelStr, ","];
  labelList = cleanMscList[labelList];
  
  labelList = Reap[
    Scan[(
       If[StringLength[#] > 1,
        mscIndex = $mscIndexAssoc[StringTake[#, 2]];
        If[Head[mscIndex] =!= Missing,
         Sow[mscIndex, "label"]
         ]
        ]
       ) &, labelList
     ], "label"
    ];
   labelList = If[labelList[[2]] =!= {},
	   labelList[[2]][[1]]
	   ,
	   {}
   ];
  zeroVec = Table[0, $mscIndexAssocLen];
  Scan[(zeroVec[[#]] = 1) &
   , labelList];
  zeroVec
  ]
  
(***RUNTIME***)
(*produce labels given net output vec, which are floats. 
Input: {0.03,...,1.02..0.93...}. 
Predetermined threshold, for when the net output is likely class.
Output: list of labels.
*)
vecToMsc[predicted_List, thresh_:0.5] := Module[{posList},
	
	(*posList = Select[Range[Length[predicted]], (predicted[[#]] > thresh) &];*)
	
	posList = MapIndexed[If[#1 > thresh, #2[[1]], Nothing] &, predicted];
	Map[(*this Head can't be Missing by construction of $indexMscAssoc*)
		   $indexMscAssoc[#] &
	  , posList]
  ]
  
(*From net output predict most probable n classes
Args: predicted: net output vec of floats.
topN: How many top ones to get.
Output: list of msc classes.
*)
vecToTopMsc[predicted_List, topN_:1] := Module[{posList},
	
	posList = TakeLargestBy[Range[Length[predicted]], (predicted[[#]]) &, topN];
	
	Map[(*this Head can't be Missing by construction of $indexMscAssoc*)
		   $indexMscAssoc[#] &
	  , posList]
	  
  ]
 
(*******Create and Train net*******)

embedDim = 128; vocabSize := vocabSize = Length[$wordIndexAssoc];

createNet[] := Module[{},
	NetChain[{EmbeddingLayer[embedDim, vocabSize, 
     "Input" -> "Varying"], AggregationLayer[Total, 1], 
    LinearLayer[$mscIndexAssocLen]}]
]
        
(*Train e.g. NetTrain[net1, {{1, 2}, {1}} -> {{1, 0}, {0, 1}}]*)
trainNet[net_, data_List, labels_List] := Module[{dataLen},
	dataLen = Length[data];
	If[dataLen =!= Length[labels],
		Print["Data and labels lists must have same length."];
		Return[Null, Module];
	];
	
	NetTrain[net, data -> labels]

]

(******EVAL TIME******)

(*Given file, create data vec from first line,
and predict the msc classes given eval vector. *)
predictMsc[model_, filePath_String] := Module[{stream,
	line, dataVec, predicted},
	
	stream = OpenRead[filePath];
	line = ReadLine[stream];
	Close[stream];
	
	dataVec = createDataIntegerVec[line];
	predicted = model[dataVec];
	Print["model output: ",predicted];
	vecToMsc[predicted]
	
]

End[]

EndPackage[]