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

	//list of relevant tokens 
	private List<String> relevantTokens;
	//scores for the tokens in relevantTokens
	private List<Integer> tokenScores;
	
	//map of relevant tokens and their scores
	private Map<String, Integer> tokenScoreMap;
	
	//list of indices of thms of returned by intersection search.
	private List<Integer> intersectionVecList;
	
	//map of thmIndex and their span scores
	private Map<Integer, Integer> thmSpanMap;
	
	//the keyset of which is intersectionVecList
	//make thread-safe!
	Map<String, Integer> map = new LinkedHashMap<>();
	
	static{
		
	}
	
	public SearchState(){
		
		this.tokenScoreMap = new HashMap<String, Integer>();
	}
	
	public SearchState( List<Integer> intersectionVecList){	
		this.intersectionVecList = intersectionVecList;
		this.tokenScoreMap = new HashMap<String, Integer>();
	}
	
	/**
	 * Adds token and its score to tokenScoreMap. 
	 * @param token
	 * @param score
	 */
	public void addTokenScore(String token, Integer score){
		tokenScoreMap.put(token, score);
	}
	
	public List<String> relevantTokensList(){
		return relevantTokens;
	}
	
	public List<Integer> tokenScoresList(){
		return tokenScores;
	}
	
	public List<Integer> intersectionVecList(){
		return intersectionVecList;
	}
}
