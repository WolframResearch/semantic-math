(* ::Package:: *)

BeginPackage["CacheManager`"]


(* ::Input:: *)
(*(*Manage cache of mx loading*)*)


(* ::Item:: *)
(*given threshold, select ones below that  . *)


(* ::Input:: *)
(*findNearest[threshold_Real,queryVec_,numNearest_Integer]:=Module[{indexDistAr}, indexDistAr=Nearest[indexDistAr->{"Index","Distance"},queryVec,numNearest];Select[indexDistAr,#[[2]]<threshold&][[All,1]]]*)


(* ::Input:: *)
(*select[threshold_Real]:=Select[ar,#[[2]]<threshold&]*)


(* ::Item:: *)
(*set $HistoryLength=0 at beginning of package, then restore at end.*)


(* ::Item:: *)
(*find the one with least recent time stamp. Keep stuff in bag, fill the bag the number of times as the total number of Mx. ~150 in total.*)


(* ::Item:: *)
(*Returns  *)


(* ::Input:: *)
(*$TotalMxCount;$MxCountCap=10;*)


(* ::Input:: *)
(*(*1 means already loaded, 0 otherwise. Returns cacheBag and timeBag*)*)
(*initializeCache[totalMxCount_Integer]:=Module[{cacheBag,timeBag},cacheBag=Internal`Bag[];Table[Internal`StuffBag[cacheBag,0],totalMxCount] ;timeBag=Internal`Bag[];Table[Internal`StuffBag[timeBag,0],totalMxCount];$TotalMxCount=totalMxCount; {cacheBag,timeBag}];*)


(* ::Item:: *)
(*mxIndex is the index of the mx to load. modifies bags in place. Java calls this function.*)


(* ::Input:: *)
(*loadMx[mxIndex_Integer,cacheBag_,timeBag_]:=With[{},If[Internal`BagPart[cacheBag,mxIndex]===0,If[cacheExceedsCapacity[cacheBag],loadMxAndClear[mxIndex,cacheBag,timeBag],loadMx[mxIndex]]]]*)


(* ::Input:: *)
(*cacheExceedsCapacity[cacheBag_]:=Total[cacheBag[[All,2]]]>$MxCountCap;*)


(* ::Item:: *)
(*loads the mx, Clears away from memory if necessary. Picks the one with least recent timestamp.*)


(* ::Input:: *)
(*loadMxAndClear[mxIndex_Integer,cacheBag_,timeBag_]:=Module[{path,mxEvictIndex,mxEvictTime},path="";*)
(*mxEvictIndex=1;mxEvictTime=timeBag[[1]];*)
(*Map[If[timeBag[#]<mxEvictTime,mxEvictTime=timeBag[#];mxEvictIndex=# ]&,Range[2,$TotalMxCount]];*)
(*(*clear symbol*) ;cacheBag[[mxEvictIndex]]=0;*)
(*Clear[getMxNameFromSymbol[getMxNameFromSymbol[ mxEvictIndex]]];*)
(*timeBag[[mxIndex]]=Now;cacheBag[[mxIndex]]=1;*)
(*Get[path];];*)


(* ::Item:: *)
(*Loop over the cacheBag, pick out the least recently used amongst those used*)


(* ::Input:: *)
(*loadMx[mxIndex_Integer]:=With[{path},path="";Get[path]];*)


(* ::Item:: *)
(*Should concatenate with root of path as well.*)


(* ::Input:: *)
(*getMxPathFromSymbol[mxIndex_Integer]:=FileNameJoin[{"src","thmp","data","CombinedTDMatrix"<>ToString[mxIndex]<>".mx"}];*)


(* ::Input:: *)
(*getMxNameFromSymbol[mxIndex_Integer]:="CombinedTDMatrix"<>ToString[mxIndex];*)


EndPackage[]
