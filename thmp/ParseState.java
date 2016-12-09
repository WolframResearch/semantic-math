package thmp;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;

import thmp.ThmP1.ParsedPair;
import thmp.utils.Buggy;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;


/**
 * Captures state of the parse, such as recent entities used (recentEnt).
 * To be used to assist ThmP1.parse(). To transfer information between
 * adjacent sentences, to preserve contextual information.
 * @author yihed
 */
public class ParseState {
	
	//current String being parsed. I.e. the unit
	//of the input that's being tokenized, so 
	//delimiter-separated.
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
	private BigInteger relationalContextVec;
	
	private boolean writeUnknownWordsToFile;
	
	//punctuation at end of current parse segment, segments are separated
	//by punctuations.
	private String finalPunctuation;	

	//pattern for matching latex expressions wrapped in dollars
	private static final Pattern LATEX_DOLLARS_PATTERN = Pattern.compile("^\\$([^$]+)\\$$");
	
	//contains both text and latex e.g. "winding number $W_{ii'} (y)$"
	private static final Pattern TEXT_LATEX_PATTERN = Pattern.compile("^[^$]+\\$([^$]+)\\$.*$");
	
	//contains both text and latex e.g. "winding number $W_{ii'} (y)$"
	//private static final Pattern COLON_PATTERN = Pattern.compile("([^:=\\s]+)\\s*[:=].*|(?([.]+)(\\subset)).*");
		private static final Pattern COLON_PATTERN = Pattern.compile("([^:=\\s]+)\\s*[:=].*");
	
	//private static final Pattern COLON_PATTERN = Pattern.compile("([.]+)\\s*[:=].*");	
	//first group captures name, second the parenthesis/bracket/brace. E.g. "f[x]"
	private static final Pattern VARIABLE_NAME_PATTERN = Pattern.compile("([^\\[\\{\\(]+)([\\[\\{\\(]).*");
	
	//Only make first part disposable if it's within certain length; e.g. K-algebra
	private static final Pattern VARIABLE_NAME_DASH_PATTERN = Pattern.compile("([^-]+){1,2}[-].*");
	
	/* 
	 * Multimap of what each symbol stands for in the text so far.
	 * Make clear it's ListMultimap, since symbol order matters.
	 * Keys are variable Strings, and values   that contain
	 * Structs, and the input sentence that defines it.
	 * When storing a definition such as "$f: X\to Y$", also store variable
	 * name before the colon, i.e. "f"
	 * 
	 */
	private ListMultimap<VariableName, VariableDefinition> variableNamesMMap;
	
	//parsedExpr to record parsed pairs during parsing. 
	private static List<ParsedPair> parsedExpr = new ArrayList<ParsedPair>();
	
	private static final Logger logger = LogManager.getLogger(ParseState.class);
	
	/**
	 * Wrapper class around variable string name (Head), also records
	 * the type, e.g. head is "f" and type is BRACKET for the variable "f[x]".
	 */
	public static class VariableName implements Serializable{
		
		private static final long serialVersionUID = 1L;
		
		private String head;
		private VariableNameType variableNameType;
		
		/**
		 * Enum to represent different types of variables.
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
					return DASH;
				default:
					return NONE;
				}
			}
		}
		
		@Override
		public String toString(){
			return (new StringBuilder(this.head)).append(": ")
					.append(this.variableNameType).toString();
		}
		
		public VariableName(String head, VariableNameType type){
			this.head = head;
			this.variableNameType = type;
		}

		public String head(){
			return this.head;
		}
		
		public VariableNameType variableType(){
			return this.variableNameType;
		}
		
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((head == null) ? 0 : head.hashCode());
			result = prime * result + ((variableNameType == null) ? 0 : variableNameType.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object other){
			
			if(this == other){
				return true;
			}
			
			//don't need null == other, because null is not instanceof anything.
			if(!(other instanceof VariableName)){
				return false;
			}
			
			VariableName otherName = (VariableName)other;
			if(otherName.head() == null && null != this.head){
				return false;
			}
			
			if(otherName.variableNameType != this.variableNameType
					|| !otherName.head.equals(this.head)){
				return false;
			}
			return true;
		}
		
	}
	
	/**
	 * Contain information that define variables, whose names are stored in 
	 * variableNamesMMap.
	 */
	public static class VariableDefinition implements Serializable {
		
