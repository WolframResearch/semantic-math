package thmp.search;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;

import thmp.parse.ParseState;
import thmp.search.SearchIntersection.ThmScoreSpanPair;

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

	/**map of relevant tokens and their scores.
	 * Tokens are *not* normalized, meaning endings not stripped, etc.
	 */
	private Map<String, Integer> tokenScoreMap;
	
	/**
	 * Set of *normalized* tokens, i.e. word forms that were actually used
	 * in search, and have valid entries in wordScoreMap.
	 */
	public Set<String> normalizedTokenSet;
	//total number of words added, including overlapping 2 or 3 grams
	private int totalWordAdded;
	
	//list of indices of thms of returned by intersection search.
	private List<Integer> intersectionVecList;
	//priority queue containing thm index, score, and span
	private List<ThmScoreSpanPair> thmScoreSpanList;
	
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
		this.normalizedTokenSet = new HashSet<String>();
		//this.intersectionVecList = Collections.emptyList();
	}
	
	/**
	 * Adds token and its score to tokenScoreMap. 
	 * @param token
	 * @param score
	 */
	public void addTokenScore(String token, Integer score){
		tokenScoreMap.put(token, score);
	}
	
	public void addNormalizedSearchToken(String tok) {
		this.normalizedTokenSet.add(tok);
	}
	
	public boolean tokenAlreadySearched(String tok) {
		return this.normalizedTokenSet.contains(tok);		
	}
	
	/**
	 * map of thmIndex and their word-weight scores.
	 * @param scoreMap
	 */
	public void setThmScoreMap(Map<Integer, Integer> scoreMap){
		this.thmScoreMap = scoreMap;
	}
	
	/**
	 * Score map derived from intersection search.
	 * Map of thmIndex and their word-weight scores.
	 * @return @Nullable 
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
	
	/*Commented out Dec 13, 2017
	 * public void setContextScoreIndexTMap(TreeMap<Integer, List<Integer>> scoreMap){
		this.contextScoreIndexTMap = scoreMap;
	}*/
	
	/**
	 * Score map for context search.
	 * Keys are thm indices, values are how many relations pairs coincide
	 * based on context vectors.
	 * @return @Nullable The context vec map. 
	 */
	/*Commented out Dec 13, 2017
	 * public TreeMap<Integer, List<Integer>> contextScoreIndexTMap(){
		return this.contextScoreIndexTMap;
	}*/
	
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
	
	/**
	 * Result list produced from intersection search.
	 * @return @Nullable Intersection search result list. 
	 */
	public List<Integer> intersectionVecList(){
		return intersectionVecList;
	}

	public void set_intersectionVecList(List<Integer> intersectionVecList){
		this.intersectionVecList = intersectionVecList;
	}

	/**
	 * List containing thm index, score, and span
	 * @return
	 */
	public List<ThmScoreSpanPair> thmScoreSpanList(){
		return thmScoreSpanList;
	}

	public void set_thmScoreSpanList(List<ThmScoreSpanPair> pq){
		this.thmScoreSpanList = pq;
	}
	
	public ParseState getParseState() {
		return parseState;
	}

	public void setParseState(ParseState parseState) {
		this.parseState = parseState;
	}
}
