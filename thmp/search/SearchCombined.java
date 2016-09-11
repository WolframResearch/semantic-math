package thmp.search;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import com.wolfram.jlink.Expr;
import com.wolfram.jlink.MathLinkException;

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

	
	public static void initializeSearchWithResource(BufferedReader freqWordsFileBuffer, BufferedReader texSourceFileBuffer){
		CollectFreqWords.setResources(freqWordsFileBuffer);
		CollectThm.setResources(texSourceFileBuffer);		
	}
	
	/**
	 * SearchIntersection interface.
	 * Searches intersection. Should run after SVD. 
	 * Takes the result nearest vectors from SVD and combines
	 * picks out the highest-scoring ones based on intersection.
	 */
	/*private static void searchIntersection(){
		
	} */
	
	/**
	 * Gets highest scored vectors that are the common to both lists.
	 * Can weigh the two lists differently.
	 * nearestVecList is 1-based, but intersectionVecList is 0-based!
	 * @param nearestVecList
	 * @param intersectionVecList
	 * @param numVectors is the total number of vectors to get 
	 * @return
	 */
	private static List<Integer> findListsIntersection(List<Integer> nearestVecList, List<Integer> intersectionVecList, 
			int numVectors){
		List<Integer> bestCommonVecList = new ArrayList<Integer>();
		//map to keep track of scores in first list
		Map<Integer, Integer> nearestVecListPositionsMap = new HashMap<Integer, Integer>();
		//use TreeMultimap to keep track of total score
		//keys are scores, and values are indices of thms that have that score
		Multimap<Integer, Integer> scoreThmTreeMMap = TreeMultimap.create();
		int nearestVecListSz = nearestVecList.size();
		int intersectionVecListSz = intersectionVecList.size();
		
		for(int i = 0; i < nearestVecList.size(); i++){
			int thmIndex = nearestVecList.get(i);
			nearestVecListPositionsMap.put(thmIndex, i);
		}
		
		int maxScore = 0;
		//should iterate to include more pairs! 
		for(int i = 0; i < intersectionVecList.size(); i++){
			//index of thm
			int intersectionThm = intersectionVecList.get(i);
			//intersection list is 0-based!
			Integer nearestListThmIndex = nearestVecListPositionsMap.remove(intersectionThm+1);
			if(nearestListThmIndex != null){
				int score = i+nearestListThmIndex;
				if(score > maxScore) maxScore = score;
				scoreThmTreeMMap.put(score, intersectionThm+1);
			}//else add the length of nearestVecList to the score
			else{
				int score = i+nearestVecListSz;
				if(score > maxScore) maxScore = score;
				scoreThmTreeMMap.put(score, intersectionThm+1);
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
		for(Integer thmIndex : scoreThmTreeMMap.values()){
			if(counter == 0) break;			
			bestCommonVecList.add(thmIndex);	
			counter--;
		}
		
		return bestCommonVecList;
	}
	
	/**
	 * Search interface to be called externally, eg from servlet.
	 * Resources should have been set prior to this if called externally.
	 * @param inputStr search input
	 */
	public static List<String> searchCombined(String input){
		if(input.matches("\\s*")) return null;
		
		List<Integer> nearestVecList = ThmSearch.readThmInput(input, NUM_NEAREST);
		if(nearestVecList.isEmpty()){
			System.out.println("I've got nothing for you yet. Try again.");
			return null;
		}
		List<Integer> intersectionVecList = SearchIntersection.getHighestThm(input, NUM_NEAREST);
		int numCommonVecs = NUM_COMMON_VECS;
		
		String firstWord = input.split("\\s+")[0];
		if(firstWord.matches("\\d+")){
			numCommonVecs = Integer.parseInt(firstWord);			
		}		
		//find best intersection of these two lists. nearestVecList is 1-based, but intersectionVecList is 0-based! 
		List<Integer> bestCommonVecs = findListsIntersection(nearestVecList, intersectionVecList, numCommonVecs);
		List<String> bestCommonThms = new ArrayList<String>();
		for(int d : bestCommonVecs){
			String thm = TriggerMathThm2.getThm(d);
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
			
			List<Integer> nearestVecList = ThmSearch.readThmInput(thm, NUM_NEAREST);
			if(nearestVecList.isEmpty()){
				System.out.println("I've got nothing for you yet. Try again.");
				continue;
			}
			List<Integer> intersectionVecList = SearchIntersection.getHighestThm(thm, NUM_NEAREST);
			int numCommonVecs = NUM_COMMON_VECS;
			
			String firstWord = thm.split("\\s+")[0];
			if(firstWord.matches("\\d+")){
				numCommonVecs = Integer.parseInt(firstWord);			
			}
			//find best intersection of these two lists. nearestVecList is 1-based, but intersectionVecList is 0-based! 
			List<Integer> bestCommonVecs = findListsIntersection(nearestVecList, intersectionVecList, numCommonVecs);
			for(int d : bestCommonVecs){
				System.out.println(TriggerMathThm2.getThm(d));
			}
		}
		
		sc.close();
	}
}