		private static final long serialVersionUID = 1L;

		//the Struct defining the variable.
		//E.g. in "$F$ is a field", the Struct for "field"
		//is the defining Struct.
		private Struct definingStruct;
		
		private VariableName variableName;
		
		private String originalDefinitionSentence;

		public VariableDefinition(VariableName variableName, Struct definingStruct, String originalDefinitionStr){
			this.variableName = variableName;
			this.definingStruct = definingStruct;
			this.originalDefinitionSentence = originalDefinitionStr;
		}

		@Override
		public String toString(){
			return definingStruct.toString();
		}
		
		
		
		/* (non-Javadoc)
		 * @see java.lang.Object#hashCode()
		 */
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
		 * @return the definingStruct
		 */
		public Struct getDefiningStruct() {
			return definingStruct;
		}

		/**
		 * @return the originalDefinitionStr
		 */
		public String getOriginalDefinitionStr() {
			return originalDefinitionSentence;
		}
		
	}
	
	//waiting WLCommandWrapper map on deck, waiting to be added. Necessary 
	//because don't know if need to add to next logic layer, or to current
	//layer, until the entire tree is read.
	//private Multimap<ParseStructType, WLCommandWrapper> waitingWrapperMMap;	
	private ParseState(ParseStateBuilder builder){
		this.variableNamesMMap = ArrayListMultimap.create();
		this.writeUnknownWordsToFile = builder.writeUnknownWordsToFile;		
		//this.headParseStructList = new ArrayList<ParseStruct>();
	}
	
	/**
	 * Whether to write unknown words to file. Also determines
	 * if need to collect unknown words or not.
	 * @return
	 */
	public boolean writeUnknownWordsToFile(){
		return this.writeUnknownWordsToFile;
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
		return relationalContextVec;
	}

