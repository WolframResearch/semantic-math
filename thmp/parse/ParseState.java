package thmp.parse;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;

import thmp.parse.ThmP1.ParsedPair;
import thmp.search.CollectThm;
import thmp.utils.FileUtils;
import thmp.utils.TexToTree;
import thmp.utils.WordForms;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

/**
 * Captures state of the parse, such as recent entities used (recentEnt).
 * To be used to assist ThmP1.parse(). To transfer information between
 * adjacent sentences, to preserve contextual information.
 * 
 * Every time a new attribute or property is added, check if it needs to be reset
 * between parses, if so, reset in local/globalParseCleanUp.
 * @author yihed
 */
public class ParseState {
	
	//placeholder context vector
	private static Map<Integer, Integer> PLACEHOLDER_CONTEXT_VEC = Collections.emptyMap();
			//new int[CollectThm.ThmWordsMaps.get_CONTEXT_VEC_SIZE()];
	/*private static String PLACEHOLDER_CONTEXT_VEC_STR 
		= GenerateContextVector.contextVecIntArrayToString(PLACEHOLDER_CONTEXT_VEC);*/
	//current String being parsed. I.e. the unit
	//of the input that's being tokenized, so 
	//delimiter-separated.
	/* *NOTE* Every time a stateful field is added, be sure to clean it up either locally or globally*/
	private String currentInputStr;
	private Struct recentEnt;	
	//recent assert
	private Struct recentAssert;
	//whether the last parse spans or not
	private boolean recentParseSpanning;	
	//tokenized input list
	private List<Struct> tokenList;
	//tokenList of previous parse segment, i.e. punctuation
	//-delimited parts of the original sentence.
	private List<Struct> prevTokenList;	
	//parseStruct, for layering built WLCommands
	private ParseStruct headParseStruct;	
	//list of ParseStruct's, one for each parse tree.
	private List<ParseStruct> headParseStructList;
	//current parse struct, somewhere down the tree from headParseStruct
	private ParseStruct curParseStruct;	
	//context vector that takes into account structure of parse tree, i.e.
	//the relations between different Structs.
	private BigInteger relationalVec;
	//number of non-tex tokens, used during search, so can discard theorems that
	//are almost all latex expressions.
	private int numNonTexTokens;
	private ParseErrorCode parseErrorCode = ParseErrorCode.NO_ERROR;
	//input with tex replaced by e.g. "X", to be used for making syntaxnet queries
	private String currentInputSansTex;
	//struct array struct token index info, for making syntaxnet queries.
	private Struct[] noTexTokenStructAr;
	//cache the most recent SyntaxnetQuery, since its construction is costly.
	private SyntaxnetQuery syntaxnetQuery;
	private boolean curParseExcessiveLatex;
	private boolean writeUnknownWordsToFileBool;
	
	//punctuation at end of current parse segment, segments are separated
	//by punctuations.
	private String finalPunctuation;	

	//pattern for matching latex expressions wrapped in dollars
	private static final Pattern LATEX_DOLLARS_PATTERN = Pattern.compile("^\\$([^$]+)\\$$");
	
	//contains both text and latex e.g. "winding number $W_{ii'} (y)$"
	private static final Pattern TEXT_LATEX_PATTERN = Pattern.compile("^[^$]+\\$([^$]+)\\$.*$");
	
	//contains both text and latex e.g. "winding number $W_{ii'} (y)$"
	//private static final Pattern COLON_PATTERN = Pattern.compile("([^:=\\s]+)\\s*[:=].*|(?([.]+)(\\subset)).*");
	//In latex expressions such as = or : , the variable before : or = should be recorded as well.
	private static final Pattern COLON_EQUAL_PATTERN = Pattern.compile("([^:=\\s]+)\\s*[:=].*");
	//same here, if \\subset is part of Tex expression.
	//need to encode all relation operators: https://oeis.org/wiki/List_of_LaTeX_mathematical_symbols
	private static final Pattern SUBSET_PATTERN = Pattern.compile("(.+?)\\s*(?:(?:\\\\not)*\\\\subset).+");
	
	//first group captures name, second the parenthesis/bracket/brace. E.g. "f[x]"
	private static final Pattern VARIABLE_NAME_PATTERN = Pattern.compile("([^\\[\\{\\(]+)([\\[\\{\\(]).*");
	
	//Only make first part disposable if it's within certain length; e.g. K-algebra
	private static final Pattern VARIABLE_NAME_DASH_PATTERN = Pattern.compile("([^-]+){1,2}[-].*");
	
	private static final boolean DEBUG = FileUtils.isOSX() ? InitParseWithResources.isDEBUG() : false;

