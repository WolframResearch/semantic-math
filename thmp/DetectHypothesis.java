package thmp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;

import thmp.ParseState.ParseStateBuilder;
import thmp.ParseState.VariableDefinition;
import thmp.ThmP1.ParsedPair;
import thmp.utils.WordForms;

/**
 * Used to detect hypotheses in a sentence.
 * @author yihed
 *
 */
public class DetectHypothesis {
	
	//pattern matching is faster than calling str.contains() repeatedly 
	//which is O(mn) time.
	private static final Pattern HYP_PATTERN = Pattern.compile(".*assume.*|.*denote.*|.*define.*") ;
	private static final Pattern PUNCTUATION_PATTERN = Pattern.compile("(\\.|;|,|!|:)");
	
	//also incorporate the separator pattern from WordForms!
	//deliberately excluding "\\\\", "\\$"
	private static final Pattern SYMBOL_SEPARATOR_PATTERN = Pattern.compile("-|'|+|\\s+");
	
	/**
	 * Combination of theorem String and the list of
	 * assumptions needed to define the variables in theorem.
	 */
	public static class DefinitionListWithThm{
		
		private String thmStr;
		
		private String thmWithDefStr;
		
		private List<VariableDefinition> definitionList = new ArrayList<VariableDefinition>();
		
		public DefinitionListWithThm(String thmStr, List<VariableDefinition> definitionList,
				String thmWithDefStr){
			this.thmStr = thmStr;
			this.definitionList = definitionList;
			this.thmWithDefStr = thmWithDefStr;
		}

		/**
		 * @return the thmWithDefStr
		 */
		public String getThmWithDefStr() {
			return thmWithDefStr;
		}

		/**
		 * @return the thmSB
		 */
		public String getThmStr() {
			return thmStr;
		}

		/**
		 * @return the definitionList
		 */
		public List<VariableDefinition> getDefinitionList() {
			return definitionList;
		}
		
	}
	
	/**
	 * Whether the inputStr is a hypothesis. By checking whether the input 
	 * contains any assumption-indicating words.
	 * @param inputStr
	 * @return
	 */
	public static boolean isHypothesis(String inputStr){
		if(HYP_PATTERN.matcher(inputStr).find()){
			return true;
		}
		return false;
	}
	
