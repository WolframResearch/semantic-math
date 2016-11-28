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
import java.util.ArrayList;
import java.util.Arrays;


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
	
	private boolean writeUnknownWordsToFile;
	
	//punctuation at end of current parse segment, segments are separated
	//by punctuations.
	private String finalPunctuation;	

	//pattern for matching latex expressions wrapped in dollars
	private static final Pattern LATEX_DOLLARS_PATTERN = Pattern.compile("^\\$([^$]+)\\$$");
	
	//contains both text and latex e.g. "winding number $W_{ii'} (y)$"
	private static final Pattern TEXT_LATEX_PATTERN = Pattern.compile("^[^$]+\\$([^$]+)\\$.*$");
	
	//contains both text and latex e.g. "winding number $W_{ii'} (y)$"
	private static final Pattern COLON_PATTERN = Pattern.compile("([^:=\\s]+)\\s*[:=].*");
		
	/* 
	 * Multimap of what each symbol stands for in the text so far.
	 * Make clear it's ListMultimap, since symbol order matters.
	 * Keys are variable Strings, and values   that contain
	 * Structs, and the input sentence that defines it.
	 * When storing a definition such as "$f: X\to Y$", also store variable
	 * name before the colon, i.e. "f"
	 * 
	 */
	private ListMultimap<String, VariableDefinition> variableNamesMMap;
	
	//parsedExpr to record parsed pairs during parsing. 
	private static List<ParsedPair> parsedExpr = new ArrayList<ParsedPair>();
	
	private static final Logger logger = LogManager.getLogger(ParseState.class);
	
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
		
		private String variableName;
		
		private String originalDefinitionSentence;

		public VariableDefinition(String variableName, Struct definingStruct, String originalDefinitionStr){
			this.definingStruct = definingStruct;
			this.originalDefinitionSentence = originalDefinitionStr;
		}

		@Override
		public String toString(){
			return definingStruct.toString();
		}
		
		/**
		 * @return the variableName
		 */
		public String getVariableName() {
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
	public ImmutableListMultimap<String, VariableDefinition> getAndClearVariableNamesMMap(){
		//ListMultimap<String, VariableDefinition> tempMap = ArrayListMultimap.create(this.variableNamesMMap);
		ImmutableListMultimap<String, VariableDefinition> tempMap = ImmutableListMultimap.copyOf(this.variableNamesMMap);
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
	public ImmutableListMultimap<String, VariableDefinition> getVariableNamesMMap(){
		//defensively copy.
		//ListMultimap<String, VariableDefinition> tempMap = ArrayListMultimap.create(this.variableNamesMMap);
		ImmutableListMultimap<String, VariableDefinition> tempMap = ImmutableListMultimap.copyOf(this.variableNamesMMap);
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
			Matcher m = COLON_PATTERN.matcher(name);
			
			if(m.find()){
				String latexName = m.group(1);
				VariableDefinition latexDef = new VariableDefinition(latexName, entStruct, this.currentInputStr);
				if(!variableNamesMMap.containsKey(latexName)){
					this.variableNamesMMap.put(latexName, latexDef);
				}
			}
		}//if name contains text and latex, e.g. "winding number $W_{ii'} (y)$"
		//create a separate entry with just the latex part.
		else if( (textLatexMatcher = TEXT_LATEX_PATTERN.matcher(name)).find() ){
			
			String latexName = textLatexMatcher.group(1);
			VariableDefinition latexDef = new VariableDefinition(latexName, entStruct, this.currentInputStr);
			if(!variableNamesMMap.containsKey(latexName)){
				this.variableNamesMMap.put(latexName, latexDef);
			}
		}
		
		VariableDefinition def = new VariableDefinition(name, entStruct, this.currentInputStr);		
		if(!variableNamesMMap.containsKey(name)){
			this.variableNamesMMap.put(name, def);	
		}
	}
	
	/**
	 * @param name Name of entStruct.
	 * @param entStruct Entity to be added with name entStruct.
	 * @return Return most recently-added Struct with this name.
	 */
	public Struct getVariableDefinition(String name){		
		
		List<VariableDefinition> r = this.variableNamesMMap.get(name);
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
		
		return this.variableNamesMMap.get(name);			
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
