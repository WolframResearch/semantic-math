package thmp.search;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

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
import thmp.search.Searcher.QueryVecContainer;
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
	
	private static final Pattern HYP_PATTERN = Pattern.compile("(?:assume|denote|define|let|is said|suppose"
			+ "|is called|if|given)");
	
	/**
	 * Finds index list of similar theorems by retrieving precomputed
	 * indices from database. Used at app runtime.
	 * @param thmIndex
	 * @return
	 */
	public static List<Integer> getSimilarThms(int thmIndex){
		
		Connection conn = DBUtils.getPooledConnection();
		if(null == conn) {
			return Collections.emptyList();
		}
		
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
		//String thmStr = thmHypPair.getEntireThmStr();
		
		String thmStr = thmHypPair.thmStr();
		if(DEBUG) System.out.println("QUERY THM: " + thmHypPair.getEntireThmStr());
		
		List<Integer> combinedList = new ArrayList<Integer>();		
		getSimilarComponent(thmIndex, thmStr, combinedList);
		
		thmStr = thmHypPair.hypStr();
		getSimilarComponent(thmIndex, thmStr, combinedList);
		
		//prune away lesser ranked intersectionList elements
		int maxSimilarThmCount = SimilarThmUtils.maxSimilarThmListLen();
		if(combinedList.size() > maxSimilarThmCount) {
			List<Integer> tempList = new ArrayList<Integer>();
			for(int i = 0; i < maxSimilarThmCount; i++) {
				tempList.add(combinedList.get(i));
			}
			combinedList = tempList;
		}
		return combinedList;
	}

	/**
	 * Similar thms for components, e.g. just statements, or just hypothesis
	 * @param thmIndex
	 * @param thmStr
	 * @return
	 */
	private static void getSimilarComponent(int thmIndex, String thmStr,
			List<Integer> combinedList) {
		//keep a running parse state to collect variable definitions.
		ParseStateBuilder parseStateBuilder = new ParseStateBuilder();
		ParseState parseState = parseStateBuilder.build();
		/*keep track of thm scores. Key is thmIndex, value is intersection search score*/
		Map<Integer, Integer> thmScoreMap = new HashMap<Integer, Integer>();
		
		//separate map into two parts, for hyp and conclusions
		Map<Integer, Integer> stmContextScoreMap = new HashMap<Integer, Integer>();		
		Map<Integer, Integer> hypContextScoreMap = new HashMap<Integer, Integer>();
		
		/* Divides thm into pieces, i.e. logical components, e.g.
		 * "if ... ", and "then ..." */
		String[] thmPieces = ThmP1.preprocess(thmStr);
		
		boolean isVerbose = DEBUG;
		
		/*deliberately don't clean up parseState inbetween thmPieces, to preserve state.*/
		for(String str : thmPieces) {
			int strLen = str.length();
			if(strLen < 10) {
				continue;
			}
			//divide thm string into several parts, to target search results specifically
			str = chopThmStr(str, parseState);
			//must parse current str *after* symbol replacement, to not replace def in current str.
			ParseRun.parseInput(str, parseState, isVerbose);
			
			if(parseState.curParseExcessiveLatex()) {
				parseState.setCurParseExcessiveLatex(false);
				continue;
			}
			
			//weigh depending on hyp or stm, prioritize stm
			//result of intersection search
			List<Integer> thmIndexList = new ArrayList<Integer>();
			/*keep track of thm scores. Key is thmIndex, value is its span length*/
			Map<Integer, Integer> thmSpanMap = new HashMap<Integer, Integer>();
			//max span amongst any thms amongst returned results.
			int maxThmSpan = gatherThmFromWords(str, thmIndexList, thmScoreMap, thmSpanMap);	
			
			SearchState searchState = new SearchState();
			searchState.setParseState(parseState);
			
			//intentional null for Searcher<Map<Integer, Integer>> 
			//but null forces it to parse again!!
			/*Searcher<Map<Integer, Integer>> searcher;
			searcher.setSearcherState(new QueryVecContainer<Map<Integer, Integer>>(queryContextVecMap));			
			ContextSearch.contextSearchMap(str, thmIndexList, 
					null, searchState);*/
			
			Map<Integer, Integer> contextVecMap = parseState.getCurThmCombinedContextVecMap();
			if(null != contextVecMap) {
				
				Map<Integer, Integer> contextVecScoreMap = new HashMap<Integer, Integer>();
	
				ContextSearch.computeContextVecScoreMap(thmIndexList,
						thmIndexList.size(), contextVecMap, contextVecScoreMap);
				
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
				
				Map<Integer, Integer> contextScoreMap = ifLowerPriorityMap(str) 
						? hypContextScoreMap : stmContextScoreMap;
				
				//some thms don't parse to produce meaningful context vecs
				
				for(Map.Entry<Integer, Integer> entry : contextVecScoreMap.entrySet()) {
						int curThmIndex = entry.getKey();
						Map<Integer, Integer> tempMap = contextScoreMap;
						//experiment and pull out this constant!
						if(thmSpanMap.get(curThmIndex) <= maxThmSpan * .34) {
							tempMap = hypContextScoreMap;
						}
						//note this double-counts relations that occur in different pieces
						Integer curScore = tempMap.get(curThmIndex);
						if(null == curScore) {
							tempMap.put(curThmIndex, entry.getValue());
						}else {
							tempMap.put(curThmIndex, entry.getValue() + curScore);			
						}
				}
			}
			//keep the ones with high context vec score, else sort the others according to
			//intersection scores.							
		}
		//list of holdover thms with lower priority.
		//List<Integer> holdOverList = new ArrayList<Integer>();
		
		addSimilarComponentsToMaps(combinedList, thmIndex, thmScoreMap, stmContextScoreMap);
		addSimilarComponentsToMaps(combinedList, thmIndex, thmScoreMap, hypContextScoreMap);
		
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
	}

	/**
	 * Adds similar thms to each component, eg hyp or conclusion. Adds to supplied combined list.
	 * @param queryThmIndex
	 * @param thmScoreMap
	 * @param contextScoreMap
	 * @return
	 */
	private static void addSimilarComponentsToMaps(List<Integer> combinedList, int queryThmIndex, 
			Map<Integer, Integer> thmScoreMap, Map<Integer, Integer> contextScoreMap) {
		//list sorted according to context vecs
		List<Integer> contextSortedList = new ArrayList<Integer>();
		//remaining ones sorted according to intersection scores.
		//List<Integer> intersectionSortedList = new ArrayList<Integer>();

		contextScoreMap.remove(queryThmIndex);
		
		contextSortedList.addAll(contextScoreMap.keySet());
		
		TreeMap<Integer, List<Integer>> contextScoreThmTMap 
			= new TreeMap<Integer, List<Integer>>(new thmp.utils.DataUtility.ReverseIntComparator());
		
		//take out list if 
		//Combine maps, prune away ones with low intersection scores 
		/*for(Map.Entry<Integer, Integer> entry : contextScoreMap.entrySet()) {			
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
			List<Integer> contextList = entry.getValue();			
			contextSortedList.addAll(contextList);
		}*/
		
		int contextScoreTotal = 0;
		int intersectionScoreTotal = 0;
		
		for(Map.Entry<Integer, Integer> entry : contextScoreMap.entrySet()) {
			int index = entry.getKey();
			int contextScore = entry.getValue();
			
			//contextScore can be 0 easily, but intersectionScore is never 0, since the results all contained
			//some word in query.
			if(contextScore > 0) {
				contextScoreTotal += contextScore;
				Integer intersectionScore = thmScoreMap.get(index);
				intersectionScoreTotal += null == intersectionScore ? 0 : intersectionScore;
			}			
		}
		
		final double thmScoreMapWeight;
		if(intersectionScoreTotal == 0) {
			thmScoreMapWeight = 1;
		}else {
			//0.92 instead of 1 to prioritize context
			thmScoreMapWeight = 0.95*((double)contextScoreTotal) / intersectionScoreTotal;
		}
		
		Collections.sort(contextSortedList, new thmp.utils.DataUtility.IntMapComparator(contextScoreMap, thmScoreMap,
				thmScoreMapWeight));
		
		combinedList.addAll(contextSortedList);
	}
	
	/**
	 * Whether the inputStr is a hypothesis. By checking whether the input 
	 * contains any assumption-indicating words.
	 * @param inputStr
	 * @return
	 */
	private static boolean ifLowerPriorityMap(String inputStr){
		String inputLower = inputStr.toLowerCase();
		int strLen = inputStr.length();
		if( strLen < 30) {
			return true;
		}
		if(strLen < 45 && !inputLower.contains(" then ") 
				&& HYP_PATTERN.matcher(inputLower).find()){
			return true;
		}
		return false;
	}
	
	private static void pruneLowScoreThms() {
		
	}
	
	/**
	 * Find theorems that contain the relevant words in input str.
	 * Return list of ones with good words match.
	 * @param str Typically a theorem segment.
	 * @param intersectionResultsList results of intersection search.
	 * @param thmSpanMap Span map of indices and their word spans in the theorems.
	 * @return maximum span amongst search results.
	 */
	private static int gatherThmFromWords(String str, List<Integer> intersectionResultsList,
			Map<Integer, Integer> allThmScoreMap, Map<Integer, Integer> thmSpanMap) {
		
		//List<String> thmWordsList = WordForms.splitThmIntoSearchWordsList(str);
		Set<String> searchWordsSet = new HashSet<String>();
		
		SearchStateBuilder searchStateBuilder = new SearchStateBuilder();
		searchStateBuilder.disableLiteralSearch();
		SearchState searchState = searchStateBuilder.build();
		
		boolean contextSearchBool = false;		
		boolean searchRelationalBool = false;
		//improve intersection search to return more pertinent results!!
		SearchIntersection.intersectionSearch(str, searchWordsSet, searchState, contextSearchBool, 
				searchRelationalBool, numHighestResults/2);
		
		List<Integer> intersectionList = searchState.intersectionVecList();
		
		if(null == intersectionList) {
			return 0;
		}
		
		intersectionResultsList.addAll(intersectionList);			
		thmSpanMap.putAll(searchState.thmSpanMap());
		
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
		
		return searchState.largestWordSpan();
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
