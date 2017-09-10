package thmp.search;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import thmp.parse.ParseState;

/**
 * Information on the state of the current query. E.g. list
 * of relevant tokens, including 2/3-grams, 
 * Useful for e.g. combined search ranking in CombinedSearch.
 * One SearchState instance per query, therefore intentionally
 * not meant to be used by multiple threads.
 * 
 * @author yihed
 *
 */
public class SearchState {

	//map of relevant tokens and their scores
	private Map<String, Integer> tokenScoreMap;
	
	//total number of words added, including overlapping 2 or 3 grams
	private int totalWordAdded;
	
	//list of indices of thms of returned by intersection search.
	private List<Integer> intersectionVecList;
	
	//map of thmIndex and their span scores, meaning how many singleton
	//tokens in query are covered.
	private Map<Integer, Integer> thmSpanMap;
	
	//private int intersectionSearchThmMaxSpan;
	
	//The largest span of current query word set amongst any 
	// theorem that are in the keyset of thmSpanMap.
	/**the max span of any theorem produced by intersection search,
	 * used to determine if perform literal word search.*/
	private int largestWordSpan;
	//map of thmIndex and their word-weight scores
	private Map<Integer, Integer> thmScoreMap;
	
	private ParseState parseState;
	
	//the keyset of which is intersectionVecList
	//make thread-safe!
	//Map<String, Integer> map = new LinkedHashMap<>();
	
	public SearchState(){
		
		this.tokenScoreMap = new HashMap<String, Integer>();
		this.thmSpanMap = new HashMap<Integer, Integer>();
	}
		
	/**
	 * Adds token and its score to tokenScoreMap. 
	 * @param token
	 * @param score
	 */
	public void addTokenScore(String token, Integer score){
		tokenScoreMap.put(token, score);
	}
	
	//should be consistent with set/add!
	public void setThmScoreMap(Map<Integer, Integer> scoreMap){
		this.thmScoreMap = scoreMap;
	}
	
	public Map<Integer, Integer> thmScoreMap(){
		return this.thmScoreMap;
	}
		
	/**
	 * Adds token and its score to tokenScoreMap. 
	 * @param thmIndex
	 * @param span Span, as in width, in the current query of 
	 * thmIndex as derived in SearchIntersection. How many words
	 * in the query does the thm cover.
	 * This is derived as the size of an entry for thmWordSpanMMap,
	 * which is a Multimap of thmIndex, and the (index of) set of words in query 
		 that appear in the thm
	 */
	public void addThmSpan(Integer thmIndex, Integer span){
		thmSpanMap.put(thmIndex, span);
	}
	
	/*public void setIntersectionSearchThmMaxSpan(int maxSpan) {
		this.intersectionSearchThmMaxSpan = maxSpan;
	}*/
	
	public void addThmSpan(Map<Integer, Integer> spanMap){
		thmSpanMap.putAll(spanMap);
	}
	
	/**
	 * The largest span of current query word set amongst any 
	 * theorem that are in the keyset of thmSpanMap.
	 */
	public void setLargestWordSpan(int span){
		if(span > this.largestWordSpan){
			this.largestWordSpan = span;
		}
	}
	
	/**the max span of any theorem produced by intersection search,
	 * used to determine if perform literal word search.*/
	public int largestWordSpan(){
		return this.largestWordSpan;
	}
	
	public void set_totalWordAdded(int totalWordAdded){
		this.totalWordAdded = totalWordAdded;
	}
	
	public int totalWordAdded(){
		return totalWordAdded;
	}
	
	/**
	 * Map of How many words in the query does the thm cover.
	 * This is derived as the size of an entry for thmWordSpanMMap,
	 * which is a Multimap of thmIndex, and the (index of) set of words in query 
	 * that appear in the thm
	 * @return
	 */
	public Map<Integer, Integer> thmSpanMap(){
		return thmSpanMap;
	}
	
	public List<Integer> intersectionVecList(){
		return intersectionVecList;
	}

	public void set_intersectionVecList(List<Integer> intersectionVecList){
		this.intersectionVecList = intersectionVecList;
	}

	public ParseState getParseState() {
		return parseState;
	}

	public void setParseState(ParseState parseState) {
		this.parseState = parseState;
	}
}