	/* 
	 * Multimap of what each symbol stands for in the text so far.
	 * Make clear it's ListMultimap, since symbol order matters.
	 * Keys are variable Strings, and values   that contain
	 * Structs, and the input sentence that defines it.
	 * When storing a definition such as "$f: X\to Y$", also store variable
	 * name before the colon, i.e. "f". 
	 */
	private ListMultimap<VariableName, VariableDefinition> globalVariableNamesMMap;
	
	/* Map to use when in thms/lemmas/etc, local variable definition map is used 
	 * first to match with variables when in thm, since this contains the local 
	 * definitions.
	 */
	public ListMultimap<VariableName, VariableDefinition> localVariableNamesMMap; //public for debugging June 29
	
	//parsedExpr to record parsed pairs during parsing. Eliminate copy in ThmP1.java!
	//<--This should be superceded by ParseStruct's, Jan 2017.
	private List<ParsedPair> parsedExpr = new ArrayList<ParsedPair>();	
	//list of context vectors, each element corresponds to a part of a thm.
	List<int[]> thmContextVecList = new ArrayList<int[]>();
	List<Map<Integer, Integer>> thmContextVecMapList = new ArrayList<Map<Integer, Integer>>();
	//flag to denote whether currently in theorem/lemma/etc or not.
	private boolean inThmFlag;
	
	//record pos postulated during parse.
	private static HashMultimap<String, String> extrapolatedPosMMap = HashMultimap.create(); 		
	private static final Logger logger = LogManager.getLogger(ParseState.class);	
	private static final String UNKNOWN_WORDS_FILE_NAME_STR = "src/thmp/data/unknownWords2.txt";
	
	public static enum ParseErrorCode{
		NO_ERROR, PARSE_ERROR;
	}
	
	/**
	 * Wrapper class around variable string name (Head), also records
	 * the type, e.g. head is "f" and type is BRACKET for the variable "f[x]".
	 */
	public static class VariableName implements Serializable{
		
		private static final long serialVersionUID = 1L;
		//name of variable.
		private String nameStr;
		private VariableNameType variableNameType;
		
		/**
		 * Enum to represent different types of variables. E.g.
		 * $f(x)$, f is attached to parentheses, so is of type PAREN.
		 */
		static enum VariableNameType{
			PAREN("("), BRACKET("["), BRACE("{"), DASH("-"), NONE("");
			
			String stringForm;
			
			VariableNameType(String form){
				this.stringForm = form;
			}
			
			/**
			 * Whether of type parenthesis, bracket, or brace.
			 * @return
			 */
			boolean isParenBracketBrace(){
				return this == PAREN || this == BRACKET || this == BRACE;
			}
			
			static VariableNameType getVariableNameTypeFromString(String typeStr){
				//switch should be as efficient as keeping an internal map,
				//since this switch is dense.
				switch(typeStr){
				case "(":
					return PAREN;
				case "[":
					return BRACKET;
				case "{":
					return BRACE;
				case "-":
					//"A-module"
					return DASH;
				default:
					return NONE;
				}
			}
		}
		
		@Override
		public String toString(){
			return (new StringBuilder(this.nameStr)).append(": ")
					.append(this.variableNameType).toString();
		}
		
		public VariableName(String head, VariableNameType type){
			this.nameStr = head;
			this.variableNameType = type;
		}

		public String nameStr(){
			return this.nameStr;
		}
		
		public VariableNameType variableType(){
			return this.variableNameType;
		}
		
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((nameStr == null) ? 0 : nameStr.hashCode());
			result = prime * result + ((variableNameType == null) ? 0 : variableNameType.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object other){
			
			if(this == other){
				return true;
			}
			
			if(!(other instanceof VariableName)){
				return false;
			}
			
			VariableName otherName = (VariableName)other;
			if(otherName.nameStr() == null && null != this.nameStr
					|| otherName.nameStr() != null && null == this.nameStr){
				return false;
			}
			
			if(otherName.variableNameType != this.variableNameType
					|| !otherName.nameStr.equals(this.nameStr)){
				return false;
			}
			return true;
		}		
	}
	
	public static Map<Integer, Integer> PLACEHOLDER_CONTEXT_VEC(){
		return PLACEHOLDER_CONTEXT_VEC;
	}

	/*public static String PLACEHOLDER_CONTEXT_VEC_String(){
		return PLACEHOLDER_CONTEXT_VEC_STR;
	}*/

	/**
	 * Contain information that define variables, whose names are stored in 
	 * variableNamesMMap.
	 */
	public static class VariableDefinition implements Serializable {

		private static final long serialVersionUID = 8599803973034802106L;

		//the Struct defining the variable.
		//E.g. in "$F$ is a field", the Struct for "field"
		//is the defining Struct.
		private transient Struct definingStruct;
		
		private transient VariableName variableName;
		private transient String originalDefinitionSentence;
		
