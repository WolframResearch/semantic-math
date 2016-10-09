package thmp.search;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Information on the state of the current query. E.g. list
 * of relevant tokens, including 2/3-grams, 
 * Useful for e.g. combined search ranking in CombinedSearch.
 * One instance per query.
 * 
 * @author yihed
 *
 */
public class SearchState {

	//map of relevant tokens and their scores
	private Map<String, Integer> tokenScoreMap;
	
	//total number of words added, including overlapping 2/3 grams
	private int totalWordAdded;
	
	//list of indices of thms of returned by intersection search.
	private List<Integer> intersectionVecList;
	
	//map of thmIndex and their span scores
	private Map<Integer, Integer> thmSpanMap;
	
	//map of thmIndex and their word-weight scores
	private Map<Integer, Integer> thmScoreMap;
	
	//the keyset of which is intersectionVecList
	//make thread-safe!
	Map<String, Integer> map = new LinkedHashMap<>();
	
	public SearchState(){
		
		this.tokenScoreMap = new HashMap<String, Integer>();
		this.thmSpanMap = new HashMap<Integer, Integer>();
	}
	
	/*public SearchState( List<Integer> intersectionVecList){	
		this.intersectionVecList = intersectionVecList;
		this.tokenScoreMap = new HashMap<String, Integer>();
	}*/
	
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
	
	//should be consistent with set/add!
		public Map<Integer, Integer> thmScoreMap(){
			return this.thmScoreMap;
		}
		
	/**
	 * Adds token and its score to tokenScoreMap. 
	 * @param thmIndex
	 * @param span Span of the current query for thmIndex as derived in SearchIntersection.
	 */
	public void addThmSpan(Integer thmIndex, Integer span){
		thmSpanMap.put(thmIndex, span);
	}
	
	public void addThmSpan(Map<Integer, Integer> spanMap){
		thmSpanMap.putAll(spanMap);
	}
	
	public void set_totalWordAdded(int totalWordAdded){
		this.totalWordAdded = totalWordAdded;
	}
	
	public int totalWordAdded(){
		return totalWordAdded;
	}
	
	public Map<Integer, Integer> thmSpanMap(){
		return thmSpanMap;
	}
	
	public List<Integer> intersectionVecList(){
		return intersectionVecList;
	}

	public void set_intersectionVecList(List<Integer> intersectionVecList){
		this.intersectionVecList = intersectionVecList;
	}
}
