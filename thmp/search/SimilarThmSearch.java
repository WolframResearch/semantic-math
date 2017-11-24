package thmp.search;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import thmp.parse.DetectHypothesis;
import thmp.parse.ParseRun;
import thmp.parse.ParseState;
import thmp.parse.ThmP1;
import thmp.parse.ParseState.ParseStateBuilder;
import thmp.search.SearchCombined.ThmHypPair;
import thmp.search.SearchState.SearchStateBuilder;
import thmp.utils.WordForms;

public class SimilarThmSearch {

	private static final int numHighestResults = 50;
	
	public static void findSimilarThm(int thmIndex) {
		
		/*
		 * Call contextSearchMap(String query, List<Integer> nearestThmIndexList, 
			Searcher<Map<Integer, Integer>> searcher, SearchState searchState)
			to get ranking.
		 */
		
		ThmHypPair thmHypPair = ThmHypPairGet.retrieveThmHypPairWithThm(thmIndex);
		String thmStr = thmHypPair.getEntireThmStr();
		System.out.println("QUERY THM: " + thmStr);
		//keep a running parse state to collect variable definitions.
		ParseStateBuilder parseStateBuilder = new ParseStateBuilder();
		ParseState parseState = parseStateBuilder.build();
		
		String[] thmPieces = ThmP1.preprocess(thmStr);
		boolean isVerbose = true;
		for(String str : thmPieces) {
			//divide thm string into several parts, to target search results specifically
			str = chopThmStr(str, parseState);
			//must parse current str *after* symbol replacement, to not replace def in current str.
			ParseRun.parseInput(str, parseState, isVerbose);
			
			List<Integer> thmIndexList = gatherThmFromWords(str);
			 
			SearchState searchState = new SearchState();		
			searchState.setParseState(parseState);
			//intentional null for Searcher<Map<Integer, Integer>> 
			thmIndexList = ContextSearch.contextSearchMap(str, thmIndexList, 
					null, searchState);
			
			//need to return score of match!
			List<ThmHypPair> thmHypPairList = SearchCombined.thmListIndexToThmHypPair(thmIndexList);
			System.out.println("FOR PIECE " + str + ":\n thmHypPairList: "+thmHypPairList);
			
		}
		
	}	
	
	/**
	 * Find theorems that contain the relevant words in input str.
	 * Return list of ones with good words match.
	 * @param str Typically a theorem segment.
	 * @return
	 */
	private static List<Integer> gatherThmFromWords(String str) {
		
		//List<Integer> thmList = new ArrayList<Integer>();
		
		//List<String> thmWordsList = WordForms.splitThmIntoSearchWordsList(str);
		Set<String> searchWordsSet = new HashSet<String>();
		
		SearchStateBuilder searchStateBuilder = new SearchStateBuilder();
		searchStateBuilder.disableLiteralSearch();
		SearchState searchState = searchStateBuilder.build();
		
		boolean contextSearchBool = false; 
		boolean searchRelationalBool = false;
		
		searchState = SearchIntersection.intersectionSearch(str, searchWordsSet, searchState, contextSearchBool, 
				searchRelationalBool, numHighestResults);
		
		return searchState.intersectionVecList();
	}
	/**
	 * Divides thm into pieces, each with.
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
			findSimilarThm(Integer.parseInt(index));
			System.out.println("Enter new thm index:");
		}
		
		sc.close();
	}
}