		/**
		 * originalDefinitionSentence could be null at construction.
		 * @return the originalDefinitionSentence
		 */
		public String getOriginalDefinitionSentence() {
			return originalDefinitionSentence;
		}

		/** 
		 * @param originalDefinitionSentence the originalDefinitionSentence to set
		 */
		public void setOriginalDefinitionSentence(String originalDefinitionSentence) {
			this.originalDefinitionSentence = originalDefinitionSentence;
		}

		/**
		 * @param definingStruct the definingStruct to set
		 */
		public void setDefiningStruct(Struct definingStruct) {
			this.definingStruct = definingStruct;
			//System.out.println(Arrays.toString(Thread.currentThread().getStackTrace()));
			//System.out.println("SETTING definingStruct: " + definingStruct);
		}

		/**
		 * @param variableName the variableName to set
		 */
		public void setVariableName(VariableName variableName) {
			this.variableName = variableName;
		}

		public VariableDefinition(VariableName variableName, Struct definingStruct, String originalDefinitionStr){
			//System.out.println(Arrays.toString(Thread.currentThread().getStackTrace()));
			//System.out.println("definingStruct: " + definingStruct);
			this.variableName = variableName;
			this.definingStruct = definingStruct;
			this.originalDefinitionSentence = originalDefinitionStr;
		}

		@Override
		public String toString(){
			if(null != definingStruct){				
				return definingStruct.toString();				
			}else{
				/*Case when de-serializing, since definingStruct is transient*/
				return "Missing[definingStruct]";
			}
		}
		
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((definingStruct.nameStr() == null) ? 0 : definingStruct.nameStr().hashCode());
			result = prime * result + ((variableName == null) ? 0 : variableName.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object other){
			
			if(this == other){
				return true;
			}			
			if(!(other instanceof VariableDefinition)){
				return false;
			}
			VariableDefinition otherDef = (VariableDefinition)other;
			
			if(!otherDef.definingStruct.nameStr().equals(this.definingStruct.nameStr())){
				return false;
			}
			
			if(null == otherDef.variableName 
					|| !otherDef.variableName.equals(this.variableName)){
				return false;
			}
			return true;
		}

		/**
		 * @return the variableName
		 */
		public VariableName getVariableName() {
			return variableName;
		}
		
		/**
		 * This is the Struct defining the variable.
		 * E.g. in "$F$ is a field", the Struct for "field"
		 * is the defining Struct. Defining Struct does not need to 
		 * of type StructH, e.g. could be a symb.
		 * Note that definingStruct could be null at construction.
		 * @return the definingStruct
		 */
		public Struct getDefiningStruct() {
			return definingStruct;
		}

	}
	
	//waiting WLCommandWrapper map on deck, waiting to be added. Necessary 
	//because don't know if need to add to next logic layer, or to current
	//layer, until the entire tree is read.
	//private Multimap<ParseStructType, WLCommandWrapper> waitingWrapperMMap;	
	private ParseState(ParseStateBuilder builder){
		this.globalVariableNamesMMap = ArrayListMultimap.create();
		this.localVariableNamesMMap = ArrayListMultimap.create();
		this.writeUnknownWordsToFileBool = builder.writeUnknownWordsToFile;		
		//this.headParseStructList = new ArrayList<ParseStruct>();
	}
	
	/**
	 * Whether to write unknown words to file. Also determines
	 * if need to collect unknown words or not.
	 * @return
	 */
	public boolean writeUnknownWordsToFileBool(){
		return this.writeUnknownWordsToFileBool;
	}
	
	/**
	 * Write previously unknown words and their postulated pos to file.
	 * Append to file, rather than overwrite.
	 */
	public void writeUnknownWordsToFile(){
		
		if(extrapolatedPosMMap.isEmpty()) return;
		
		//first format the output so it can just be pasted into lexicon.
		StringBuilder sb = new StringBuilder(1000);
		for(Map.Entry<String, String> entry : extrapolatedPosMMap.entries()){
			sb.append("\n").append(entry.getKey()).append(" ").append(entry.getValue());
		}
		
		boolean appendBool = true;
		try(FileWriter fw = new FileWriter(UNKNOWN_WORDS_FILE_NAME_STR, appendBool);
			    BufferedWriter bw = new BufferedWriter(fw);
			    PrintWriter outPrintWriter = new PrintWriter(bw))
			{
				outPrintWriter.println(sb);				
			} catch (IOException e) {
			   logger.error("IOException while writing to unknown words file!");			   
			}		
	}
	
	/**
	 * Add a postulated pos from posTagger to map, to be written to file later.
	 * @param word
	 * @param pos
	 */
	public void addUnknownWordPosToMap(String word, String pos){
		//extrapolatedPosMMap
		extrapolatedPosMMap.put(word, pos);		
	}
	
