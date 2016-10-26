package thmp.search;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import com.wolfram.jlink.Expr;
import com.wolfram.jlink.MathLinkException;

import thmp.ProcessInput;

/**
 * Search that combines svd/nearest and intersection.
 * @author yihed
 *
 */
public class SearchCombined {

	private static final int NUM_NEAREST = 10;
	//combined number of vectors to take from search results of
	//svd/nearest and intersection
	private static final int NUM_COMMON_VECS = 4;
	//should update at the very beginning!
	//private static final int LIST_INDEX_SHIFT = 1;
	
	public static void initializeSearchWithResource(BufferedReader freqWordsFileBuffer, BufferedReader texSourceFileBuffer){		
		CollectThm.setWordFrequencyBR(freqWordsFileBuffer);
		CollectThm.setResources(texSourceFileBuffer);		
	}
	
	/**
	 * Set resources for list of resource files.
	 * @param freqWordsFileBuffer
	 * @param texSourceFileBuffer
	 * @param macrosReader
	 */
	public static void initializeSearchWithResource(BufferedReader freqWordsFileBuffer, List<BufferedReader> texSourceFileBuffer,
			BufferedReader macrosReader){
		//CollectFreqWords.setResources(freqWordsFileBuffer);
		CollectThm.setWordFrequencyBR(freqWordsFileBuffer);
		CollectThm.setResources(texSourceFileBuffer, macrosReader);	
		ProcessInput.setResources(macrosReader);
	}
	
	/**
	 * Gets highest scored vectors that are the common to both lists.
	 * Can weigh the two lists differently.
	 * nearestVecList is 1-based, but intersectionVecList is 0-based!
	 * @param nearestVecList
	 * @param intersectionVecList
	 * @param numVectors is the total number of vectors to get 
	 * @return
	 */
	private static List<Integer> findListsIntersection(List<Integer> nearestVecList, SearchState searchState, 
			int numVectors, String input, boolean searchContextBool){
		
		List<Integer> intersectionVecList = searchState.intersectionVecList();
		//short-circuit
		if(intersectionVecList.isEmpty()){
			return nearestVecList;
		}
		int intersectionVecListSz = intersectionVecList.size();
		
		Map<Integer, Integer> thmScoreMap = searchState.thmScoreMap();
		List<Integer> bestCommonVecList = new ArrayList<Integer>();
		//map to keep track of scores in first list
		Map<Integer, Integer> nearestVecListPositionsMap = new HashMap<Integer, Integer>();
		//use TreeMultimap to keep track of total score
		//keys are scores, and values are indices of thms that have that score
		Multimap<Integer, Integer> scoreThmTreeMMap = TreeMultimap.create();
		int nearestVecListSz = nearestVecList.size();
		Map<Integer, Integer> thmSpanMap = searchState.thmSpanMap();
		
		for(int i = 0; i < nearestVecList.size(); i++){
			int thmIndex = nearestVecList.get(i);
			nearestVecListPositionsMap.put(thmIndex, i);
		}
		
		int totalWordAdded = searchState.totalWordAdded();
		//avoid magic numbers
		int threshold = totalWordAdded < 3 ? totalWordAdded : (totalWordAdded < 6 ? totalWordAdded-1 
				: totalWordAdded - totalWordAdded/3);
		int maxScore = 0;
		//should iterate to include more pairs! 
		for(int i = 0; i < intersectionVecListSz; i++){
			//index of thm
			int intersectionThm = intersectionVecList.get(i);
			//intersection list is 0-based!
			Integer nearestListThmIndex = nearestVecListPositionsMap.remove(intersectionThm);
			//first check if spanning is good, if spanning above a threshold, say contains more than
			//(total #relevant words) - 2, threshold determined by relative size
			//then don't need to be contained in nearestVecList	
			if(thmSpanMap.get(intersectionThm) >= threshold){
				int score = i;
				if(score > maxScore) maxScore = score;
				scoreThmTreeMMap.put(score, intersectionThm);
			}			
			else if(nearestListThmIndex != null){
				int score = i+nearestListThmIndex;
				if(score > maxScore) maxScore = score;
				scoreThmTreeMMap.put(score, intersectionThm);
			}//else add the length of nearestVecList to the score
			else{
				int score = i+nearestVecListSz;
				if(score > maxScore) maxScore = score;
				scoreThmTreeMMap.put(score, intersectionThm);
			}			
		}
		
		//now add the vecs from nearestVecList that haven't been added to scoreThmTreeMMap
		for(Map.Entry<Integer, Integer> entry : nearestVecListPositionsMap.entrySet()){
					
			int thm = entry.getKey();
			int nearestListThmIndex = entry.getValue();
			//prioritize intersectionList because the results there are more precise. So
			//all added lists there have guaranteed spot
			//int score = Math.max(maxScore, nearestListThmIndex + intersectionVecListSz);
			scoreThmTreeMMap.put(nearestListThmIndex + intersectionVecListSz, thm);
		}
		
		//System.out.println("values size " + scoreThmTreeMMap.values().size());
		//pick out top vecs
		int counter = numVectors;
		Iterator<Integer> scoreThmTreeMMapValIter = scoreThmTreeMMap.values().iterator();
		//take first one from intersectionList if span for top result is not ideal,
		//and score gap not too large
		Integer topThmIndex = scoreThmTreeMMapValIter.next();
		
		int topIntersectionThmIndex = intersectionVecList.get(0);
		int topIntersectionThmScore = thmScoreMap.get(topIntersectionThmIndex);
		int topQueryIndex;
		//intersectionVecList guranteed not empty at this point. Remove magic numbers.
		//thmSpanMap.get(topThmIndex) < thmSpanMap.get(topIntersectionThmIndex)*4.0/5
		//System.out.println("topThmIndex: " + topThmIndex + " thmScoreMap: " + thmScoreMap);
		
		//adjust top search result
		if( (!intersectionVecList.contains(topThmIndex)
				//make the 4.0/5 into constant after done with tinkering
				|| thmSpanMap.get(topThmIndex) < thmSpanMap.get(topIntersectionThmIndex)*4.0/5)
				&& (!thmScoreMap.containsKey(topThmIndex) || thmScoreMap.get(topThmIndex) < topIntersectionThmScore)){
			bestCommonVecList.add(topIntersectionThmIndex);
			topQueryIndex = topIntersectionThmIndex;
		}else{
			bestCommonVecList.add(topThmIndex);		
			topQueryIndex = topThmIndex;
		}		
		counter--;
		
		while(scoreThmTreeMMapValIter.hasNext()){
			int thmIndex = scoreThmTreeMMapValIter.next();
			if(counter == 0 || thmIndex == topQueryIndex) break;			
			bestCommonVecList.add(thmIndex);	
			counter--;
		}
		
		if(searchContextBool){
			bestCommonVecList = ContextSearch.contextSearch(input, bestCommonVecList);
		}
		return bestCommonVecList;
	}
	
