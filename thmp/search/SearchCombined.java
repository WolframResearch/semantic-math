package thmp.search;

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
	
	/**
	 * SVD/Nearest interface.
	 */
	private static void searchSVD(){
		
	}
	
	/**
	 * SearchIntersection interface.
	 * Searches intersection. Should run after SVD. 
	 * Takes the result nearest vectors from SVD and combines
	 * picks out the highest-scoring ones based on intersection.
	 */
	private static void searchIntersection(){
		
	}
	
	/**
	 * Gets highest scored vectors that are the common to both lists.
	 * Can weigh the two lists differently
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
			Integer nearestListThmIndex = nearestVecListPositionsMap.remove(intersectionThm);
			if(nearestListThmIndex != null){
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
		for(Integer thmIndex : scoreThmTreeMMap.values()){
			if(counter == 0) break;			
			bestCommonVecList.add(thmIndex);	
			counter--;
		}
		
		return bestCommonVecList;
	}
	
	/**
	 * Search that invokes different layers
	 * @param args
	 */
	public static void main(String[] args){
		
		Scanner sc = new Scanner(System.in);
		
		while(sc.hasNextLine()){
			String thm = sc.nextLine();
			
			List<Integer> nearestVecList = ThmSearch.readThmInput(thm, NUM_NEAREST);
			List<Integer> intersectionVecList = SearchIntersection.getHighestThm(thm, NUM_NEAREST);
			//find best intersection of these two lists.
			List<Integer> bestCommonVecs = findListsIntersection(nearestVecList, intersectionVecList, NUM_COMMON_VECS);
			for(int d : bestCommonVecs){
				System.out.println(TriggerMathThm2.getThm(d));
			}
		}
		
		sc.close();
	}
}