	/**
	 * @return the recentParseSpanning
	 */
	public boolean isRecentParseSpanning() {
		return recentParseSpanning;
	}

	/**
	 * @param recentParseSpanning the recentParseSpanning to set
	 */
	public void setRecentParseSpanning(boolean recentParseSpanning) {
		this.recentParseSpanning = recentParseSpanning;
	}

	/**
	 * @return the relationalContextVec
	 */
	public BigInteger getRelationalContextVec() {
		return relationalVec;
	}

	/**
	 * @param relationalContextVec the relationalContextVec to set.
	 * Don't like this getting/setting vectors, bug-prone, should just have 
	 * an atomic structure that can't leak across parses.
	 */
	public void setRelationalContextVec(BigInteger relationalContextVec) {
		this.relationalVec = relationalContextVec;
	}

	/**
	 * Combines list of relational vecs by logical "or" and sets the result as 
	 * current relationalVec. This is used when ThmP1.preprocess() produces several
	 * components for one theorem, but we only want one relational vec for the theorem. 
	 * @param relationalContextVecList the relationalContextVec to combine and set.
	 */
	public void setRelationalContextVec(List<BigInteger> relationalContextVecList) {
		//combine the relational vecs
		BigInteger completeRelationalVec = new BigInteger(1, new byte[1]);
		for(BigInteger bi : relationalContextVecList){
			if(null == bi) continue;
			completeRelationalVec = completeRelationalVec.or(bi);
		}
		this.relationalVec = completeRelationalVec;
	}
	
	/**
	 * Combines the context vec for each component into a single thm context vec.
	 * Computes the combined vector each time. Caller should only call once and store
	 * result.
	 * @return the relationalContextVec
	 */
	public int[] getCurThmCombinedContextVec() {		
		return GenerateContextVector.combineContextVectors(thmContextVecList);
	}
	
	public Map<Integer, Integer> getCurThmCombinedContextVecMap() {		
		return GenerateContextVector.combineContextVectorMaps(thmContextVecMapList);
	}
	
	/**
	 * Adds a context vec to the parse of current thm, since each thm likely consists
	 * of several parts.
	 * @param contextVec
	 */
	public void addContextVecMapToCurThmParse(Map<Integer, Integer> map){
		this.thmContextVecMapList.add(map);
	}
	
	/**
	 * Adds a context vec to the parse of current thm, since each thm likely consists
	 * of several parts.
	 * @param contextVec
	 */
	public void addContextVecToCurThmParse(int[] contextVec) {
		this.thmContextVecList.add(contextVec);
	}
	/**
	 * Build ParseState using builder, to avoid occurrences of half-baked states,
	 * and to avoid .
	 */
	public static class ParseStateBuilder{
		
		private boolean writeUnknownWordsToFile;
		
		//fill in something...
		public ParseStateBuilder(){			
		}
		
		public void setWriteUnknownWordsToFile(boolean bool){
			this.writeUnknownWordsToFile = bool;
		}		
		
		public ParseState build(){
			return new ParseState(this);
		}
	}

	/**
	 * Map of what each symbol stands for in the text so far.
	 * Make clear it's ListMultimap, since symbol order matters.
	 * Clears the map.
	 * @return
	 */
	public ImmutableListMultimap<VariableName, VariableDefinition> getAndClearGlobalVariableNamesMMap(){
		//ListMultimap<String, VariableDefinition> tempMap = ArrayListMultimap.create(this.variableNamesMMap);
		ImmutableListMultimap<VariableName, VariableDefinition> tempMap = ImmutableListMultimap.copyOf(this.globalVariableNamesMMap);
		this.globalVariableNamesMMap = ArrayListMultimap.create();
		return tempMap;
	}
	
	/**
	 * Map of what each symbol stands for in the text so far.
	 * Make clear it's ListMultimap, since symbol order matters.
	 * Returns immutable map, so the original map stays protected and 
	 * can only be modified through the exposed modification method.
	 * @return
	 */
	public ImmutableListMultimap<VariableName, VariableDefinition> getGlobalVariableNamesMMap(){
		//defensively copy.
		//ListMultimap<String, VariableDefinition> tempMap = ArrayListMultimap.create(this.variableNamesMMap);
		ImmutableListMultimap<VariableName, VariableDefinition> tempMap = ImmutableListMultimap.copyOf(this.globalVariableNamesMMap);
		return tempMap;
	}
	
