package thmp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;

import thmp.DetectHypothesis.DefinitionListWithThm;
import thmp.ParseState.ParseStateBuilder;
import thmp.ParseState.VariableDefinition;
import thmp.ParseState.VariableName;
import thmp.ThmP1.ParsedPair;
import thmp.search.CollectThm;
import thmp.search.TriggerMathThm2;
import thmp.utils.FileUtils;
import thmp.utils.WordForms;

/**
 * Used to detect hypotheses in a sentence.
 * 
 * Serializes ALL_THM_WORDS_LIST to file, to be used as seed words for next time
 * search is initialized.
 * 
 * @author yihed
 *
 */
public class DetectHypothesis {
	
	//pattern matching is faster than calling str.contains() repeatedly 
	//which is O(mn) time.
	private static final Pattern HYP_PATTERN = WordForms.get_HYP_PATTERN();
	//positive look behind to split on any punctuation before a space.
	//private static final Pattern PUNCTUATION_PATTERN = Pattern.compile("(?<=[\\.|;|,|!|:]) ");
	
	//also incorporate the separator pattern from WordForms!
	//deliberately excluding "\\\\", "\\$"
	//Positive look behind, split on empty space preceded by bracket/paren/brace, preceded by non-empty-space
	private static final Pattern SYMBOL_SEPARATOR_PATTERN = Pattern.compile("-|'|\\+|\\s+|(?:(?:)?<=(?:[^\\s](?:[\\(\\[\\{])))|\\)|\\]\\}");
	
	private static final Pattern BRACKET_SEPARATOR_PATTERN = Pattern.compile("([^\\(\\[\\{]+)[\\(\\[\\{].*");
	
	//contains ParsedExpressions, to be serialized to persistent storage
	private static final List<ParsedExpression> parsedExpressionList = new ArrayList<ParsedExpression>();
	private static final List<String> parsedExpressionStrList = new ArrayList<String>();
	private static final List<String> DefinitionListWithThmStrList = new ArrayList<String>();
	private static final List<String> DefinitionList = new ArrayList<String>();
	
	private static final String parsedExpressionSerialFileStr = "src/thmp/data/parsedExpressionList.dat";
	private static final String parsedExpressionStringFileStr = "src/thmp/data/parsedExpressionList.txt";
	private static final String definitionStrFileStr = "src/thmp/data/parsedExpressionDefinitions.txt";
	//files to serialize theorem words to.
	private static final String allThmWordsSerialFileStr = "src/thmp/data/allThmWordsList.dat";
	private static final String allThmWordsStringFileStr = "src/thmp/data/allThmWordsList.txt";
	
	//serialize the words as well, to bootstrap up after iterations of processing. The math words are going to 
	//stabilize. 
	//This is ordered version of CollectThm.ThmWordsMaps.get_docWordsFreqMapNoAnno().
	private static final List<String> ALL_THM_WORDS_LIST = new ArrayList<String>(TriggerMathThm2.allThmsKeywordIndexDict().keySet());
	
	private static final boolean PARSE_INPUT_VERBOSE = true;
	private static final Pattern SKIP_PATTERN = Pattern.compile("\\\\begin\\{proof\\}.*");
	private static final Pattern END_SKIP_PATTERN = Pattern.compile("\\\\end\\{proof\\}.*");
	
	/**
	 * Combination of theorem String and the list of
	 * assumptions needed to define the variables in theorem.
	 */
	public static class DefinitionListWithThm implements Serializable {
		
		private static final long serialVersionUID = 7178202892278343033L;

		private String thmStr;
		
		//thmStr, with definition strings prepended.
		private String thmWithDefStr;
		
		private List<VariableDefinition> definitionList = new ArrayList<VariableDefinition>();
		
		public DefinitionListWithThm(String thmStr, List<VariableDefinition> definitionList,
				String thmWithDefStr){
			this.thmStr = thmStr;
			this.definitionList = definitionList;
			this.thmWithDefStr = thmWithDefStr;
		}
		
		@Override
		public String toString(){
			//initial capacity is average number of characters.
			StringBuilder sb = new StringBuilder(250);
			sb.append("- definitionList: ").append(definitionList)
				.append("thmWithDefStr: -").append(thmWithDefStr);
			return sb.toString();
		}

		/**
		 * @return the thmWithDefStr
		 */
		public String getThmWithDefStr() {
			return thmWithDefStr;
		}
		
