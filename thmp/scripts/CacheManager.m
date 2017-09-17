(* ::Package:: *)

BeginPackage["CacheManager`"]


(* ::Input:: *)
(*(*Manage cache of mx loading*)*)


$TotalMxCount;$MxCountCap;$ZeroVec;


(* ::Item:: *)
(*given threshold, select ones below that  . *)


findNearest[combinedTDMx_,queryVec_,threshold_Real,numNearest_Integer]:=Module[{indexDistAr}, indexDistAr=Nearest[combinedTDMx->{"Index","Distance"},queryVec,numNearest];
Select[indexDistAr,#[[2]]<threshold&][[All,1]]-1]


(*Use this, returns list with two sublists, first is indices, second distances*)
findNearestDist[combinedTDMx_,queryVec_,threshold_Real,numNearest_Integer]:=Module[{indexDistAr,selected,validIndices}, indexDistAr=Nearest[combinedTDMx->{"Index","Distance"},queryVec,numNearest];
selected=Select[indexDistAr,#[[2]]<threshold&];If[{}=!=selected,validIndices=filterNonSense[combinedTDMx[[selected[[All,1]]]]];
{selected[[validIndices,1]]-1,selected[[validIndices,2]]},{{},{}}]]


(*filter out vecs that are close to the zero vec, without much content, e.g. those that contain a lot of tex. Treshold 0.0035 for 5337 words, 0.0001 for 23000 words*)
filterNonSense[candidateVecs_?MatrixQ]:=Module[{list},list=Reap[Map[If[EuclideanDistance[$ZeroVec,candidateVecs[[#]]]>0.0001,Sow[#]]&,
Range[Length[candidateVecs]]]];If[list[[2]]=!={},list[[2,1]],{}]]


(* ::Input:: *)
(*select[threshold_Real]:=Select[ar,#[[2]]<threshold&]*)


(* ::Item:: *)
(*find the one with least recent time stamp. Keep stuff in bag, fill the bag the number of times as the total number of Mx. ~300 in total as of Sept 2017.*)


(* ::Item:: *)
(*Returns  *)


(* ::Input:: *)
(*(*1 means already loaded, 0 otherwise. Returns cacheBag and timeBag*)*)


(*mxRowDimension, is number of words*)
initializeCache[totalMxCount_Integer,mxCountCap_Integer,tdMxRowDim_Integer]:=Module[{cacheBag,timeBag},cacheBag=Internal`Bag[];
Table[Internal`StuffBag[cacheBag,0],totalMxCount];timeBag=Internal`Bag[];Table[Internal`StuffBag[timeBag,0],totalMxCount];
$TotalMxCount=totalMxCount; $MxCountCap=mxCountCap;$HistoryLength=0;$ZeroVec=Table[0.0,tdMxRowDim];{cacheBag,timeBag}]


(* ::Item:: *)
(*mxIndex is the index of the mx to load. modifies bags in place. Java calls this function.*)


(*mxIndex is 0-based indexing!*)
loadMx[mxIndex_Integer(*0-based*),cacheBag_,timeBag_]:=Module[{},
If[Internal`BagPart[cacheBag,mxIndex+1]===0,
If[cacheExceedsCapacity[cacheBag],loadMxAndClear[mxIndex,cacheBag,timeBag],loadMx[mxIndex]]]]


cacheExceedsCapacity[cacheBag_]:=Total[Internal`BagPart[cacheBag,All]]>$MxCountCap


(* ::Item:: *)
(*loads the mx, Clears away from memory if necessary. Picks the one with least recent timestamp.*)


loadMxAndClear[mxIndex_Integer(*0-based*),cacheBag_,timeBag_]:=Module[{path,mxEvictIndex,mxEvictTime},
path=getMxPathFromSymbol[mxIndex];mxEvictIndex=1;mxEvictTime=Internal`BagPart[timeBag,1];
Map[If[Internal`BagPart[timeBag,#]<mxEvictTime, mxEvictTime=Internal`BagPart[timeBag,#];
mxEvictIndex=#]&,Range[2,$TotalMxCount]];
(*clear symbol*)Internal`BagPart[cacheBag,mxEvictIndex]=0;
Clear[getMxNameFromSymbol[mxEvictIndex-1]];
Internal`BagPart[timeBag,mxIndex+1]=Now;Internal`BagPart[cacheBag,mxIndex+1]=1;
Get[path]];


(* ::Item:: *)
(*Loop over the cacheBag, pick out the least recently used amongst those used*)


loadMx[mxIndex_Integer(*0-based*)]:=With[{path=getMxPathFromSymbol[mxIndex]},Get[path]];


(* ::Item:: *)
(*Should concatenate with root of path as well.*)


getMxPathFromSymbol[mxIndex_Integer(*0-based*)]:=FileNameJoin[{"src","thmp","data","mx","CombinedTDMatrix"<>ToString[mxIndex]<>".mx"}]


getMxNameFromSymbol[mxIndex_Integer(*0-based*)]:="CombinedTDMatrix"<>ToString[mxIndex]<>".mx"


(*Not sure when this would be called? Since there a kernel is supposed to be used perpetually*)
cleanUpCache[]:=($HistoryLength=\[Infinity])


EndPackage[]