	/**
	 * First try local variable map, then global variable map, to find definition for variableName.
	 * varName should not include $$ for faster speed.
	 * @param variableDefList List of variable definitions to be filled. Should be empty when coming in.
	 * @return Whether definition was extracted locally from the same theorem, as opposed to
	 * 	from the context.
	 */
	public boolean getVariableDefinitionListFromName(VariableName varName, 
			List<VariableDefinition> variableDefList){
		
		boolean isLocalVar = false;
		//List<VariableDefinition> varDefList = new ArrayList<VariableDefinition>();		
		//localVariableNamesMMap can be empty, but should not be null. When outside theorem, inThmFlag is
		//false, so should not be getting theorem-local variables outside of theorems.
		if(inThmFlag){
			variableDefList.addAll(this.localVariableNamesMMap.get(varName));			
		}
		
		if(variableDefList.isEmpty()){
			variableDefList.addAll(this.globalVariableNamesMMap.get(varName));
		}else if(inThmFlag){
			isLocalVar = true;
		}
		//System.out.println("ParseState - " +varName + "  " + this.globalVariableNamesMMap + "  "+this.globalVariableNamesMMap.containsKey(varName));
		return isLocalVar;
	}
	
	/**
	 * Get the latest context-appropriate definition given a VariableName.
	 * First try local variable map, then global variable map, to find definition for variableName.
	 * @param variableDefinition a VariableDefinition instance with the desired varName,
	 * the definingStruct and defining sentence of which will be filled in.
	 * @return
	 */
	public boolean getVariableDefinitionFromName(VariableDefinition variableDefinition){
		
		VariableName varName = variableDefinition.getVariableName();		
		List<VariableDefinition> variableDefList = new ArrayList<VariableDefinition>();
		boolean isLocalVar = getVariableDefinitionListFromName(varName, variableDefList);
		if(DEBUG) System.out.println("ParseState - getVariableDefinitionListFromName " + varName + "  " + variableDefList);
		
		int defListSize = variableDefList.size();		
		//get last-added one.
		if(0 < defListSize){
			VariableDefinition retrievedVarDef = variableDefList.get(defListSize-1);
			variableDefinition.setDefiningStruct(retrievedVarDef.getDefiningStruct());
			variableDefinition.setOriginalDefinitionSentence(retrievedVarDef.getOriginalDefinitionSentence());
		}		
		return isLocalVar;	
	}
	
