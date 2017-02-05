package thmp;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import thmp.ParseState.ParseStateBuilder;
import thmp.ParseState.VariableDefinition;
import thmp.ParseState.VariableName;
import thmp.search.CollectThm;
import thmp.search.Searcher;
import thmp.search.ThmSearch;
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
	
	private static final Logger logger = LogManager.getLogger(DetectHypothesis.class);
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
	private static final List<String> allThmsStrList = new ArrayList<String>();
	private static final List<String> DefinitionList = new ArrayList<String>();
	
	private static final String parsedExpressionSerialFileStr = "src/thmp/data/parsedExpressionList.dat";
	private static final String parsedExpressionStringFileStr = "src/thmp/data/parsedExpressionList.txt";
	private static final String allThmsStringFileStr = "src/thmp/data/allThmsList.txt";
	
	private static final String definitionStrFileStr = "src/thmp/data/parsedExpressionDefinitions.txt";
	//files to serialize theorem words to.
	private static final String allThmWordsMapSerialFileStr = "src/thmp/data/allThmWordsMap.dat";
	private static final String allThmWordsMapStringFileStr = "src/thmp/data/allThmWordsMap.txt";
	private static final String allThmWordsSerialFileStr = "src/thmp/data/allThmWordsList.dat";
	private static final String allThmWordsStringFileStr = "src/thmp/data/allThmWordsList.txt";
	
	private static final String statsFileStr = "src/thmp/data/parseStats.txt";
	
	//serialize the words as well, to bootstrap up after iterations of processing. The math words are going to 
	//stabilize.
	//This is ordered based on word frequencies.
	private static final List<String> ALL_THM_WORDS_LIST;
	private static final Map<String, Integer> ALL_THM_WORDS_FREQ_MAP;
	
	private static final boolean PARSE_INPUT_VERBOSE = true;
	//whether to gather a list of statistics, such as percentage of thms with full parses, or non-null head ParseStruct's.
	private static final boolean GATHER_STATS = true;
	
	//pattern for lines to skip any kind of parsing, even hypothesis-detection.
	//skip examples and bibliographies  
	//Pattern.compile("\\\\begin\\{proof\\}.*|\\\\begin\\{exam.*|\\\\begin\\{thebib.*");
	private static final Pattern SKIP_PATTERN = WordForms.getSKIP_PATTERN();	
	////Pattern.compile("\\\\end\\{proof\\}.*|\\\\end\\{exam.*|\\\\end\\{thebib.*")
	private static final Pattern END_SKIP_PATTERN = WordForms.getEND_SKIP_PATTERN();			
	
	//single lines to skip. Such as comments
	//Pattern.compile("^%.*|\\\\begin\\{bib.*")
	private static final Pattern SINGLE_LINE_SKIP_PATTERN = WordForms.getSINGLE_LINE_SKIP_PATTERN();
	
	private static final Pattern END_DOCUMENT_PATTERN = Pattern.compile("\\\\end\\{document\\}.*");
	private static final Pattern NEW_DOCUMENT_PATTERN = Pattern.compile(".*\\\\documentclass.*");
	
	static{
		Searcher.SearchMetaData.set_gatheringDataBoolToTrue();
		FileUtils.set_dataGenerationMode();
		
		ALL_THM_WORDS_LIST = new ArrayList<String>(CollectThm.ThmWordsMaps.get_contextVecWordsNextTimeMap().keySet());
		ALL_THM_WORDS_FREQ_MAP = CollectThm.ThmWordsMaps.get_contextVecWordsNextTimeMap();
		ThmSearch.TermDocumentMatrix.createTermDocumentMatrixSVD();
	}
	
	/**
	 * Statistics class to record statistics such as the percentage of thms 
	 * that have spanning parses and/or non-null headParseStruct's.
	 */
	public static class Stats{
		//number of thms for which headParseStruct == null
		private int headParseStructNullNum = 0;
		//total number of theorems
		private int totalThmsNum = 0;
		
		public Stats(){
			this.headParseStructNullNum = 0;
			this.totalThmsNum = 0;
		}
		
		public Stats(int numHeadParseStructNull, int numTotalThms){
			this.headParseStructNullNum = numHeadParseStructNull;
			this.totalThmsNum = numTotalThms;
		}
		
		/**
		 * @param headParseStructNullNum the headParseStructNullNum to set
		 */
		public void incrementHeadParseStructNullNum() {
			this.headParseStructNullNum++;
		}

		/**
		 * @param totalThmsNum the totalThmsNum to set
		 */
		public void incrementTotalThmsNum() {
			this.totalThmsNum++;
		}

		/**
		 * @return the headParseStructNullNum
		 */
		public int getHeadParseStructNullNum() {
			return headParseStructNullNum;
		}

		/**
		 * @return the totalThmsNum
		 */
		public int getTotalThmsNum() {
			return totalThmsNum;
		}
		
		/**
		 * @return the totalThmsNum
		 */
		public double getNonNullPercentage() {
			return ((double)headParseStructNullNum)/totalThmsNum;
		}
		
		@Override
		public String toString(){
			StringBuilder sb = new StringBuilder("totalThmsNum: ").append(totalThmsNum);
			sb.append(" Percetage nontrivial parseStructHeads: ").append(getNonNullPercentage());
			return sb.toString();
		}
	}
	
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

		ParseStateBuilder parseStateBuilder = new ParseStateBuilder();		
		ParseState parseState = parseStateBuilder.build();

		BufferedReader inputBF = null;		
		//try to read file path from command line argument first
		//path should be absolute
		int argsLen = args.length;
		boolean inputBFCreatedBool = false;
		//String srcStr = null;
		if(argsLen > 0){
			String srcStr = args[0];
			try{
				inputBF = new BufferedReader(new FileReader(srcStr));
				inputBFCreatedBool = true;
			}catch(FileNotFoundException e){
				inputBFCreatedBool = false;
				System.out.println("Cannot find file specified via command line!");
				//for now:
				throw new IllegalStateException("Cannot find file specified via command line!");
			}
		}
		if(!inputBFCreatedBool){
			try{
				//inputBF = new BufferedReader(new FileReader("src/thmp/data/CommAlg5.txt"));
				//inputBF = new BufferedReader(new FileReader("src/thmp/data/fieldsRawTex.txt"));
				//inputBF = new BufferedReader(new FileReader("src/thmp/data/samplePaper1.txt"));
				inputBF = new BufferedReader(new FileReader("src/thmp/data/Total.txt"));
				//inputBF = new BufferedReader(new FileReader("src/thmp/data/fieldsThms2.txt"));
			}catch(FileNotFoundException e){
				e.printStackTrace();
				throw new IllegalStateException("Source file not found!");
			}
		}
		
		List<DefinitionListWithThm> defThmList = new ArrayList<DefinitionListWithThm>();
		Stats stats = null;
		try{
			stats = readAndParseThm(inputBF, parseState, defThmList);
			//System.out.println("DefinitionListWithThm list: " + defThmList);
			System.out.println("STATS -- percentage of non-trivial ParseStruct heads: " + stats.getNonNullPercentage() 
				+ " out of total " + stats.getTotalThmsNum() + "thms");
			DefinitionListWithThmStrList.add(defThmList.toString()+ "\n");
			for(DefinitionListWithThm def : defThmList){
				DefinitionList.add(def.getDefinitionList().toString());
			}
		}catch(IOException e){
			e.printStackTrace();
			logger.error(e.getStackTrace());
		}catch(Throwable e){
			logger.error(e.getStackTrace());			
			throw e;
		}finally{		
			serializeDataToFile(stats);
			
		}
		
		//deserialize objects
		boolean deserialize = false;
		if(deserialize){
			deserializeParsedExpressionsList();
		}
		
		FileUtils.cleanupJVMSession();
	}

	/**
	 * Serialize collected data to persistent storage.
	 * The serializations done in this method should remain atomic, i.e. do *not* perform 
	 * a subset of steps only, since we rely on the different serialized data to come from
	 * the same source with the same settings.
	 */
	private static void serializeDataToFile(Stats stats) {
		//List<Object> listToSerialize = new ArrayList<Object>();
		//listToSerialize.add(parsedExpressionList);
		FileUtils.serializeObjToFile(parsedExpressionList, parsedExpressionSerialFileStr);
		
		//serialize words used for context vecs
		//List<List<String>> wordListToSerializeList = new ArrayList<List<String>>();
		//wordListToSerializeList.add(ALL_THM_WORDS_LIST);
		//System.out.println("------++++++++-------putting in : ALL_THM_WORDS_LIST.size " + ALL_THM_WORDS_LIST.size());
		FileUtils.serializeObjToFile(ALL_THM_WORDS_LIST, allThmWordsSerialFileStr);
		
		List<Map<String, Integer>> wordMapToSerializeList = new ArrayList<Map<String, Integer>>();
		wordMapToSerializeList.add(ALL_THM_WORDS_FREQ_MAP);
		FileUtils.serializeObjToFile(wordMapToSerializeList, allThmWordsMapSerialFileStr);
		//this list is for human checking the result.
		List<String> wordMapStringList = new ArrayList<String>();
		wordMapStringList.add(ALL_THM_WORDS_FREQ_MAP.toString());
		FileUtils.writeToFile(wordMapStringList, allThmWordsMapStringFileStr);
		
		//write parsedExpressionList to file
		FileUtils.writeToFile(parsedExpressionStrList, parsedExpressionStringFileStr);
		FileUtils.writeToFile(DefinitionList, definitionStrFileStr);
		
		//write just the thms
		FileUtils.writeToFile(allThmsStrList, allThmsStringFileStr);
		//append to stats file!
		FileUtils.appendObjToFile(stats, statsFileStr);
		FileUtils.writeToFile(ALL_THM_WORDS_LIST, allThmWordsStringFileStr);
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
	private static Stats readAndParseThm(BufferedReader srcFileReader, 
			ParseState parseState, List<DefinitionListWithThm> definitionListWithThmList) throws IOException{
		
		//Pattern thmStartPattern = ThmInput.THM_START_PATTERN;
		//Pattern thmEndPattern = ThmInput.THM_END_PATTERN;
		List<String> macrosList = new ArrayList<String>();
		//contextual sentences outside of theorems, to be scanned for
		//definitions, and parse those definitions. Reset between theorems.
		StringBuilder contextSB = new StringBuilder();
		
		Stats stats = new Stats();
		//List<DefinitionListWithThm> definitionListWithThmList = new ArrayList<DefinitionListWithThm>();
		
		String line = extractMacros(srcFileReader, macrosList);
		
		//append list of macros to THM_START_STR and THM_END_STR
		Pattern[] customPatternAr = addMacrosToThmBeginEndPatterns(macrosList);
		
		Pattern thmStartPattern = customPatternAr[0];
		Pattern thmEndPattern = customPatternAr[1];	
		
		StringBuilder newThmSB = new StringBuilder();
		Matcher matcher;
		boolean inThm = false;
		
		if(null != line){
			matcher = thmStartPattern.matcher(line);
			//use find(), not matches(), to look for any matching substring
			if (matcher.find()) {			
				inThm = true;
				parseState.setInThmFlag(true);
			}
		}
		while ((line = srcFileReader.readLine()) != null) {
			if (WordForms.getWhiteEmptySpacePattern().matcher(line).matches()){
				continue;
			}
			
			if(SINGLE_LINE_SKIP_PATTERN.matcher(line).matches()){
				continue;
			}
			//should skip certain sections, e.g. \begin{proof}
			Matcher skipMatcher = SKIP_PATTERN.matcher(line);
			if(skipMatcher.find()){
				while ((line = srcFileReader.readLine()) != null){
					if(END_SKIP_PATTERN.matcher(line).find()){						
						break;
					}
				}
				continue;
			}
			
			boolean appendedToThm = false;
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
				detectAndParseHypothesis(contextStr, parseState, stats);	
				
				inThm = true;
				newThmSB.append(line);
				appendedToThm = true;
				//this should be set *after* calling detectAndParseHypothesis(), since detectAndParseHypothesis
				//depends on the state.
				parseState.setInThmFlag(true);
				contextSB.setLength(0);
			}
			//if and not else if, since \begin{} and \end{} could be on same line.
			if (thmEndPattern.matcher(line).matches()) {
				
				inThm = false;
				
				if(0 == newThmSB.length()){
					continue;
				}
				//System.out.println("newThmSB: " + newThmSB);
				//System.out.println("!---------! line: " + line+" thmEndPattern: " + thmEndPattern);
				
				allThmsStrList.add(newThmSB.toString() + "\n\n");
				//newThmSB.setLength(0);
				processParseHypThm(newThmSB, parseState, stats, definitionListWithThmList);
				continue;
			}else if(END_DOCUMENT_PATTERN.matcher(line).matches()){
				parseState.parseRunGlobalCleanUp();
				//read until a new document, marked by \documentclass..., is encountered.
				//sometimes the \documentclass follows immediately after \end{document},
				//without starting a new line.
				if(!NEW_DOCUMENT_PATTERN.matcher(line).matches()){
					while(null != (line = srcFileReader.readLine())){					
						if(NEW_DOCUMENT_PATTERN.matcher(line).matches()){
							break;
						}					
					}
				}
				//should start with new macro-reading code.
				line = extractMacros(srcFileReader, macrosList);				
				//append list of macros to THM_START_STR and THM_END_STR
				customPatternAr = addMacrosToThmBeginEndPatterns(macrosList);				
				continue;
			}

			if(inThm && !appendedToThm){
				newThmSB.append(" ").append(line);
			}else if (!inThm) {
				//need to parse to gather definitions
				//add to contextSB
				contextSB.append(" ").append(line);
			}
		}

		parseState.writeUnknownWordsToFile();
		
		//Stats stats = new Stats(headParseStructNullNum, totalThmsNum);
		// srcFileReader.close();
		// System.out.println("Inside ThmInput, thmsList " + thms);
		// System.out.println("thmWebDisplayList " + thmWebDisplayList);
		return stats;
	}

	/**
	 * Processes (e.g. remove tex markup) and parses a theorem after it has been read in.
	 * @param newThmSB StringBuilder containing the theorem.
	 * @param parseState
	 * @param stats
	 * @param definitionListWithThmList
	 */
	private static void processParseHypThm(StringBuilder newThmSB, ParseState parseState, Stats stats, 
			List<DefinitionListWithThm> definitionListWithThmList){
		//if(true) throw new IllegalStateException(newThmSB.toString());
		
		// process here, return two versions, one for bag of words, one
		// for display
		// strip \df, \empf. Index followed by % strip, not percent
		// don't strip.
		// replace enumerate and \item with *
		//thmWebDisplayList, and bareThmList should both be null
		String thm = ThmInput.removeTexMarkup(newThmSB.toString(), null, null);
		
		//Must clear headParseStruct and curParseStruct of parseState, so newThm
		//has its own stand-alone parse tree.
		parseState.setCurParseStruct(null);
		parseState.setHeadParseStruct(null);
		
		//first gather hypotheses in the theorem. <--Note that this will cause the hypothetical
		//sentences to be parsed twice, unless these sentences are marked so they don't get parsed again.
		detectAndParseHypothesis(thm, parseState, stats);
		//if(true) throw new IllegalStateException(parseState.toString());
		//if contained in local map, should be careful about when to append map.
		
		//append to newThmSB additional hypotheses that are applicable to the theorem.				
		DefinitionListWithThm thmDef = appendHypothesesAndParseThm(thm, parseState, stats);
		
		definitionListWithThmList.add(thmDef);
		//System.out.println("___-------++++++++++++++" + thmDef);
		//should parse the theorem.
		//serialize the full parse, i.e. parsedExpression object, along with original input.				
		
		/*if (!WordForms.getWhitespacePattern().matcher(thm).find()) {
			thms.add(thm);
		}*/
		//local clean up, after done with a theorem, but still within same document.
		parseState.parseRunLocalCleanUp();
		newThmSB.setLength(0);
	}
	
	/**
	 * Create custom start and end patterns by appending to THM_START_STR and THM_END_STR.
	 * @param macrosList
	 * @return
	 */
	private static Pattern[] addMacrosToThmBeginEndPatterns(List<String> macrosList) {
		//compiler will inline these, so don't add function calls to stack.
		Pattern[] customPatternAr = new Pattern[]{ThmInput.THM_START_PATTERN, ThmInput.THM_END_PATTERN};
		if(!macrosList.isEmpty()){
			StringBuilder startBuilder = new StringBuilder();
			StringBuilder endBuilder = new StringBuilder();
			for(String macro : macrosList){
				//create start and end macros
				//but only if isn't already included in THM_START_PATTERN
				if(ThmInput.THM_START_PATTERN.matcher("\\begin{" + macro).matches()){
					continue;
				}
				startBuilder.append("|.*\\\\begin\\s*\\{").append(macro).append(".*");				
				endBuilder.append("|.*\\\\end\\s*\\{").append(macro).append(".*");
			}
			if(startBuilder.length() > 0){
				customPatternAr[0] = Pattern.compile(ThmInput.THM_START_STR + startBuilder);
				customPatternAr[1] = Pattern.compile(ThmInput.THM_END_STR + endBuilder);	
			}
		}
		//if(true) throw new IllegalStateException("updated macros list: " + Arrays.deepToString(customPatternAr));
		return customPatternAr;
	}

	/**
	 * Read in custom macros, break as soon as \begin{...} encountered, 
	 * in particular \begin{document}. There are no \begin{...} in the preamble
	 * @param srcFileReader
	 * @param macrosList
	 * @throws IOException
	 */
	private static String extractMacros(BufferedReader srcFileReader, List<String> macrosList) throws IOException {
		
		String line = null;		
		while ((line = srcFileReader.readLine()) != null) {
			//should also extract those defined with \def{} patterns.
			Matcher newThmMatcher;	
			
			if(ThmInput.BEGIN_PATTERN.matcher(line).matches()){
				break;
			}else if((newThmMatcher = ThmInput.NEW_THM_PATTERN.matcher(line)).matches()){
				//should be a proposition, hypothesis, etc. E.g. don't look through proofs.
				if(ThmInput.THM_TERMS_PATTERN.matcher(newThmMatcher.group(2)).matches()){
					macrosList.add(newThmMatcher.group(1));	
				}
			}			
		}
		//if(true)throw new IllegalStateException("macros: "+macrosList);
		return line;
	}
	
	/**
	 * detect hypotheses and definitions, and add definitions to parseState.
	 * @param contextSB
	 * @param parseState
	 */
	private static void detectAndParseHypothesis(String contextStr, ParseState parseState, Stats stats){
		
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
				ParseRun.parseInput(sentence, parseState, PARSE_INPUT_VERBOSE, stats);
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
	private static DefinitionListWithThm appendHypothesesAndParseThm(String thmStr, ParseState parseState, Stats stats){
		
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
		ParseRun.parseInput(thmStr, parseState, PARSE_INPUT_VERBOSE, stats);
		System.out.println("~~~~~~Done parsing~~~~~~~");		
				
		System.out.println("Adding " + thmWithDefSB + " to theorem " + thmStr);
		
		thmWithDefSB.append(thmStr);
		DefinitionListWithThm defListWithThm = 
				new DefinitionListWithThm(thmStr, variableDefinitionList, thmWithDefSB.toString());
		
		//relational and context vecs can't be null, since ImmutableList cannot contain null elements
		BigInteger relationalContextVec = parseState.getRelationalContextVec();
		if(null == relationalContextVec){
			//write placeholder
			relationalContextVec = new BigInteger(1, new byte[1]);
		}
		int[] combinedContextVec = parseState.getCurThmCombinedContextVec();
		if(null == combinedContextVec){
			combinedContextVec = ParseState.PLACEHOLDER_CONTEXT_VEC();
		}
		
		//create parsedExpression to serialize to persistent storage to be used later
		//for search, etc
		ParsedExpression parsedExpression = new ParsedExpression(thmStr, parseState.getHeadParseStruct(),
						defListWithThm, combinedContextVec, relationalContextVec);
		
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
