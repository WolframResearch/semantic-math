package thmp.search;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.wolfram.puremath.dbapp.SimilarThmUtils;

import thmp.parse.DetectHypothesis;
import thmp.parse.InitParseWithResources;
import thmp.parse.ParseRun;
import thmp.parse.ParseState;
import thmp.parse.ThmP1;
import thmp.parse.ParseState.ParseStateBuilder;
import thmp.search.SearchCombined.ThmHypPair;
import thmp.search.SearchState.SearchStateBuilder;
import thmp.utils.DBUtils;
import thmp.utils.FileUtils;
import thmp.utils.WordForms;

/**
 * Methods to precompute indices of similar theorems a priori, 
 * as well as retrieving similar thms 
 * 
 * @author yihed
 */
public class SimilarThmSearch {

	private static final int numHighestResults = 50;
	private static final boolean DEBUG = FileUtils.isOSX() ? InitParseWithResources.isDEBUG() : false;
	//private static final boolean DEBUG = false;
	private static final Logger logger = LogManager.getLogger(SimilarThmSearch.class);
	
	/**
	 * Finds index list of similar theorems by retrieving precomputed
	 * indices from database. Used at app runtime.
	 * @param thmIndex
	 * @return
	 */
	public static List<Integer> getSimilarThms(int thmIndex){
		
		Connection conn = DBUtils.getPooledConnection();
		List<Integer> indexList;
		
		try {
			indexList = SimilarThmUtils.getSimilarThmListFromDb(thmIndex, conn);
		}catch(SQLException e) {
			logger.error("SQLException while getting similar thms! " + e);
			return Collections.emptyList();
		}finally {
			DBUtils.closePooledConnection(conn);
		}
		return indexList;
	}
	
	/**
	 * Finds index list of similar theorems. Used to precompute indices
	 * locally, *not* at app runtime, which should use getSimilarThms().
	 * @param thmIndex
	 * @return
	 */
	public static List<Integer> preComputeSimilarThm(int thmIndex) {
		
		/*
		 * Call contextSearchMap(String query, List<Integer> nearestThmIndexList, 
			Searcher<Map<Integer, Integer>> searcher, SearchState searchState)
			to get ranking.
		 */		
		ThmHypPair thmHypPair = ThmHypPairGet.retrieveThmHypPairWithThm(thmIndex);
		String thmStr = thmHypPair.getEntireThmStr();
		if(DEBUG) System.out.println("QUERY THM: " + thmStr);
		
		//keep a running parse state to collect variable definitions.
		ParseStateBuilder parseStateBuilder = new ParseStateBuilder();
		ParseState parseState = parseStateBuilder.build();
		/*keep track of thm scores. Key is thmIndex, value is intersection search score*/
		Map<Integer, Integer> thmScoreMap = new HashMap<Integer, Integer>();
		Map<Integer, Integer> contextScoreMap = new HashMap<Integer, Integer>();
		
		/* Divides thm into pieces, i.e. logical components, e.g.
		 * "if ... ", and "then ..." */
		String[] thmPieces = ThmP1.preprocess(thmStr);
		boolean isVerbose = DEBUG;
		for(String str : thmPieces) {
			//divide thm string into several parts, to target search results specifically
			str = chopThmStr(str, parseState);
			//must parse current str *after* symbol replacement, to not replace def in current str.
			ParseRun.parseInput(str, parseState, isVerbose);
			
			//weigh depending on hyp or stm, prioritize stm
			//parseState.getHeadParseStruct();
			
			List<Integer> thmIndexList = gatherThmFromWords(str, thmScoreMap);			
			SearchState searchState = new SearchState();
			searchState.setParseState(parseState);
			
			//intentional null for Searcher<Map<Integer, Integer>> 
			thmIndexList = ContextSearch.contextSearchMap(str, thmIndexList, 
					null, searchState);
			Map<Integer, Integer> curContextScoreMap = searchState.contextVecScoreMap();
			
			//don't need below, Nov 24, delete soon
			/*TreeMap<Integer, List<Integer>> contextScoreIndexTMap = searchState.contextScoreIndexTMap();
			Iterator<Map.Entry<Integer, List<Integer>>> contextTMapIter = contextScoreIndexTMap.entrySet().iterator();			
			while(contextTMapIter.hasNext()) {
				Map.Entry<Integer, List<Integer>> entry = contextTMapIter.next();
				int score = entry.getKey();
				if(score == 0) {
					intersectionSortedList.addAll(entry.getValue());
					break;
				}
				contextSortedList.addAll(entry.getValue());
			}*/
			//some thms don't parse to produce meaningful context vecs
			if(null != curContextScoreMap) {
				for(Map.Entry<Integer, Integer> entry : curContextScoreMap.entrySet()) {
					int curThmIndex = entry.getKey();
					//note this double-counts relations that occur in different pieces
					Integer curScore = contextScoreMap.get(curThmIndex);
					if(null == curScore) {
						contextScoreMap.put(curThmIndex, entry.getValue());
					}else {
						contextScoreMap.put(curThmIndex, entry.getValue() + curScore);			
					}
				}
			}
			//keep the ones with high context vec score, else sort the others according to
			//intersection scores.			
			
			//List<ThmHypPair> thmHypPairList = SearchCombined.thmListIndexToThmHypPair(thmIndexList);
			//System.out.println("FOR PIECE " + str + ":\n thmHypPairList: "+thmHypPairList);			
		}
		//list sorted according to context vecs
		List<Integer> contextSortedList = new ArrayList<Integer>();
		//remaining ones sorted according to intersection scores.
		List<Integer> intersectionSortedList = new ArrayList<Integer>();
		
		TreeMap<Integer, List<Integer>> contextScoreThmTMap 
			= new TreeMap<Integer, List<Integer>>(new thmp.utils.DataUtility.ReverseIntComparator());
		
		contextScoreMap.remove(thmIndex);
		/*Combine maps, prune away ones with low intersection scores */
		for(Map.Entry<Integer, Integer> entry : contextScoreMap.entrySet()) {			
			int curIndex = entry.getKey();
			int score = entry.getValue();			
			//note this double-counts relations that occur in different pieces
			List<Integer> thmIndexList = contextScoreThmTMap.get(score);
			if(null == thmIndexList) {
				thmIndexList = new ArrayList<Integer>();				
				contextScoreThmTMap.put(score, thmIndexList);
			}
			thmIndexList.add(curIndex);			
		}	
		
		for(Map.Entry<Integer, List<Integer>> entry : contextScoreThmTMap.entrySet()) {			
			int score = entry.getKey();
			if(score == 0) {
				intersectionSortedList.addAll(entry.getValue());
				break;
			}
			contextSortedList.addAll(entry.getValue());
		}
		Collections.sort(intersectionSortedList, new thmp.utils.DataUtility.IntMapComparator(thmScoreMap));
		
		List<Integer> combinedList = new ArrayList<Integer>();
		combinedList.addAll(contextSortedList);
		combinedList.addAll(intersectionSortedList);
		
		//prune away intersectionList ends!!
		int maxSimilarThmCount = SimilarThmUtils.maxSimilarThmListLen();
		if(combinedList.size() > maxSimilarThmCount) {
			List<Integer> tempList = new ArrayList<Integer>();
			for(int i = 0; i < maxSimilarThmCount; i++) {
				tempList.add(combinedList.get(i));
			}
			combinedList = tempList;
		}
		
		if(DEBUG) {
			List<ThmHypPair> thmHypPairList = SearchCombined.thmListIndexToThmHypPair(combinedList);
			System.out.println("SIMILAR SEARCH RESULTS: for thm: " + thmStr
					+ "\n thmHypPairList: ");
			int counter = 0;
			for(ThmHypPair thm : thmHypPairList) {
				System.out.println("Thm "+ counter + ". " + thm);
				counter++;
			}
		}
		return combinedList;
	}
	