	/**
	 * Adds to either local or global var map depending on the inThm flag.
	 * May find multiple names, but all are defined with the same Struct
	 * supplied in input.
	 * @param name Name of entStruct. Usually small snippets of tex, not e.g.
	 * elaborate equations.
	 * @param entStruct Entity to be added with name entStruct.
	 */
	public void addLocalVariableStructPair(String name, Struct entStruct){
		//if(true)throw new RuntimeException(name + " " + entStruct);
		if(WordForms.getWhiteEmptySpacePattern().matcher(name).matches()) return;
		
		Matcher latexContentMatcher = LATEX_DOLLARS_PATTERN.matcher(name);
		Matcher textLatexMatcher;
		//System.out.println("adding name: " +name +Arrays.toString(Thread.currentThread().getStackTrace()));
		ListMultimap<VariableName, VariableDefinition> variableMMapToAddTo = inThmFlag 
				? localVariableNamesMMap : globalVariableNamesMMap;
		boolean varAdded = false;
		if(latexContentMatcher.find()){
			name = latexContentMatcher.group(1);
			name = WordForms.stripSurroundingWhiteSpace(name);
			//if colon/equal sign present, record
			//the expression before colon or equal. E.g. "f(x) = x", as well 
			//as the entire expression
			Matcher colonMatcher = COLON_EQUAL_PATTERN.matcher(name);
			Matcher subsetMatcher = SUBSET_PATTERN.matcher(name);
			//make this more systematic!
			String latexName = colonMatcher.find() ? colonMatcher.group(1) : (subsetMatcher.find() ? subsetMatcher.group(1) : "");
			//findTexName(name);
			if(!"".equals(latexName)){
				//String latexName = colonMatcher.group(1);
				//define a VariableName of the right type. 
				VariableName latexVariableName = createVariableName(latexName);
				VariableDefinition latexDef = new VariableDefinition(latexVariableName, entStruct, this.currentInputStr);
				//must check entry, not just key 
				if(!variableMMapToAddTo.containsEntry(latexVariableName, latexDef)){				
					if(DEBUG) System.out.println("....********latexVariableName: " + latexVariableName);
					variableMMapToAddTo.put(latexVariableName, latexDef);
					
					//if variableNameType is paren/brace/bracket, also include name without 
					//the paren/brace/bracket in the map, e.g. "$f(x) = x$", include "f", so can pick up "$f$ is smooth".
					//nest inside so not to check for containment again.
					if(latexVariableName.variableType().isParenBracketBrace()){
						latexVariableName = new VariableName(latexVariableName.nameStr, VariableName.VariableNameType.NONE);
						variableMMapToAddTo.put(latexVariableName, latexDef);						
					}
					varAdded = true;
				}				
			}
		}//if name contains text and latex, e.g. "winding number $W_{ii'} (y)$"
		//create a separate entry with just the latex part.
		else if( (textLatexMatcher = TEXT_LATEX_PATTERN.matcher(name)).find() ){
			
			String latexName = textLatexMatcher.group(1);
			VariableName latexVariableName = createVariableName(latexName);
			VariableDefinition latexDef = new VariableDefinition(latexVariableName, entStruct, this.currentInputStr);
			
			if(!variableMMapToAddTo.containsEntry(latexVariableName, latexDef)){				
				variableMMapToAddTo.put(latexVariableName, latexDef);
			}
			varAdded = true;
		}
		if(!varAdded){
			//get the name before the first special character, could be \\, (, etc.
			//so don't get all of "let $p > 0$ be an integer"
			int i = 0;
			String iCharStr = "";
			int nameLen = name.length();			
			while(i < nameLen && iCharStr.equals(" ")){
				iCharStr = String.valueOf(name.charAt(i++));
			}
			StringBuilder varSB = new StringBuilder();
			while(i < nameLen && (!WordForms.SPECIAL_CHARS_PATTERN.matcher((iCharStr=String.valueOf(name.charAt(i++)))).matches())){
				varSB.append(iCharStr);
			}
			///if(true ) System.out.println("varSB.toString() "+varSB.toString());
			name = varSB.length() == 0 ? name : varSB.toString();
			// e.g. $\\alpha$
			if(varSB.length() == 0 && WordForms.getTexCommandBeginPattern().matcher(iCharStr).matches()){
				StringBuilder nextCommandSB = new StringBuilder(10);
				while(i < nameLen && (!WordForms.getTexCommandEndPattern().matcher((iCharStr=String.valueOf(name.charAt(i)))).matches()
						//|| iCharStr.equals(" ")
						)){
					nextCommandSB.append(iCharStr);
					i++;
				}
				/*while(++i < nameLen && iCharStr.equals(" ")){
					iCharStr = String.valueOf(name.charAt(i));
				}*/
				//if(true) System.out.println("TexToTree nextCommandSB - "+nextCommandSB.toString());
				String nextCommand = nextCommandSB.toString();
				//look for Greek alphabets, which could be variables
				if(WordForms.isGreekAlpha(nextCommand)){
					name = nextCommand;
				}else if(nextCommand.equals("mathbb")){
					//e.g. \mathbb{ }
					name = nextCommandSB.append(getNextParenthesizedToken(name, i)).toString();	
				}
			}			
			//include the entire string
			VariableName variableName = createVariableName(name);
			//should check if contains entry.
			VariableDefinition def = new VariableDefinition(variableName, entStruct, this.currentInputStr);	
			if(!variableMMapToAddTo.containsEntry(variableName, def)){
				variableMMapToAddTo.put(variableName, def);	
			}	
		}
	}
	/**
	 * For fishing out token contained in parenthses, e.g. \\mathbb{Z}
	 * @param name
	 * @param i
	 * @return
	 */
	public static String getNextParenthesizedToken(String name, int i){
		StringBuilder sb = new StringBuilder();
		int nameLen = name.length();
		String curStr = String.valueOf(name.charAt(0));
		//while(i < nameLen && !TexToTree.DESCEND_STRING().contains(String.valueOf(name.charAt(i++))));
		
		while(i < nameLen && !TexToTree.ASCEND_STRING().contains((curStr=String.valueOf(name.charAt(i))))){
			sb.append(curStr);
			i++;
		}	
		if(i < nameLen //&& TexToTree.ASCEND_STRING().contains((curStr=String.valueOf(name.charAt(i))))
				){
			sb.append(curStr);
		}
		//System.out.println("sb  "+sb);
		return sb.toString();
	}
	/**
	 * Creates a VariableName instance of the appropriate type,
	 * based on name. 
	 * @param name Should not contain $ $ around it.
	 * @return
	 */
	public static VariableName createVariableName(String name){
		Matcher spaceM;
		if((spaceM=WordForms.SPACES_AROUND_TEXT_PATTERN().matcher(name)).matches()){
			name = spaceM.group(1);
		}
		VariableName.VariableNameType variableNameType = VariableName.VariableNameType.NONE;
		//figure out the VariableNameType
		//e.g. Brace for f(x), bracket for f[x]
		Matcher parenMatcher = VARIABLE_NAME_PATTERN.matcher(name);
		Matcher dashMatcher;
		
		if(parenMatcher.matches()){		
			//E.g. f(\alpha) = \alpha^2 should not include \alpha as variable name.
			String nameOutsideParen = parenMatcher.group(1);
			if(!"mathbb".equals(nameOutsideParen)){
				name = parenMatcher.group(1);
				variableNameType = VariableName.VariableNameType
						.getVariableNameTypeFromString(parenMatcher.group(2));				
			}
			//throw new IllegalStateException(variableNameType + " " + name);
		}
		else if((dashMatcher = VARIABLE_NAME_DASH_PATTERN.matcher(name)).matches()){	
			//e.g. K-algebra 
			name = dashMatcher.group(1);
			variableNameType = VariableName.VariableNameType
					.getVariableNameTypeFromString("-");
		}		
		return new VariableName(name, variableNameType);
	}
	