	/**
	 * Search interface to be called externally, eg from servlet.
	 * Resources should have been set prior to this if called externally.
	 * @param inputStr search input
	 */
	public static List<String> searchCombined(String input, boolean searchContextBool){
		if(input.matches("\\s*")) return null;
		
		input = input.toLowerCase();
		
		List<Integer> nearestVecList = ThmSearch.readThmInput(input, NUM_NEAREST);
		if(nearestVecList.isEmpty()){
			System.out.println("I've got nothing for you yet. Try again.");
			return null;
		}
		
		SearchState searchState = SearchIntersection.getHighestThm(input, NUM_NEAREST);
		//List<Integer> intersectionVecList;
		int numCommonVecs = NUM_COMMON_VECS;
		
		String firstWord = input.split("\\s+")[0];
		if(firstWord.matches("\\d+")){
			numCommonVecs = Integer.parseInt(firstWord);			
		}		
		//find best intersection of these two lists. nearestVecList is 1-based, but intersectionVecList is 0-based! 
		List<Integer> bestCommonVecs = findListsIntersection(nearestVecList, searchState, 
				numCommonVecs, input, searchContextBool);
		List<String> bestCommonThms = new ArrayList<String>();
		for(int d : bestCommonVecs){
			//String thm = TriggerMathThm2.getThm(d);
			String thm = TriggerMathThm2.getWebDisplayThm(d);
			System.out.println(thm);
			bestCommonThms.add(thm);
		}
		return bestCommonThms;
	}
	
	/**
	 * Nested class for Gson processing.
	 * Contains thm, and keywords for highlighting.
	 */
	/*public static class SoughtPair{
		private String thm;
		private List<String> keywords;
		
		public SoughtPair(){
			
		}
	}*/
	
	/**
	 * Search that invokes different layers
	 * @param args
	 */
	public static void main(String[] args){
		
		//load the necessary classes so the first call doesn't take 
		//disproportionately more time
		Scanner sc = new Scanner(System.in);
		
		while(sc.hasNextLine()){
			String thm = sc.nextLine();
			if(thm.matches("\\s*")) continue;
			
			thm = thm.toLowerCase();
			
			List<Integer> nearestVecList = ThmSearch.readThmInput(thm, NUM_NEAREST);
			if(nearestVecList.isEmpty()){
				System.out.println("I've got nothing for you yet. Try again.");
				continue;
			}
			//filter nearestVecList through context search, to re-arrange list output based on context
			String[] thmAr = thm.split("\\s+");
			if(thmAr.length > 1 && thmAr[0].equals("context")){
				nearestVecList = ContextSearch.contextSearch(thm, nearestVecList);
			}
			//need to run GenerateContext.java to generate the context vectors during build, but ensure 
			//context vectors are up to date!
			SearchState searchState = SearchIntersection.getHighestThm(thm, NUM_NEAREST);
			int numCommonVecs = NUM_COMMON_VECS;
			
			String firstWord = thm.split("\\s+")[0];
			if(firstWord.matches("\\d+")){
				numCommonVecs = Integer.parseInt(firstWord);
			}
			boolean searchContextBool = false;
			if(thmAr.length > 1 && thmAr[0].equals("context")){				
				searchContextBool = true;
			}
			//find best intersection of these two lists. nearestVecList is 1-based, but intersectionVecList is 0-based! 
			//now both are 0-based.
			List<Integer> bestCommonVecs = findListsIntersection(nearestVecList, searchState, numCommonVecs,
					thm, searchContextBool);
			
			for(int d : bestCommonVecs){
				System.out.println(TriggerMathThm2.getThm(d));
			}
		}
		
		sc.close();
	}
}
