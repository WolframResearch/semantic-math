package thmp.search;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
	
	/* Score map for context search.
	 * Keys are thm indices, values are how many relations pairs coincide
	 * based on context vectors.
	 */
	private Map<Integer, Integer> contextSearchNumCoincidingMap;
	
	//TreeMap of context vec scores and list of thm indices
	private TreeMap<Integer, List<Integer>> contextScoreIndexTMap;
	
	//The largest span of current query word set amongst any 
	// theorem that are in the keyset of thmSpanMap.
	/**the max span of any theorem produced by intersection search,
	 * used to determine if perform literal word search.*/
	private int largestWordSpan;
	//map of thmIndex and their word-weight scores
	private Map<Integer, Integer> thmScoreMap;
	
	private ParseState parseState;
	private boolean allowLiteralSearch = true;
	
	public static class SearchStateBuilder{
		
		private boolean allowLiteralSearch = true;
		
		public void disableLiteralSearch() {
			this.allowLiteralSearch = false;
		}
		
		public SearchState build() {
			return new SearchState(this);
		}
		
	}
	
	public SearchState(SearchStateBuilder builder){
		this();		
		this.allowLiteralSearch = builder.allowLiteralSearch;		
	}
	
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
	
	/**
	 * Score map derived from intersection search.
	 * @return
	 */
	public Map<Integer, Integer> thmScoreMap(){
		return this.thmScoreMap;
	}
	
	public void setContextVecScoreMap(Map<Integer, Integer> scoreMap){
		this.contextSearchNumCoincidingMap = scoreMap;
	}
	
	/**
	 * Score map for context search.
	 * Keys are thm indices, values are how many relations pairs coincide
	 * based on context vectors.
	 * @return @Nullable The context vec map. 
	 */
	public Map<Integer, Integer> contextVecScoreMap(){
		return this.contextSearchNumCoincidingMap;
	}
	
	public void setContextScoreIndexTMap(TreeMap<Integer, List<Integer>> scoreMap){
		this.contextScoreIndexTMap = scoreMap;
	}
	
	/**
	 * Score map for context search.
	 * Keys are thm indices, values are how many relations pairs coincide
	 * based on context vectors.
	 * @return @Nullable The context vec map. 
	 */
	public TreeMap<Integer, List<Integer>> contextScoreIndexTMap(){
		return this.contextScoreIndexTMap;
	}
	
	public boolean allowLiteralSearch() {
		return allowLiteralSearch;
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
	 * used to determine if perform literal word search, and whether
	 * to perform relation and context searches. So "a" and "a b" span
	 * two, not three, words, even if "a b" is a valid bigram whose scores
	 * are countered differently.
	 */
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
	 * Keys are indices of thms, and values are span lengths.
	 * Map of How many words in the query does the thm cover.
	 * This is derived as the size of an entry for thmWordSpanMMap,
	 * which is a Multimap of thmIndex, and the (index of) set of words in query 
	 * that appear in the thm.
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
