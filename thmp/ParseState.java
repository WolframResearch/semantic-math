package thmp;

import java.util.List;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

import thmp.ThmP1.ParsedPair;

import java.util.ArrayList;
import java.util.Arrays;


/**
 * Captures state of the parse, such as recent entities used (recentEnt).
 * To be used to assist ThmP1.parse(). To transfer information between
 * adjacent sentences, to preserve contextual information.
 * @author yihed
 */
public class ParseState {

	private Struct recentEnt;
	
	//recent assert
	private Struct recentAssert;

	//tokenized input list
	private List<Struct> tokenList;
	
	//parseStruct, for layering built WLCommands
	private ParseStruct headParseStruct;
	
	//list of ParseStruct's, one for each parse tree.
	private List<ParseStruct> headParseStructList;
	
	//current parse struct, somewhere down the tree from headParseStruct
	private ParseStruct curParseStruct;
	
	private boolean writeUnknownWordsToFile;
	
	//punctuation at end of current parse segment, segments are separated
	//by punctuations.
	private String punctuation;	

	//what each symbol stands for in the text so far.
	//Make clear it's ListMultimap, since symbol order matters.
	private ListMultimap<String, Struct> variableNamesMMap;
	
	//parsedExpr to record parsed pairs during parsing. 
	private static List<ParsedPair> parsedExpr = new ArrayList<ParsedPair>();
	
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
	 * What each symbol stands for in the text so far.
	 * Make clear it's ListMultimap, since symbol order matters.
	 * @return
	 */
	public ListMultimap<String, Struct> getAndClearVariableNamesMMap(){
		ListMultimap<String, Struct> tempMap = ArrayListMultimap.create(this.variableNamesMMap);
		this.variableNamesMMap = ArrayListMultimap.create();
		return tempMap;
	}
	
	/**
	 * 
	 * @param name Name of entStruct.
	 * @param entStruct Entity to be added with name entStruct.
	 */
	public void addLocalVariableStructPair(String name, Struct entStruct){
		this.variableNamesMMap.put(name, entStruct);
	}
	
	/**
	 * @param name Name of entStruct.
	 * @param entStruct Entity to be added with name entStruct.
	 * @return Return most recently-added Struct with this name.
	 */
	public Struct getNamedStruct(String name){		
		
		List<Struct> r = this.variableNamesMMap.get(name);
		int listSz = r.size();
		
		if(r.isEmpty()){
			return null;
		}
		return r.get(listSz-1);		
	}
	
	/**
	 * Get list of Structs with specified name.
	 * @param name
	 * @return
	 */
	public List<Struct> getNamedStructList(String name){		
		
		return this.variableNamesMMap.get(name);			
	}
	
	/**
	 * punctuation at end of current parse segment, segments are separated
	 * by punctuations.
	 * @return the punctuation
	 */
	public String getAndClearCurPunctuation() {
		String punctuationCopy = punctuation;
		this.punctuation = null;
		return punctuationCopy;
	}

	/**
	 * @param punctuation the punctuation to set
	 */
	public void setPunctuation(String punctuation) {
		this.punctuation = punctuation;
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
		if(headParseStruct == null){
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
	public void setTokenList(List<Struct> tokenList) {
		this.tokenList = tokenList;
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