	/**
	 * @param name Name of entStruct.
	 * @return Return most recently-added Struct with this name.
	 */
	public Struct getVariableDefinition(String name){	
		
		Matcher matcher;
		if((matcher = LATEX_DOLLARS_PATTERN.matcher(name)).matches()){
			name = matcher.group(1);
		}
		
		VariableName variableName = createVariableName(name);
		
		VariableDefinition variableDef = new VariableDefinition(variableName, null, null);
		getVariableDefinitionFromName(variableDef);
		
		if(null != variableDef.getDefiningStruct()){
			return variableDef.definingStruct;
		}		
		return null;		
	}
	
	/**
	 * Get list of VariableDefinition's with specified name.
	 * @param name
	 * @return
	 */
	public List<VariableDefinition> getNamedStructList(String name){		
		
		VariableName variableName = createVariableName(name);
		return this.globalVariableNamesMMap.get(variableName);			
	}
	
	/**
	 * Logs various objects to file, e.g. variableNamesMMap.
	 */
	public void logState(){
		//remove this on PRD
		logger.info(this.globalVariableNamesMMap);
	}
	
	/**
	 * punctuation at end of current parse segment, segments are separated
	 * by punctuations.
	 * @return the punctuation
	 */
	public String getAndClearCurPunctuation() {
		String punctuationCopy = finalPunctuation;
		this.finalPunctuation = null;
		return punctuationCopy;
	}
	
	/**
	 * @return the currentInputStr
	 */
	public String getCurrentInputStr() {
		return this.currentInputStr;
	}

	/**
	 * @param currentInputStr the currentInputStr to set
	 */
	public void setCurrentInputStr(String currentInputStr) {
		this.currentInputStr = currentInputStr;
	}
	
	/**
	 * @param punctuation the punctuation to set
	 */
	public void setPunctuation(String punctuation) {
		this.finalPunctuation = punctuation;
	}
	
	/*public void addParseStructWrapperPair(ParseStructType type, WLCommandWrapper wrapper){
		this.waitingWrapperMMap.put(type, wrapper);
	}*/
	
	/*public Multimap<ParseStructType, WLCommandWrapper> retrieveAndClearWrapperMMap(){
		Multimap<ParseStructType, WLCommandWrapper> mapCopy = ArrayListMultimap.create(this.waitingWrapperMMap);
		this.waitingWrapperMMap = ArrayListMultimap.create();
		return mapCopy;		
	}*/
	
	/**
	 * @return the curParseStruct
	 */
	public ParseStruct getCurParseStruct() {
		return curParseStruct;
	}

	/**
	 * @param curParseStruct the curParseStruct to set
	 */
	public void setCurParseStruct(ParseStruct curParseStruct) {
		//System.out.println(Arrays.toString(Thread.currentThread().getStackTrace()));
		this.curParseStruct = curParseStruct;
		if(null == headParseStruct && null != curParseStruct){
			synchronized(ParseState.class){
				if(null == headParseStruct){
					this.headParseStruct = curParseStruct;
				}
			}
		}
	}

	public void setCurrentInputSansTex(String inputNoTex){
		this.currentInputSansTex = inputNoTex;
	}
	
	/**
	 * Input with tex replaced by e.g. "X", to be used for making syntaxnet queries.
	 * @return
	 */
	public String currentInputSansTex(){
		return this.currentInputSansTex;
	}
	
	public void setNoTexTokenStructAr(Struct[] noTexTokenStructAr){
		this.noTexTokenStructAr = noTexTokenStructAr;
	}
	
	/**
	 * Input with tex replaced by e.g. "X", to be used for making syntaxnet queries.
	 * @return
	 */
	public Struct[] noTexTokenStructAr(){
		return this.noTexTokenStructAr;
	}
	
	/**
	 * @return the tokenList
	 */
	public List<Struct> getTokenList() {
		return this.tokenList;
	}
	
	/**
	 * @param tokenList the tokenList to set
	 */
	public void setTokenList(List<Struct> tokenList_) {
		this.prevTokenList = this.tokenList;
		this.tokenList = tokenList_;
	}
	