	public static void main(String[] args){
		//only parse if sentence is hypothesis, when parsing outside theorems.
		//to build up variableNamesMMap. Should also collect the sentence that 
		//defines a variable, to include inside the theorem for search.
		Scanner sc = null;
		/*try{
			sc = new Scanner(new File("src/thmp/data/samplePaper2.txt"));
		}catch(FileNotFoundException e){
			e.printStackTrace();
			throw new IllegalStateException("Source file not found!");
		}*/
		
		ParseStateBuilder parseStateBuilder = new ParseStateBuilder();		
		ParseState parseState = parseStateBuilder.build();
		
		BufferedReader inputBF = null;
		try{
			inputBF = new BufferedReader(new FileReader("src/thmp/data/samplePaper2.txt"));
		}catch(FileNotFoundException e){
			e.printStackTrace();
			throw new IllegalStateException("Source file not found!");
		}
		
		try{
			readThm(inputBF, parseState);
		}catch(IOException e){
			e.printStackTrace();
		}
		
		/*
		while(sc.hasNextLine()){					
			
			String nextLine = sc.nextLine();			
			if(nextLine.matches("^\\s*$")) continue;
			System.out.println("*~~~*");
			System.out.println(nextLine + "\n");
			parseInputVerbose(nextLine, parseState);
		}*/
		
		sc.close();
		
		//how to best detect substring?! for latex symbol matching
		
		
	}
	
	
	/**
	 * Extracts list of theorems/propositions/etc from provided BufferedReader,
	 * with hypotheses added. 
	 * @param srcFileReader
	 *            BufferedReader to get tex from.
	 * @param thmWebDisplayList
	 *            List to contain theorems to display for the web. without
	 *            \labels, \index, etc. Can be null, for callers who don't need it.
	 * @param bareThmList
	 * 				bareThmList for parsing, without label content. Can be null.
	 * @param macros author-defined macros using \newtheorem
	 * @return List of unprocessed theorems read in from srcFileReader, for bag
	 *         of words search.
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	private static List<DefinitionListWithThm> readThm(BufferedReader srcFileReader, 
			//List<String> thmWebDisplayList,
			//List<String> bareThmList, 
			ParseState parseState) throws IOException	{
		
		// \\\\end\\{def(?:.*)
		//compiler will inline these, so don't count as extra calls.
		Pattern thmStartPattern = ThmInput.THM_START_PATTERN;
		Pattern thmEndPattern = ThmInput.THM_END_PATTERN;
		List<String> macrosList = new ArrayList<String>();
		//contextual sentences outside of theorems, to be scanned for
		//definitions, and parse those definitions. Reset between theorems.
		StringBuilder contextSB = new StringBuilder();
		
		List<DefinitionListWithThm> definitionListWithThmList = new ArrayList<DefinitionListWithThm>();
		
		String line;
		
		//read in custom macros, break as soon as \begin{...} encountered, 
		//in particular \begin{document}. There are no \begin{...} in the preamble
		while ((line = srcFileReader.readLine()) != null) {
			Matcher newThmMatcher = ThmInput.NEW_THM_PATTERN.matcher(line);
			
			if(ThmInput.BEGIN_PATTERN.matcher(line).find()){
				break;
			}else if(newThmMatcher.find()){
				//should be a proposition, hypothesis, etc
				if(ThmInput.THM_TERMS_PATTERN.matcher(newThmMatcher.group(2)).find()){
					macrosList.add(newThmMatcher.group(2));		
				}
			}			
		}
		
		//append list of macros to THM_START_STR and THM_END_STR
		if(!macrosList.isEmpty()){
			StringBuilder startBuilder = new StringBuilder();
			StringBuilder endBuilder = new StringBuilder();
			for(String macro : macrosList){
				//create start and end macros
				startBuilder.append("\\\\begin\\{").append(macro).append(".*");
				
				endBuilder.append("\\\\end\\{").append(macro).append(".*");
			}
			thmStartPattern = Pattern.compile(ThmInput.THM_START_STR + startBuilder);
			thmEndPattern = Pattern.compile(ThmInput.THM_END_STR + endBuilder);	
		}
			
		StringBuilder newThmSB = new StringBuilder();
		boolean inThm = false;
		while ((line = srcFileReader.readLine()) != null) {
			if (WordForms.getWhitespacePattern().matcher(line).find()){
				continue;
			}
			
			Matcher matcher = thmStartPattern.matcher(line);
			if (matcher.find()) {
				
				inThm = true;
				
				//scan contextSB for assumptions and definitions
				//and parse the definitions
				detectHypothesis(contextSB.toString(), parseState);
				
				contextSB.setLength(0);
			}
			else if (thmEndPattern.matcher(line).find()) {
				
				inThm = false;
				
				if(0 == newThmSB.length()){
					continue;
				}
				
				// process here, return two versions, one for bag of words, one
				// for display
				// strip \df, \empf. Index followed by % strip, not percent
				// don't strip.
				// replace enumerate and \item with *
				//thmWebDisplayList, and bareThmList should both be null
				String thm = ThmInput.processTex(newThmSB, null, null);
				
				//append to newThmSB additional hypotheses that are applicable to the theorem.
				DefinitionListWithThm thmDef = appendHypotheses(thm, parseState);
				
				definitionListWithThmList.add(thmDef);
				
				//should parse the theorem.
				//serialize the full parse, i.e. parsedExpression object, along with original input.				
				
				/*if (!WordForms.getWhitespacePattern().matcher(thm).find()) {
					thms.add(thm);
				}*/
				
