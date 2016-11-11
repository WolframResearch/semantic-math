package thmp;

import java.util.Arrays;
import java.util.List;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import thmp.ParseToWLTree.WLCommandWrapper;

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
	
	//current parse struct, somewhere down the tree from headParseStruct
	private ParseStruct curParseStruct;
	
	//punctuation at end of current parse segment, segments are separated
	//by punctuations
	private String punctuation;	

	//waiting WLCommandWrapper map on deck, waiting to be added. Necessary 
	//because don't know if need to add to next logic layer, or to current
	//layer, until the entire tree is read.
	private Multimap<ParseStructType, WLCommandWrapper> waitingWrapperMMap;
	
	public ParseState(){
		this.waitingWrapperMMap = ArrayListMultimap.create();
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
	
	public void addParseStructWrapperPair(ParseStructType type, WLCommandWrapper wrapper){
		this.waitingWrapperMMap.put(type, wrapper);
	}
	
	public Multimap<ParseStructType, WLCommandWrapper> retrieveAndClearWrapperMMap(){
		Multimap<ParseStructType, WLCommandWrapper> mapCopy = ArrayListMultimap.create(this.waitingWrapperMMap);
		this.waitingWrapperMMap = ArrayListMultimap.create();
		return mapCopy;		
	}
	
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
	 * @return the tokenList
	 */
	public void setHeadParseStruct(ParseStruct parseStruct) {
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