		/**
		 * @return the theorem String.
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
		if(HYP_PATTERN.matcher(inputStr.toLowerCase()).matches()){
			return true;
		}
		return false;
	}
	
	public static void main(String[] args){
		//only parse if sentence is hypothesis, when parsing outside theorems.
		//to build up variableNamesMMap. Should also collect the sentence that 
		//defines a variable, to include inside the theorem for search.
		//Scanner sc = null;
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
			//inputBF = new BufferedReader(new FileReader("src/thmp/data/CommAlg5.txt"));
			//inputBF = new BufferedReader(new FileReader("src/thmp/data/fieldsRawTex.txt"));
			inputBF = new BufferedReader(new FileReader("src/thmp/data/samplePaper1.txt"));
		}catch(FileNotFoundException e){
			e.printStackTrace();
			throw new IllegalStateException("Source file not found!");
		}
		
		try{
			List<DefinitionListWithThm> defThmList = readAndParseThm(inputBF, parseState);
			System.out.println("DefinitionListWithThm list: " + defThmList);
			DefinitionListWithThmStrList.add(defThmList.toString()+ "\n");
			for(DefinitionListWithThm def : defThmList){				
				DefinitionList.add(def.getDefinitionList().toString());
			}
		}catch(IOException e){
			e.printStackTrace();
		}
		
		List<Object> listToSerialize = new ArrayList<Object>();
		listToSerialize.add(parsedExpressionList);
		FileUtils.serializeObjToFile(listToSerialize, parsedExpressionSerialFileStr);
		
		//serialize words used for context vecs
		List<List<String>> wordListToSerializeList = new ArrayList<List<String>>();
		wordListToSerializeList.add(ALL_THM_WORDS_LIST);
		FileUtils.serializeObjToFile(wordListToSerializeList, allThmWordsSerialFileStr);
		
		//write parsedExpressionList to file
		FileUtils.writeToFile(parsedExpressionStrList, parsedExpressionStringFileStr);
		FileUtils.writeToFile(DefinitionList, definitionStrFileStr);
		
		FileUtils.writeToFile(ALL_THM_WORDS_LIST, allThmWordsStringFileStr);
		
		//deserialize objects
		boolean deserialize = false;
		if(deserialize){
			deserializeParsedExpressionsList();
		}
	}
	
	/**
	 * Deserialize objects in parsedExpressionOutputFileStr, so we don't 
	 * need to read and parse through all papers on every server initialization.
	 * Can just read from serialized data.
	 */
	@SuppressWarnings("unchecked")
	private static List<ParsedExpression> deserializeParsedExpressionsList(){
	
		List<ParsedExpression> parsedExpressionsList = null;
		FileInputStream fileInputStream = null;
		ObjectInputStream objectInputStream = null;
		try{
			fileInputStream = new FileInputStream(parsedExpressionSerialFileStr);
			objectInputStream = new ObjectInputStream(fileInputStream);
		}catch(FileNotFoundException e){
			e.printStackTrace();
			throw new IllegalStateException("ParsedExpressionList output file not found!");
		}catch(IOException e){
			e.printStackTrace();
			throw new IllegalStateException("IOException while opening ObjectOutputStream");
		}
		
		try{
			Object o = objectInputStream.readObject();
			parsedExpressionsList = (List<ParsedExpression>)o;
			//System.out.println("object read: " + ((ParsedExpression)((List<?>)o).get(0)).getOriginalThmStr());			
		}catch(IOException e){
			e.printStackTrace();
			throw new IllegalStateException("IOException while reading deserialized data!");
		}catch(ClassNotFoundException e){
			e.printStackTrace();
			throw new IllegalStateException("ClassNotFoundException while writing to file or closing resources");
		}finally{
			try{
				objectInputStream.close();
				fileInputStream.close();
			}catch(IOException e){
				e.printStackTrace();
				throw new IllegalStateException("IOException while closing resources");
			}
		}
		return parsedExpressionsList;
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
	private static List<DefinitionListWithThm> readAndParseThm(BufferedReader srcFileReader, 
			ParseState parseState) throws IOException{
		
		//compiler will inline these, so don't add function calls to stack.
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
			
			if(ThmInput.BEGIN_PATTERN.matcher(line).matches()){
				break;
			}else if(newThmMatcher.matches()){
				//should be a proposition, hypothesis, etc. E.g. don't look through proofs.
				if(ThmInput.THM_TERMS_PATTERN.matcher(newThmMatcher.group(2)).matches()){
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
		
		Matcher matcher = thmStartPattern.matcher(line);
		if (matcher.matches()) {			
			inThm = true;
			parseState.setInThmFlag(true);
		}
		
		while ((line = srcFileReader.readLine()) != null) {
			if (WordForms.getWhiteEmptySpacePattern().matcher(line).matches()){
				continue;
			}
		
			//should skip certain sections, e.g. \begin{proof}
			Matcher skipMatcher = SKIP_PATTERN.matcher(line);
			if(skipMatcher.matches()){
				while ((line = srcFileReader.readLine()) != null){
					if(END_SKIP_PATTERN.matcher(line).matches()){
						
						break;
					}
				}
				continue;
			}
			
			matcher = thmStartPattern.matcher(line);
			if (matcher.matches()) {
				
				// process here, return two versions, one for bag of words, one
				// for display
				// strip \df, \empf. Index followed by % strip, not percent
				// don't strip.
				// replace enumerate and \item with *
				//thmWebDisplayList, and bareThmList should both be null
				String contextStr = ThmInput.removeTexMarkup(contextSB.toString(), null, null);
				
				//scan contextSB for assumptions and definitions
				//and parse the definitions
				detectAndParseHypothesis(contextStr, parseState);	
				
				inThm = true;		
				//this should be set *after* calling detectAndParseHypothesis(), since detectAndParseHypothesis
				//depends on the state.
				parseState.setInThmFlag(true);
				contextSB.setLength(0);
			}
			else if (thmEndPattern.matcher(line).matches()) {
				
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
				String thm = ThmInput.removeTexMarkup(newThmSB.toString(), null, null);
				
				//clear headParseStruct and curParseStruct of parseState, so newThm
				//has its own stand-alone parse tree.
				parseState.setCurParseStruct(null);
				parseState.setHeadParseStruct(null);
				
				//first gather hypotheses in the theorem. <--Note that this will cause the hypothetical
				//sentences to be parsed twice, unless these sentences are marked so they don't get parsed again.
				detectAndParseHypothesis(thm, parseState);
				//if(true) throw new IllegalStateException(parseState.toString());
				//if contained in local map, should be careful about when to append map.
				
				//append to newThmSB additional hypotheses that are applicable to the theorem.				
				DefinitionListWithThm thmDef = appendHypothesesAndParseThm(thm, parseState);
				
				definitionListWithThmList.add(thmDef);
				//System.out.println("___-------++++++++++++++" + thmDef);
				//should parse the theorem.
				//serialize the full parse, i.e. parsedExpression object, along with original input.				
				
				/*if (!WordForms.getWhitespacePattern().matcher(thm).find()) {
					thms.add(thm);
				}*/
				parseState.parseRunCleanUp();
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

		parseState.writeUnknownWordsToFile();
		
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
	private static void detectAndParseHypothesis(String contextStr, ParseState parseState){
		
		//split on punctuations precede a space, but keep the punctuation.
		//String[] contextStrAr = PUNCTUATION_PATTERN.split(contextStr);
		String[] contextStrAr = ThmP1.preprocess(contextStr);
		for(int i = 0; i < contextStrAr.length; i++){
			String sentence = contextStrAr[i];
			if(isHypothesis(sentence)){	
				System.out.println("isHypothesis! " + sentence);
				//if(true) throw new IllegalStateException(sentence);				
				parseState.setCurParseStruct(null);
				parseState.setHeadParseStruct(null);
				ParseRun.parseInput(sentence, parseState, PARSE_INPUT_VERBOSE);
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
	private static DefinitionListWithThm appendHypothesesAndParseThm(String thmStr, ParseState parseState){
		
		//ListMultimap<VariableName, VariableDefinition> variableNamesMMap = parseState.getGlobalVariableNamesMMap();
		//String thmStr = thmSB.toString();
		StringBuilder thmWithDefSB = new StringBuilder();		
		StringBuilder latexExpr = new StringBuilder();
		
		List<VariableDefinition> variableDefinitionList = new ArrayList<VariableDefinition>();
		//varDefSet set to keep track of which VariableDefinition's have been added, so not to 
		//add duplicate ones.
		Set<VariableDefinition> varDefSet = new HashSet<VariableDefinition>();
		
		int thmStrLen = thmStr.length();		
		boolean mathMode = false;
		
		//filter through text and try to pick up definitions.
		for(int i = 0; i < thmStrLen; i++){
			
			char curChar = thmStr.charAt(i);			
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
					//definition strings to thmWithDefSB. Should only append
					//variables that are not defined within the same thm.				
					List<VariableDefinition> varDefList = pickOutVariables(latexExpr.toString(), //variableNamesMMap,
							parseState, varDefSet, thmWithDefSB);
					
					variableDefinitionList.addAll(varDefList);
					latexExpr.setLength(0);
				}			
			}else if(mathMode){
				latexExpr.append(curChar);
			}			
		}

		//Parse the thm first, with the variableNamesMMap already updated to include contexual definitions.
		//should return parsedExpression object, and serialize it. But only pick up definitions that are 
		//not defined locally within this theorem.
		System.out.println("~~~~~~parsing~~~~~~~~~~");		
		ParseRun.parseInput(thmStr, parseState, PARSE_INPUT_VERBOSE);
		System.out.println("~~~~~~Done parsing~~~~~~~");		
				
		System.out.println("Adding " + thmWithDefSB + " to theorem " + thmStr);
		
		thmWithDefSB.append(thmStr);
		DefinitionListWithThm defListWithThm = 
				new DefinitionListWithThm(thmStr, variableDefinitionList, thmWithDefSB.toString());
		
		//create parsedExpression to serialize to persistent storage to be used later
		//for search, etc
		ParsedExpression parsedExpression = new ParsedExpression(thmStr, parseState.getHeadParseStruct(),
						defListWithThm, parseState.getCurThmCombinedContextVec(), parseState.getRelationalContextVec());
		
		parsedExpressionList.add(parsedExpression);
		parsedExpressionStrList.add(parsedExpression.toString());
		//return this to supply to search later
		return defListWithThm;
	}
	
	/**
	 * Picks out variables to be defined, and try to match them with prior definitions.
	 * Picks up variable definitions.
	 * @param latexExpr 
	 * @param thmDefSB StringBuilder that's the original input string appended
	 * to the definition strings.
	 * @param varDefSet set to keep track of which VariableDefinition's have been added, so not to 
	 * add duplicate ones.
	 */
	private static List<VariableDefinition> pickOutVariables(String latexExpr, 
			//ListMultimap<VariableName, VariableDefinition> variableNamesMMap,
			ParseState parseState, Set<VariableDefinition> varDefSet,
			StringBuilder thmDefSB){
		
		//list of definitions needed in this latexExpr
		List<VariableDefinition> varDefList = new ArrayList<VariableDefinition>();
		
		//split the latexExpr with delimiters
		String[] latexExprAr = SYMBOL_SEPARATOR_PATTERN.split(latexExpr);
		//System.out.println("=++++++++========= latexExpr " + latexExpr);
		for(int i = 0; i < latexExprAr.length; i++){
			
			String possibleVar = latexExprAr[i];
			//System.out.println("^^^$^%%% &^^^ possibleVar: "+ possibleVar);
			
			//Get a variableName and check if a variable has been defined.
			VariableName possibleVariableName = ParseState.createVariableName(possibleVar);
			VariableDefinition possibleVarDef = new VariableDefinition(possibleVariableName, null, null);
			
			boolean isLocalVar = parseState.getVariableDefinitionFromName(possibleVarDef);
			//whether the variable definition was defined locally in the theorem, used to determine whether
			//to include originalDefiningSentence.
			
			//System.out.println("^^^ variableNamesMMap: "+ variableNamesMMap);
			//System.out.println("^^^^^^^PossibleVar: " + possibleVar);
			//get the latest definition
			//int possibleVarDefListLen = possibleVarDefList.size();
			//if empty, check to see if bracket pattern, if so, check just the name without the brackets.
			//e.g. x in x(yz)
			if(null == possibleVarDef.getDefiningStruct()){
				Matcher bracketSeparatorMatcher = BRACKET_SEPARATOR_PATTERN.matcher(possibleVar);
				if(bracketSeparatorMatcher.find()){
					possibleVariableName = ParseState.createVariableName(bracketSeparatorMatcher.group(1));
					possibleVarDef.setVariableName(possibleVariableName);
					isLocalVar = parseState.getVariableDefinitionFromName(possibleVarDef);				
				}
			}
			//if some variable found.
			if(!isLocalVar && null != possibleVarDef.getDefiningStruct()){			
				if(!varDefSet.contains(possibleVarDef)){
					varDefSet.add(possibleVarDef);
					varDefList.add(possibleVarDef);
					//System.out.println("latestVarDef.getOriginalDefinitionStr() " + latestVarDef.getOriginalDefinitionStr());
			 		thmDefSB.append(possibleVarDef.getOriginalDefinitionStr()).append(" ");
				}
			}			
		}
		//if(true) throw new IllegalStateException("latexExpr containing var: " + latexExpr + " varDefList " + varDefList);
		return varDefList;
	}
	
}