	/**
	 * @param relationalContextVec the relationalContextVec to set
	 */
	public void setRelationalContextVec(BigInteger relationalContextVec) {
		this.relationalContextVec = relationalContextVec;
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
	public ImmutableListMultimap<VariableName, VariableDefinition> getAndClearVariableNamesMMap(){
		//ListMultimap<String, VariableDefinition> tempMap = ArrayListMultimap.create(this.variableNamesMMap);
		ImmutableListMultimap<VariableName, VariableDefinition> tempMap = ImmutableListMultimap.copyOf(this.variableNamesMMap);
		this.variableNamesMMap = ArrayListMultimap.create();
		return tempMap;
	}
	
	/**
	 * Map of what each symbol stands for in the text so far.
	 * Make clear it's ListMultimap, since symbol order matters.
	 * Returns immutable map, so the original map stays protected and 
	 * can only be modified through the exposed modification method.
	 * @return
	 */
	public ImmutableListMultimap<VariableName, VariableDefinition> getVariableNamesMMap(){
		//defensively copy.
		//ListMultimap<String, VariableDefinition> tempMap = ArrayListMultimap.create(this.variableNamesMMap);
		ImmutableListMultimap<VariableName, VariableDefinition> tempMap = ImmutableListMultimap.copyOf(this.variableNamesMMap);
		return tempMap;
	}
	
	/**
	 * 
	 * @param name Name of entStruct.
	 * @param entStruct Entity to be added with name entStruct.
	 */
	public void addLocalVariableStructPair(String name, Struct entStruct){
		
		
		Matcher latexContentMatcher = LATEX_DOLLARS_PATTERN.matcher(name);
		Matcher textLatexMatcher;
		//System.out.println("adding name: " +name +Arrays.toString(Thread.currentThread().getStackTrace()));
		
		if(latexContentMatcher.find()){
			name = latexContentMatcher.group(1);
			//if colon/equal sign present, record
			//the expression before colon or equal. E.g. "f(x) = x", as well 
			//as the entire expression
			Matcher colonMatcher = COLON_PATTERN.matcher(name);
			
			if(colonMatcher.find()){
				String latexName = colonMatcher.group(1);
				//define a VariableName of the right type. 
				VariableName latexVariableName = getVariableName(latexName);
				VariableDefinition latexDef = new VariableDefinition(latexVariableName, entStruct, this.currentInputStr);
				
				if(!variableNamesMMap.containsKey(latexVariableName) || !variableNamesMMap.containsEntry(latexVariableName, latexDef)){				
					System.out.println("....********latexVariableName: " + latexVariableName);
					this.variableNamesMMap.put(latexVariableName, latexDef);
					
					//if variableNameType is paren/brace/bracket, also include name without 
					//the paren/brace/bracket in the map, e.g. $f(x) = x$, $f$ is smooth.
					//nest inside so not to check for containment again.
					if(latexVariableName.variableType().isParenBracketBrace()){
						latexVariableName = new VariableName(latexVariableName.head, VariableName.VariableNameType.NONE);
						this.variableNamesMMap.put(latexVariableName, latexDef);
					}
				}
				
			}
		}//if name contains text and latex, e.g. "winding number $W_{ii'} (y)$"
		//create a separate entry with just the latex part.
		else if( (textLatexMatcher = TEXT_LATEX_PATTERN.matcher(name)).find() ){
			
			String latexName = textLatexMatcher.group(1);
			VariableName latexVariableName = getVariableName(latexName);
			
			if(!variableNamesMMap.containsKey(latexVariableName)){
				VariableDefinition latexDef = new VariableDefinition(latexVariableName, entStruct, this.currentInputStr);
				this.variableNamesMMap.put(latexVariableName, latexDef);
			}
		}
		
		VariableName variableName = getVariableName(name);

		if(!variableNamesMMap.containsKey(variableName)){
			//System.out.println(variableName + "-++-" + variableNamesMMap + " ===== "  +Arrays.toString(Thread.currentThread().getStackTrace()));
			
			VariableDefinition def = new VariableDefinition(variableName, entStruct, this.currentInputStr);	
			this.variableNamesMMap.put(variableName, def);	
		}
		
	}
	
	/**
	 * Creates a VariableName instance of the appropriate type,
	 * based on name.
	 * @param name Should not contain $ $ around it.
	 * @return
	 */
	public static VariableName getVariableName(String name){
		
		VariableName.VariableNameType variableNameType = VariableName.VariableNameType.NONE;
		//figure out the VariableNameType
		Matcher parenMatcher = VARIABLE_NAME_PATTERN.matcher(name);
		Matcher dashMatcher;
		
		if(parenMatcher.find()){
			
			name = parenMatcher.group(1);
			variableNameType = VariableName.VariableNameType
					.getVariableNameTypeFromString(parenMatcher.group(2));
			//throw new IllegalStateException(variableNameType + " " + name);
		}else if((dashMatcher = VARIABLE_NAME_DASH_PATTERN.matcher(name)).find()){
			
			name = dashMatcher.group(1);
			variableNameType = VariableName.VariableNameType
					.getVariableNameTypeFromString("-");
		}
		
		return new VariableName(name, variableNameType);
	}
	
	/**
	 * @param name Name of entStruct.
	 * @param entStruct Entity to be added with name entStruct.
	 * @return Return most recently-added Struct with this name.
	 */
	public Struct getVariableDefinition(String name){	
		
		VariableName variableName = getVariableName(name);
		List<VariableDefinition> r = this.variableNamesMMap.get(variableName);
		int listSz = r.size();
		
		if(r.isEmpty()){
			return null;
		}
		return r.get(listSz-1).getDefiningStruct();		
	}
	

	/**
	 * Get list of VariableDefinition's with specified name.
	 * @param name
	 * @return
	 */
	public List<VariableDefinition> getNamedStructList(String name){		
		
		VariableName variableName = getVariableName(name);
		return this.variableNamesMMap.get(variableName);			
	}
	
	/**
	 * Logs various objects to file, e.g. variableNamesMMap.
	 */
	public void logState(){
		//remove this on PRD
		System.out.println("variableNamesMMap: " + this.variableNamesMMap);
		logger.info(this.variableNamesMMap);
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

	/**
	 * @return the tokenList
	 */
	public List<Struct> getTokenList() {
		return tokenList;
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
		logger.info("@@@headParseStruct" + headParseStruct);
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
	/*public void addToHeadParseStructList(ParseStruct headParseStruct) {
		this.headParseStructList.add(headParseStruct);
	}*/
	
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
	
}
