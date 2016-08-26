package thmp.search;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Scanner;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.TreeMultimap;

/**
 * Searches by finding intersections in theorems that contain given keywords.
 * 
 * @author yihed
 */
public class SearchIntersection {

	//delimiters to split on when making words out of input
	private static final String SPLIT_DELIM = "\\s+|\'|\\(|\\)|\\{|\\}|\\[|\\]|\\.|\\;|\\,|:";
	/**
	 * Map of keywords and their scores in document, the higher freq in doc, the lower 
	 * score, say 1/(log freq + 1) since log 1 = 0. 
	 */
	private static final ImmutableMap<String, Double> wordsScoreMap;	
	
	/**
	 * List of theorems.
	 */
	private static final ImmutableList<String> thmList;
	
	/**
	 * Multimap of words, and the theorems (their indices) in thmList, the word shows up in.
	 */
	private static final ImmutableMultimap<String, Integer> wordThmMMap;
	
	/**
	 * Static initializer, builds the maps using CollectThm.java. 
	 */
	static{
		thmList = CollectThm.get_thmList();
		//builds scoresMap based on frequency map obtained from CollectThm.
		ImmutableMap.Builder<String, Double> wordsScoreMapBuilder = ImmutableMap.builder();		
		buildScoreMap(wordsScoreMapBuilder);
		wordsScoreMap = wordsScoreMapBuilder.build();
		wordThmMMap = CollectThm.get_wordThmsMMap();
	}	
	/**
	 * Fills up wordsScoreMapBuilder
	 * @param wordsScoreMapBuilder
	 */
	private static void buildScoreMap(ImmutableMap.Builder<String, Double> wordsScoreMapBuilder){
		Map<String, Integer> wordFreqMap = CollectThm.get_docWordsFreqMap();
		for(Entry<String, Integer> entry : wordFreqMap.entrySet()){
			//truncate 1/... !
			
			//wordsScoreMapBuilder.put(entry.getKey(), 1/(Math.log(entry.getValue() + 1)));
			wordsScoreMapBuilder.put(entry.getKey(), 1/Math.log(entry.getValue()+1)*10 );
		}
	}
		
	/**
	 * Builds scoreThmMMap 
	 * @param input input String
	 * @param numHighest number of highest-scored thms to retrieve
	 */
	public static List<Integer> getHighestThm(String input, int numHighest){
		//make input list of words
		String[] inputAr = input.toLowerCase().split(SPLIT_DELIM);
		
		/*
		 * Map of theorems, in particular their indices in thmList, and the scores corresponding to the keywords they contain.
		 * The rarer a keyword is in the doc, the higher its score is.
		 */
		Map<Integer, Double> thmScoreMap = new HashMap<Integer, Double>(); 
		
		/*
		 * Multimap of double and ints, where double is score, and the Integers
		 * are the indices of the thms having this score.
		 */
		TreeMultimap<Double, Integer> scoreThmMMap = TreeMultimap.create();
		
		for(String word : inputAr){
			
			//update scores map
			if(wordsScoreMap.containsKey(word)){
				//for every word, get list of thms containing this word
				Collection<Integer> wordThms = wordThmMMap.get(word);
				Double wordScore = wordsScoreMap.get(word);
				for(Integer thmIndex : wordThms){
					Double newScore = wordsScoreMap.get(word) + wordScore;
					//this mapping is not used right now
					thmScoreMap.put(thmIndex, newScore);
					scoreThmMMap.put(newScore, thmIndex);
				}				
			}
		}
		List<Integer> highestThmList = new ArrayList<Integer>();
		//get the thms having the highest k scores
		NavigableMap<Double, Collection<Integer>> thmMap = scoreThmMMap.asMap().descendingMap();
		int counter = numHighest;
		
		for(Entry<Double, Collection<Integer>> entry : thmMap.entrySet()){			
			for(Integer thmIndex : entry.getValue()){
				if(counter == 0) break;				
				highestThmList.add(thmIndex);				
				counter--;			
			}
				
		}
		return highestThmList ;
	}	
	
	//test function
	public static void main(String[] args){
		Scanner sc = new Scanner(System.in);
		
		while(sc.hasNextLine()){
			String thm = sc.nextLine();			
			List<Integer> highestThms = getHighestThm(thm, 3);
			for(Integer thmIndex : highestThms){
				System.out.println(thmList.get(thmIndex));
			}
		}		
		sc.close();
	}
	
}