				newThmSB.setLength(0);
				continue;
			}

			if (inThm) {
				newThmSB.append(" ").append(line);
			}else{
				//need to parse to gather definitions
				//add to contextSB
				contextSB.append(" ").append(line);
			}
		}

		// srcFileReader.close();
		// System.out.println("Inside ThmInput, thmsList " + thms);
		// System.out.println("thmWebDisplayList " + thmWebDisplayList);
		return definitionListWithThmList;
	}
	
	/**
	 * detect hypotheses and definitions, and add definitions to parseState.
	 * @param contextSB
	 * @param parseState
	 */
	private static void detectHypothesis(String contextStr, ParseState parseState){
		
		String[] contextStrAr = PUNCTUATION_PATTERN.split(contextStr);
		
		for(int i = 0; i < contextStrAr.length; i++){
			String sentence = contextStrAr[i];
			if(isHypothesis(sentence)){				
				parseInputVerbose(sentence, parseState);
			}
			
		}
		
	}
	
	/**
	 * Append hypotheses and definition statements in front of thmSB, 
	 * for the variables that do appear in thmSB.
	 * 
	 * @param thmSB
	 * @param parseState
	 */
	private static DefinitionListWithThm appendHypotheses(String thmStr, ParseState parseState){
		
		ListMultimap<String, VariableDefinition> variableNamesMMap = parseState.getVariableNamesMMap();
		//String thmStr = thmSB.toString();
		StringBuilder thmWithDefSB = new StringBuilder();
		List<VariableDefinition> variableDefinitionList = new ArrayList<VariableDefinition>();
		
		int thmStrLen = thmStr.length();	
		boolean mathMode = false;
		for(int i = 0; i < thmStrLen; i++){
			
			char curChar = thmStr.charAt(i);
			StringBuilder latexExpr = new StringBuilder();
			//go through thm, get the variables that need to be defined
			//once inside Latex, use delimiters, should also take into account
			//the case of entering math mode with \[ !
			if(curChar == '$'){
				if(!mathMode){
					mathMode = true;
					
				}else{
					mathMode = false;
					//process the latexExpr, first pick out the variables,
					//and try to find definitions for them. Appends original
					//definition strings to thmWithDefSB
					List<VariableDefinition> varDefList = pickOutVariables(latexExpr.toString(), variableNamesMMap,
							thmWithDefSB);
					
					variableDefinitionList.addAll(varDefList);
				}			
			}else if(mathMode){
				latexExpr.append(curChar);
			}
			
		}
		
		thmWithDefSB.append(thmStr);
		
		return new DefinitionListWithThm(thmStr, variableDefinitionList, thmWithDefSB.toString());
	}
	
	/**
	 * Picks out variables to be defined, and try to match them with prior definitions.
	 * @param latexExpr
	 * @param variableNamesMMap
	 * @param thmWithDefSB StringBuilder that's the original input string appended
	 * to the definition strings.
	 */
	private static List<VariableDefinition> pickOutVariables(String latexExpr, 
			ListMultimap<String, VariableDefinition> variableNamesMMap,
			StringBuilder thmWithDefSB){
		
		//list of definitions needed in this latexExpr
		List<VariableDefinition> varDefList = new ArrayList<VariableDefinition>();
		
		//split the latexExpr with delimiters
		String[] latexExprAr = SYMBOL_SEPARATOR_PATTERN.split(latexExpr);
		
		for(int i = 0; i < latexExprAr.length; i++){
			String possibleVar = latexExprAr[i];
			
			List<VariableDefinition> possibleVarDefList = variableNamesMMap.get(possibleVar);
			//get the latest definition
			int possibleVarDefListLen = possibleVarDefList.size();
			if(possibleVarDefListLen > 0){
				VariableDefinition latestVarDef = possibleVarDefList.get(possibleVarDefListLen-1);
				varDefList.add(latestVarDef);
				thmWithDefSB.append(latestVarDef.getOriginalDefinitionStr()).append(" ");
			}
			
		}
		return varDefList;
	}
	
	/**
	 * Verbose way of parsing the input. With more print statements
	 * @param inputStr
	 */
	private static void parseInputVerbose(String st, ParseState parseState){
		
		List<int[]> parseContextVecList = new ArrayList<int[]>();			
		
		String[] strAr = ThmP1.preprocess(st);			
		
		for(int i = 0; i < strAr.length; i++){
			//alternate commented out line to enable tex converter
			//ThmP1.parse(ThmP1.tokenize(TexConverter.convert(strAr[i].trim()) ));
			parseState = ThmP1.tokenize(strAr[i].trim(), parseState);
			parseState = ThmP1.parse(parseState);
			int[] curContextVec = ThmP1.getParseContextVector();
			parseContextVecList.add(curContextVec);
			//get context vector
			System.out.println("cur vec: " + Arrays.toString(curContextVec));
		}
		
		/*List<ParseStruct> headParseStructList = parseState.getHeadParseStructList();
		for(ParseStruct headParseStruct : headParseStructList){
			System.out.println("@@@" + headParseStruct);
		}*/
		
		System.out.println("@@@" + parseState.getHeadParseStruct());
		
		parseState.logState();
		
		//combine these vectors together, only add subsequent vector entry
		//if that entry is 0 in all previous vectors int[].
		int[] combinedVec = GenerateContextVector.combineContextVectors(parseContextVecList);
		System.out.println("combinedVec: " + Arrays.toString(combinedVec));
		
		String parsedOutput = ThmP1.getAndClearParseStructMapList().toString();
		//String parsedOutput = Arrays.toString(ThmP1.getParseStructMapList().toArray());			
		//String processedOutput = parsedOutput.replaceAll("MathObj", "MathObject").replaceAll("\\$([^$]+)\\$", "LaTEXMath[\"$1\"]")
				//.replaceAll("MathObject\\{([^}]+)\\}", "MathObject\\[$1\\]");					
		
		System.out.println("PARTS: " + parsedOutput);			
		System.out.println("****ParsedExpr ");
		for(ParsedPair pair : ThmP1.getAndClearParsedExpr()){
			System.out.println(pair);
		}
	}
	
}
