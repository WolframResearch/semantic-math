(* ::Package:: *)

(*Generate names of math files of the format 1234.5678.*)

Import["/Users/yihed/Documents/workspace/Other/src/thmpAux/\
PaperIDToCategories.m", "Package"]

writeFile1[] :=
  Module[{list, listString, stream},
	   list = Reap[
		       Map[If[StringMatchQ[Keys[#],
					              RegularExpression["\\d+\\.\\d+"]] &&
			      MemberQ[Values[#], _?(StringTake[#, UpTo[4]] === "math" &)],
			      Sow[Keys[#]]] &, file]][[2]][[1]];
	 listString = ExportString[list, "Text"]; stream = OpenWrite[];
	 WriteString[stream, listString]; Print[Close[stream]]]
