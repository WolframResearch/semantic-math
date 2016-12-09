package thmp;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import thmp.ThmP1.ParsedPair;
import thmp.utils.WordForms;

/**
 * Contains methods to parse verbosely or minimally without much output.
 * @author yihed
 *
 */
public class ParseRun {

	/**
	 * Parse input String.
	 * @param st
	 * @param parseState
	 * @param isVerbose whether to be verbose and print results to stdout.
	 */
	public static void parseInput(String st, ParseState parseState, boolean isVerbose){
		
		List<int[]> parseContextVecList = new ArrayList<int[]>();			
		
		String[] strAr = ThmP1.preprocess(st);			
		
		for(int i = 0; i < strAr.length; i++){
			if(WordForms.getWhitespacePattern().matcher(strAr[i]).find()){
				continue;
			}
			//alternate commented out line to enable tex converter
			//ThmP1.parse(ThmP1.tokenize(TexConverter.convert(strAr[i].trim()) ));
			parseState = ThmP1.tokenize(strAr[i].trim(), parseState);
			
			parseState = ThmP1.parse(parseState);
			int[] curContextVec = ThmP1.getParseContextVector();
			parseContextVecList.add(curContextVec);
			//get context vector
			if(isVerbose) System.out.println("cur vec: " + Arrays.toString(curContextVec));
		}
		
		if(isVerbose) {
			System.out.println("@@@" + parseState.getHeadParseStruct());
			BigInteger relationVec = parseState.getRelationalContextVec();
			System.out.println("Relational Vector num of bits set: " + (relationVec == null ? "" : relationVec.bitCount()));
		}
		parseState.logState();
		
		//combine these vectors together, only add subsequent vector entry
		//if that entry is 0 in all previous vectors int[].
		int[] combinedVec = GenerateContextVector.combineContextVectors(parseContextVecList);
		if(isVerbose) System.out.println("combinedVec: " + Arrays.toString(combinedVec));
		
		String parsedOutput = ThmP1.getAndClearParseStructMapList().toString();
		
		if(isVerbose) {
			System.out.println("PARTS: " + parsedOutput);			
			System.out.println("****ParsedExpr ");
			for(ParsedPair pair : ThmP1.getAndClearParsedExpr()){
				System.out.println(pair);
			}
		}
	}

}