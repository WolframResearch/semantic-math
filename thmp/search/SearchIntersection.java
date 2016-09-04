package thmp.search;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Scanner;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.TreeMultimap;

import thmp.search.SearchWordPreprocess.WordWrapper;

/**
 * Searches by finding intersections in theorems that contain given keywords.
 * 
 * @author yihed
 */
public class SearchIntersection {

	
	//bonus points for matching context better, eg hyp or stm
	private static final int CONTEXT_WORD_BONUS = 1;
	/**
	 * Map of keywords and their scores in document, the higher freq in doc, the lower 
	 * score, say 1/(log freq + 1) since log 1 = 0. 
	 */
	private static final ImmutableMap<String, Integer> wordsScoreMap;	
	
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
		//System.out.println(thmList);
		wordsScoreMap = CollectThm.get_wordsScoreMapNoAnno();
		//System.out.println(CollectThm.get_wordsScoreMap());
		wordThmMMap = CollectThm.get_wordThmsMMap();
	}
		
	/**
	 * Builds scoreThmMMap 
	 * @param input input String
	 * @param numHighest number of highest-scored thms to retrieve.
	 * @return List of indices of highest-scored thms. Sorted in ascending
	 * order, best first. List is 0-based.
	 */
	public static List<Integer> getHighestThm(String input, int ... num){
		if(input.matches("\\s*")) return null;
		
		//make input list of words
		//String[] inputAr = input.toLowerCase().split(SPLIT_DELIM);
		List<WordWrapper> wordWrapperList = SearchWordPreprocess.sortWordsType(input);
		
		//determine if first token is integer, if yes, use it as the number of 
		//closest thms. Else use 3 as default value.
		int numHighest = 3;
		//whether to skip first token
		int firstIndex = 0;
		if(num.length == 0){
		String firstWord = wordWrapperList.get(0).word();
		if(firstWord.matches("\\d+")){
			numHighest = Integer.parseInt(firstWord);
			firstIndex = 1;
		}
		}else{
			numHighest = num[0];
		}
		/*
		 * Map of theorems, in particular their indices in thmList, and the scores corresponding to the keywords they contain.
		 * The rarer a keyword is in the doc, the higher its score is.
		 */
		Map<Integer, Integer> thmScoreMap = new HashMap<Integer, Integer>(); 
		
		/*
		 * Multimap of double and ints, where double is score, and the Integers
		 * are the indices of the thms having this score.
		 */
		TreeMultimap<Integer, Integer> scoreThmMMap = TreeMultimap.create();
		
		for(int i = firstIndex; i < wordWrapperList.size(); i++){
			WordWrapper curWrapper = wordWrapperList.get(i);
			String word = curWrapper.word();			
			
			//other annotation form of word. 
			//String wordOtherForm;
			//elicit higher score if wordLong fits
			//also turn into singular form if applicable
			String wordLong = curWrapper.hashToString();
			
			//update scores map
			int curScoreToAdd = 0;
			
			//for every word, get list of thms containing this word			
			Collection<Integer> wordThms = wordThmMMap.get(wordLong);
			Integer wordScore;
			if(!wordThms.isEmpty()){	
				//wordScore = wordsScoreMap.get(wordLong);
				wordScore = wordsScoreMap.get(word);
				curScoreToAdd = wordScore + CONTEXT_WORD_BONUS;
				
			}else{
				String wordOtherForm = curWrapper.otherHashForm();
				String singWordOtherForm = curWrapper.otherHashForm();
				
				String singForm = CollectThm.getSingularForm(word);	
				String singFormLong = curWrapper.hashToString(singForm);
				//if(wordsScoreMap.get(singFormLong) != null){
				if(wordsScoreMap.get(singForm) != null){	
					wordThms = wordThmMMap.get(singFormLong);
					//wordScore = wordsScoreMap.get(singFormLong);
					wordScore = wordsScoreMap.get(singForm);
					curScoreToAdd = wordScore + CONTEXT_WORD_BONUS;	
				}//other form of word
				/*else if(wordThmMMap.containsKey(wordOtherForm)){
					wordThms = wordThmMMap.get(wordOtherForm);
					wordScore = wordsScoreMap.get(wordOtherForm);
					curScoreToAdd = wordScore;				
				}else if(wordThmMMap.containsKey(singWordOtherForm)){
					wordThms = wordThmMMap.get(singWordOtherForm);
					wordScore = wordsScoreMap.get(singWordOtherForm);
					curScoreToAdd = wordScore;		
				}			*/	
			}			
			
			if(wordThms != null){
				for(Integer thmIndex : wordThms){	
					Integer prevScore = thmScoreMap.get(thmIndex);
					prevScore = prevScore == null ? 0 : prevScore;
					Integer newScore = prevScore + curScoreToAdd;
					//this mapping is not being used right now
					thmScoreMap.put(thmIndex, newScore);
					scoreThmMMap.put(newScore, thmIndex);
				}				
			}			
		}
		List<Integer> highestThmList = new ArrayList<Integer>();
		//get the thms having the highest k scores
		NavigableMap<Integer, Collection<Integer>> thmMap = scoreThmMMap.asMap().descendingMap();
		
		//pick up numHighest number of unique thms
		Set<Integer> pickedThmSet = new HashSet<Integer>();
		
		int counter = numHighest;
		for(Entry<Integer, Collection<Integer>> entry : thmMap.entrySet()){			
			for(Integer thmIndex : entry.getValue()){
				if(counter == 0) break;				
				if(pickedThmSet.contains(thmIndex)) continue;
				pickedThmSet.add(thmIndex);
				highestThmList.add(thmIndex);				
				counter--;			
				//System.out.println("thm Score " + entry.getKey() + " thmIndex "+ thmIndex + " thm " + thmList.get(thmIndex));
			}
			
		}
		return highestThmList ;
	}	
	
	/**
	 * Reads in keywords. Gets theorems with highest scores for this.
	 * @param args
	 */
	public static void main(String[] args){
		Scanner sc = new Scanner(System.in);
		
		while(sc.hasNextLine()){
			String thm = sc.nextLine();			
			
			List<Integer> highestThms = getHighestThm(thm);
			
			if(highestThms == null) continue;
			
			for(Integer thmIndex : highestThms){
				System.out.println(thmList.get(thmIndex));
			}
		}		
		sc.close();
	}
	

}
