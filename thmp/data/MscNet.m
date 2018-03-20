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
(*nme of model with label length 3*)
$model3FileName = "msc3March14.wlnet";

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
  
(***assocs for length-2 Labels***)
$mscIndexAssoc := $mscClassAssoc = createMscListAssoc[$mscClassList];
$mscIndexAssocLen := $mscIndexAssocLen = Length[$mscIndexAssoc];

$indexMscAssoc := $indexMscAssoc =
 AssociationMap[(Values[#] -> Keys[#]) &, $mscIndexAssoc];
 
(***assocs for length-3 Labels***)
$mscIndexAssoc3 := $mscClassAssoc3 = createMscListAssoc[$mscClassList3];
$mscIndexAssocLen3 := $mscIndexAssocLen3 = Length[$mscIndexAssoc3];

$indexMscAssoc3 := $indexMscAssoc3 =
 AssociationMap[(Values[#] -> Keys[#]) &, $mscIndexAssoc3];
 
(******Read data from file, and creates data and labels lists*******)

(*Args: file containing alternating pairs of word-freq data, e.g.
"er,1;imply,12; maxim, 1", and msc classes in subsequent line. 
labelLen, length of created label, can be 2-5.
*)
createDataAndLabelsImpl[filePath_String, labelLen_:2] := Module[{lines, linesLen, 
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
       mscVec = createLabelVec[mscLines[[#]], labelLen];       
       {wordVec, mscVec}
       ]
      ) &
    , Range[Length[dataLines]]
    ];    
    lists 
 ];
 
createDataAndLabels[filePath_String, labelLen_:2] := Module[{lists},
 	lists = createDataAndLabelsImpl[filePath, labelLen];
 	lists = Transpose[lists];
  	{lists[[1]], lists[[2]]}
 ]
 
(*Take list of files containing training data*)
createDataAndLabels[filePaths_List, labelLen_:2] := Module[{lists},
	lists = Map[(
		Sequence@@createDataAndLabelsImpl[#, labelLen]	
		)&
		, filePaths];
	lists = Transpose[lists];
	{lists[[1]], lists[[2]]}	
] 

(*Clean up msc class list. *)
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
createDataIntegerVec[data_String, wordIndexAssoc_:$wordIndexAssoc] := Module[{dataList, 
	startIndex=2, firstWords},
  (*Each data string has form "0708.0309;scien,2;compatible almost
	complex,2;projecti,6;" Or can start with semicolon: "; ...;"*)
  If[StringTake[data, 1] === ";",
  	startIndex = 1;
  ]; 
  (*Note some file data can have the form 1311.4130,unary operation,half,coh..., i.e. without
  the count, process these by taking all their words*)
  (*remove this after processing these data, since don't need this check at runtime*)
  firstWords = StringTake[data, 12];
  If[!StringContainsQ[firstWords, ";"],
  	dataList = StringSplit[data, ","];
  	dataList = dataList[[2 ;; -1]];
  	,
  	dataList = StringSplit[data, commaSemicolonReg];
 	dataList = dataList[[startIndex ;; -1 ;; 2]];
  ];
  
  (**use this at runtime: dataList = StringSplit[data, commaSemicolonReg];
  dataList = dataList[[startIndex ;; -1 ;; 2]];*)
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
(*Create labels. 
Args: string e.g. "60K35,82C22 ", "53C55,53C10,53C15". 
labelLen: length of desired label, can be 2 to 5.
Returns fixed length vec of 1's and 0's.*)
createLabelVec[labelStr_String, labelLen_:2] := 
 Module[{labelList, zeroVec, mscIndex, mscIndexAssoc,
 	mscIndexAssocLen},
  labelList = StringSplit[labelStr, ","];
  labelList = cleanMscList[labelList];
  
  If[labelLen === 3,
  	mscIndexAssoc = $mscIndexAssoc3;
  	mscIndexAssocLen = $mscIndexAssocLen3;
  	,
  	mscIndexAssoc = $mscIndexAssoc;
  	mscIndexAssocLen = $mscIndexAssocLen; 
  ];
  
  labelList = Reap[
    Scan[(
       If[StringLength[#] >= labelLen,
       	(*Can create label of first 2 or 3 digits*)
        mscIndex = mscIndexAssoc[StringTake[#, labelLen]];
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
  zeroVec = Table[0, mscIndexAssocLen];
  Scan[(zeroVec[[#]] = 1) &
   , labelList];
  zeroVec
  ]
  
(***RUNTIME***)

(**both Length-2 and Length-3 labels**)
(*produce labels given net output vec, which are floats. 
Input: {0.03,...,1.02..0.93...}. 
Predetermined threshold, for when the net output is likely class.
Output: list of labels.
*)
vecToMsc[predicted_List, thresh_:0.5] := Module[{posList, indexMscAssoc},
	
	(*posList = Select[Range[Length[predicted]], (predicted[[#]] > thresh) &];*)
	
	indexMscAssoc = If[Length[predicted] === Length[$indexMscAssoc3],
		$indexMscAssoc3,
		$indexMscAssoc
		];
		
	posList = MapIndexed[If[#1 > thresh, #2[[1]], Nothing] &, predicted];
	Map[(*this Head can't be Missing by construction of $indexMscAssoc*)
		  indexMscAssoc[#] &
	  , posList]
  ]
  
(*From net output predict most probable n classes
Args: predicted: net output vec of floats.
topN: How many top ones to get.
Output: list of msc classes.
This takes label length into account.
*)
vecToTopMsc[predicted_List, topN_:1] := Module[{posList, indexMscAssoc},
	
	posList = TakeLargestBy[Range[Length[predicted]], (predicted[[#]]) &, topN];
	
	indexMscAssoc = If[Length[predicted] === Length[$indexMscAssoc3],
		$indexMscAssoc3,
		$indexMscAssoc
		];
	
	Map[(*this Head can't be Missing by construction of $indexMscAssoc*)
		   indexMscAssoc[#] &
	  , posList]
	  
  ]
  
 
(*******Create and Train net*******)

defaultEmbedDim = 128; vocabSize := vocabSize = Length[$wordIndexAssoc];

(*for length-3 labels, e.g. 60A, use 256
128 works well for length-2 labels*)
createNet[embedDim_:defaultEmbedDim, mscIndexAssocLen_:$mscIndexAssocLen] := Module[{},
	NetChain[{EmbeddingLayer[embedDim, vocabSize, 
     "Input" -> "Varying"], AggregationLayer[Total, 1], 
    LinearLayer[mscIndexAssocLen]}]
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

(*Notes on training:
March 14, length-3 labels:
100     10   4489    2.17e-3    2.41e-3    1.00e-3         94   4h20m53s         3s
*)

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

(**Additional data**)
mscClassStr3 = "00A, 00B, 01A, 02A, 02B, 02C, 02F, 02K, 03A, 03B, 03C, 03D, 03E, \
03F, 03G, 03H, 04A, 05A, 05B, 05C, 05D, 05E, 06A, 06B, 06C, 06D, 06E, \
06F, 08A, 08B, 08C, 10A, 10C, 10D, 10E, 10F, 10G, 10H, 10J, 10K, 10L, \
10M, 11A, 11B, 11C, 11D, 11E, 11F, 11G, 11H, 11J, 11K, 11L, 11M, 11N, \
11P, 11Q, 11R, 11S, 11T, 11U, 11Y, 11Z, 12A, 12B, 12C, 12D, 12E, 12F, \
12G, 12H, 12J, 12K, 12L, 12Y, 13A, 13B, 13C, 13D, 13E, 13F, 13G, 13H, \
13J, 13K, 13L, 13M, 13N, 13P, 14A, 14B, 14C, 14D, 14E, 14F, 14G, 14H, \
14J, 14K, 14L, 14M, 14N, 14P, 14Q, 14R, 14T, 15A, 15B, 16A, 16B, 16D, \
16E, 16G, 16H, 16K, 16L, 16N, 16P, 16R, 16S, 16T, 16U, 16W, 16Y, 16Z, \
17A, 17B, 17C, 17D, 17E, 18A, 18B, 18C, 18D, 18E, 18F, 18G, 18H, 19A, \
19B, 19C, 19D, 19E, 19F, 19G, 19J, 19K, 19L, 19M, 20A, 20B, 20C, 20D, \
20E, 20F, 20G, 20H, 20J, 20K, 20L, 20M, 20N, 20P, 22A, 22B, 22C, 22D, \
22E, 22F, 26A, 26B, 26C, 26D, 26E, 28A, 28B, 28C, 28D, 28E, 30A, 30B, \
30C, 30D, 30E, 30F, 30G, 30H, 30J, 30K, 30L, 31A, 31B, 31C, 31D, 31E, \
32A, 32B, 32C, 32D, 32E, 32F, 32G, 32H, 32J, 32K, 32L, 32M, 32N, 32P, \
32Q, 32S, 32T, 32U, 32V, 32W, 33A, 33B, 33C, 33D, 33E, 33F, 34A, 34B, \
34C, 34D, 34E, 34F, 34G, 34H, 34J, 34K, 34L, 34M, 34N, 35A, 35B, 35C, \
35D, 35E, 35F, 35G, 35H, 35J, 35K, 35L, 35M, 35N, 35P, 35Q, 35R, 35S, \
37A, 37B, 37C, 37D, 37E, 37F, 37G, 37H, 37J, 37K, 37L, 37M, 37N, 37P, \
39A, 39B, 39C, 40A, 40B, 40C, 40D, 40E, 40F, 40G, 40H, 40J, 41A, 42A, \
42B, 42C, 43A, 44A, 45A, 45B, 45C, 45D, 45E, 45F, 45G, 45H, 45J, 45K, \
45L, 45M, 45N, 45P, 45Q, 45R, 46A, 46B, 46C, 46D, 46E, 46F, 46G, 46H, \
46J, 46K, 46L, 46M, 46N, 46P, 46S, 46T, 47A, 47B, 47C, 47D, 47E, 47F, \
47G, 47H, 47J, 47L, 47N, 47S, 49A, 49B, 49C, 49E, 49F, 49G, 49H, 49J, \
49K, 49L, 49M, 49N, 49Q, 49R, 49S, 50A, 50C, 51A, 51B, 51C, 51D, 51E, \
51F, 51G, 51H, 51J, 51K, 51L, 51M, 51N, 51P, 52A, 52B, 52C, 53A, 53B, \
53C, 53D, 53Z, 54A, 54B, 54C, 54D, 54E, 54F, 54G, 54H, 54J, 55A, 55B, \
55C, 55D, 55E, 55F, 55G, 55H, 55J, 55M, 55N, 55P, 55Q, 55R, 55S, 55T, \
55U, 57A, 57B, 57C, 57D, 57E, 57F, 57M, 57N, 57P, 57Q, 57R, 57S, 57T, \
58A, 58B, 58C, 58D, 58E, 58F, 58G, 58H, 58J, 58K, 58Z, 60A, 60B, 60C, \
60D, 60E, 60F, 60G, 60H, 60J, 60K, 62A, 62B, 62C, 62D, 62E, 62F, 62G, \
62H, 62J, 62K, 62L, 62M, 62N, 62P, 62Q, 65A, 65B, 65C, 65D, 65E, 65F, \
65G, 65H, 65J, 65K, 65L, 65M, 65N, 65P, 65Q, 65R, 65S, 65T, 65U, 65W, \
65Y, 65Z, 68A, 68B, 68C, 68D, 68E, 68G, 68H, 68K, 68M, 68N, 68P, 68Q, \
68R, 68T, 68U, 68W, 70A, 70B, 70C, 70D, 70E, 70F, 70G, 70H, 70J, 70K, \
70L, 70M, 70P, 70Q, 70S, 73B, 73C, 73D, 73E, 73F, 73K, 73V, 74A, 74B, \
74C, 74D, 74E, 74F, 74G, 74H, 74J, 74K, 74L, 74M, 74N, 74P, 74Q, 74R, \
74S, 76A, 76B, 76C, 76D, 76E, 76F, 76G, 76H, 76J, 76K, 76L, 76M, 76N, \
76P, 76Q, 76R, 76S, 76T, 76U, 76V, 76W, 76X, 76Y, 76Z, 78A, 78M, 80A, \
80M, 81B, 81C, 81D, 81E, 81F, 81G, 81H, 81J, 81K, 81P, 81Q, 81R, 81S, \
81T, 81U, 81V, 82A, 82B, 82C, 82D, 83A, 83B, 83C, 83D, 83E, 83F, 85A, \
86A, 90A, 90B, 90C, 90D, 91A, 91B, 91C, 91D, 91E, 91F, 91G, 92A, 92B, \
92C, 92D, 92E, 92F, 92H, 92K, 93A, 93B, 93C, 93D, 93E, 94A, 94B, 94C, \
94D, 97A, 97B, 97C, 97D, 97E, 97F, 97G, 97H, 97I, 97K, 97M, 97N, 97P, \
97Q, 97R, 97U";

$mscClassList3 := $mscClassList3 = StringSplit[mscClassStr3, ", "];

End[]

EndPackage[]