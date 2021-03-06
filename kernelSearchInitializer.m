(* ::Package:: *)

BeginPackage["KernelSearchInitializer`"]
(*To be placed under servlet src dir, e.g. /usr/share/tomcat/webapps/theoremSearchTest/src*)


servletBaseDirPath=FileNameJoin[{"", "usr","share","tomcat","webapps","theoremSearchTest","src","thmp","data"}];
pathToProjectionMx=FileNameJoin[{servletBaseDirPath, "termDocumentMatrixSVD.mx"}];
combinedProjectedMxFilePath=FileNameJoin[{servletBaseDirPath, "CombinedTDMatrix.mx"}];
<<combinedProjectedMxFilePath;
<<pathToProjectionMx;
AppendTo[$ContextPath, "TermDocumentMatrix`"];
combinedRangeList=Range[Length[CombinedTDMatrix]];


EndPackage[]
