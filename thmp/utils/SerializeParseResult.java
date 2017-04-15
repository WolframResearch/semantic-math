package thmp.utils;

import java.util.ArrayList;
import java.util.List;

import thmp.ParseRun;
import thmp.ParseState;
import thmp.ParseStruct;
import thmp.test.ParseEqualityCheck.ParseResult;
import thmp.ParseState.ParseStateBuilder;

/**
 * Serialize parse result. Used for testing for now.
 * 
 * @author yihed
 */
public class SerializeParseResult {

	public static String parseResultSerialFile = "src/thmp/parseResultSerialFile.dat";
	
	public static void f(){
		
	}
	
	public static void main(String args[]){
		List<String> inputStrList = new ArrayList<String>();
		addInputToList(inputStrList);
		List<ParseResult> parseResultList = new ArrayList<ParseResult>();
		
		boolean isVerbose = false;
		ParseStateBuilder parseStateBuilder = new ParseStateBuilder();		
		ParseState parseState = parseStateBuilder.build();
		
		for(String inputString : inputStrList){
			ParseRun.parseInput(inputString, parseState, isVerbose);
			ParseStruct headParseStruct = parseState.getHeadParseStruct();
			System.out.println("headParseStruct " + headParseStruct);
			ParseResult pr = new ParseResult(inputString, headParseStruct);
			parseResultList.add(pr);
		}
		FileUtils.serializeObjToFile(parseResultList, parseResultSerialFile);
	}

	/**
	 * @param inputStrList
	 */
	private static void addInputToList(List<String> inputStrList) {
		inputStrList.add("take derivative of log of f");
		inputStrList.add("$M/gM$ is Cohen-Macaulay with maximal regular sequence $f_1, \\ldots, f_{d-1}$.");
		
	}
	
}