	/**
	 * Get previous tokenlist, used for conditional parses,
	 * i.e. parse if current sentence does not produce a spanning
	 * parse, or does not satisfy any WLCommand, and that the prior 
	 * sentence triggered a particular command, identified by the 
	 * trigger word, e.g. "define", "suppose".
	 * E.g. "Let $F$ be a field, and $R$ a ring."
	 */
	public List<Struct> getPrevTokenList() {
		return this.prevTokenList;
	}

	/**
	 * @return 
	 */
	public ParseStruct getHeadParseStruct() {
		return headParseStruct;
	}
	
	/**
	 * @return 
	 */
	public List<ParseStruct> getHeadParseStructList() {
		return this.headParseStructList;
	}	
	
	/**
	 * @return the tokenList
	 */
	public void setHeadParseStruct(ParseStruct parseStruct) {
		//System.out.println(Arrays.toString(Thread.currentThread().getStackTrace()));
		this.headParseStruct = parseStruct;
	}
	
	/**
	 * @return the recentEnt
	 */
	public Struct getRecentEnt() {
		return recentEnt;
	}
	
	/**
	 * @param recentEnt the recentEnt to set
	 */
	public void setRecentEnt(Struct recentEnt) {
		this.recentEnt = recentEnt;
	}
	
	/**
	 * @return the recentAssert
	 */
	public Struct getRecentAssert() {
		return recentAssert;
	}
	
	/**
	 * @param recentAssert the recentAssert to set
	 */
	public void setRecentAssert(Struct recentAssert) {
		this.recentAssert = recentAssert;
	}
	
	/**
	 * Sets the inThm flag to the given boolean.
	 */
	public void setInThmFlag(boolean inThm){
		this.inThmFlag = inThm;
	}
	
	public SyntaxnetQuery getRecentSyntaxnetQuery(){
		return this.syntaxnetQuery;
	}
	
	/**
	 * Cache the most recent SyntaxnetQuery, since its construction is costly.
	 * @param syntaxnetQuery_
	 */
	public void setSyntaxnetQuery(SyntaxnetQuery syntaxnetQuery_){
		this.syntaxnetQuery = syntaxnetQuery_;
	}
	
	/**
	 * Clear variables, reset state, headParseStruct etc, to not contaminate
	 * variable space for next parse. One parse run is defined
	 * to be the unit given to the preprocessor in Thmp1.java. 
	 * So can contain multiple sentences. E.g. an entire theorem.
	 */
	public void parseRunLocalCleanUp(){
		this.numNonTexTokens = 0;
		this.setCurParseStruct(null);
		this.setHeadParseStruct(null);
		this.parseErrorCode = ParseErrorCode.NO_ERROR;
		//reset list of context vectors
		this.thmContextVecList = new ArrayList<int[]>();
		this.thmContextVecMapList = new ArrayList<Map<Integer, Integer>>();
		this.localVariableNamesMMap = ArrayListMultimap.create();
		this.inThmFlag = false;
		this.recentParseSpanning = false;
		this.currentInputSansTex = null;
		this.noTexTokenStructAr = null;
		this.syntaxnetQuery = null;
		this.curParseExcessiveLatex = false;
		logger.info("(cleanup)@@@headParseStruct" + headParseStruct);
	}	
	
	/**
	 * Global clean up, e.g. after \end{document}.
	 * In addition to everything parseRunLocalCleanUp() does, 
	 * also cleans up localVariableNamesMMap.
	 * 
	 */
	public void parseRunGlobalCleanUp(){
		parseRunLocalCleanUp();
		this.recentAssert = null;
		this.prevTokenList = null;
		this.tokenList = null;
		this.recentEnt = null;
		this.globalVariableNamesMMap = ArrayListMultimap.create();
	}
	
	public void setParseErrorCode(ParseErrorCode errorCode){
		this.parseErrorCode = errorCode;
	}
	
	public ParseErrorCode getParseErrorCode(){
		return this.parseErrorCode;
	}
	
	public void setCurParseExcessiveLatex(boolean bool){
		this.curParseExcessiveLatex = bool;
	}
	
	public boolean curParseExcessiveLatex(){
		return this.curParseExcessiveLatex;
	}
	
	@Override
	public String toString(){
		StringBuilder sb = new StringBuilder(300);
		sb.append("globalVariableNamesMMap: " + globalVariableNamesMMap);
		sb.append("localVariableNamesMMap: " + localVariableNamesMMap);
		sb.append("inThmFlag: " + inThmFlag);
		return sb.toString();
	}

	public int numNonTexTokens() {
		return numNonTexTokens;
	}

	/**
	 * Sets numNonTexTokens to 0.
	 */
	public void resetNumNonTexTokens() {
		this.numNonTexTokens = 0;
	}
	
	public void addToNumNonTexTokens(int numNonTexTokensToAdd) {
		this.numNonTexTokens += numNonTexTokensToAdd;
	}
	
}