	private static void pruneLowScoreThms() {
		
	}
	
	/**
	 * Find theorems that contain the relevant words in input str.
	 * Return list of ones with good words match.
	 * @param str Typically a theorem segment.
	 * @return
	 */
	private static List<Integer> gatherThmFromWords(String str, Map<Integer, Integer> allThmScoreMap) {
		
		//List<String> thmWordsList = WordForms.splitThmIntoSearchWordsList(str);
		Set<String> searchWordsSet = new HashSet<String>();
		
		SearchStateBuilder searchStateBuilder = new SearchStateBuilder();
		searchStateBuilder.disableLiteralSearch();
		SearchState searchState = searchStateBuilder.build();
		
		boolean contextSearchBool = false; 
		
		boolean searchRelationalBool = false;
		//improve intersection search to return more pertinent results!!
		searchState = SearchIntersection.intersectionSearch(str, searchWordsSet, searchState, contextSearchBool, 
				searchRelationalBool, numHighestResults/2);
		
		List<Integer> thmList = searchState.intersectionVecList();	
		
		Map<Integer, Integer> scoreMap = searchState.thmScoreMap();
		for(Map.Entry<Integer, Integer> entry : scoreMap.entrySet()) {
			int index = entry.getKey();
			Integer curScore = allThmScoreMap.get(index);
			if(null == curScore) {
				allThmScoreMap.put(index, entry.getValue());
			}else {
				allThmScoreMap.put(index, curScore + entry.getValue());
			}
		}
		
		return thmList;
	}
	
	/**
	 * 
	 * 
	 * Emphasize on conclusions and properties, over defining statements
	 * e.g. "let A be ...". Try substituting definitions in, for context 
	 * searcher to better detect relations. E.g. then $s_i$ converges goes
	 * to "sequence $s_i$ converges".
	 * And try breacking compound sentences up into several pieces.
	 * @param thm
	 * @return
	 */
	private static String chopThmStr(String thm, ParseState parseState){
		//to be expanded!
		
		return DetectHypothesis.replaceSymbols(thm, parseState);		
	}	
	
	public static void main(String[] args) {
		//experiment!!
		Scanner sc = new Scanner(System.in);
		
		while(sc.hasNext()) {
			String index = WordForms.stripSurroundingWhiteSpace(sc.nextLine());
			if(!WordForms.DIGIT_PATTERN.matcher(index).matches()) {
				System.out.println("Enter an integer index for a thm!");
				continue;
			}
			preComputeSimilarThm(Integer.parseInt(index));
			System.out.println("Enter new thm index:");
		}
		
		sc.close();
	}
}
