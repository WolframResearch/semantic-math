package thmp.search;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;

import com.google.common.collect.TreeMultimap;

import thmp.ProcessInput;
import thmp.utils.FileUtils;
import thmp.utils.WordForms;

/**
 * Search for 3-grams. 
 * Uses TreeMultimap to store the 3 grams, and their
 * first words are the keys.
 * 	//if two strings are not equal, my comparator could make 
	//them equal, since they are already in same bucket

 * @author yihed
 *
 */
public class ThreeGramSearch {

	private static final TreeMultimap<String, String> threeGramMap;
	//frequency counts of three-grams
	private static final Map<String, Integer> threeGramCountsMap;
	
	private static final List<String> threeGramList;
	//map of three grams and their frequencies
	private static final Map<String, Integer> threeGramFreqMap;
	
	//private static final Set<String> nonMathFluffWordsSet = CollectFreqWords.GetFreqWords.get_nonMathFluffWordsSet2();	
	private static final Path threeGramsFilePath = Paths.get("src/thmp/data/threeGrams.txt");
	//additional three grams to be intetionally added. 
	private static final String[] ADDITIONAL_THREE_GRAMS = new String[]{"local ring", "local field"};
	//map of maps containing first words of 2 grams as keys, and maps of 2nd words and their counts as values
	private static final Map<String, Map<String, Integer>> twoGramFreqMap = NGramSearch.get_nGramMap();
	
	//should put these in map
	//private static final String FLUFF_WORDS = "a|the|tex|of|and|on|let|lemma|for|to|that|with|is|be|are|there|by"
		//	+ "|any|as|if|we|suppose|then|which|in|from|this|assume|this|have";
	private static final Set<String> fluffWordsSet;
	
	//the reciprocal of this is the portion of the map we want to take
	private static final int MAP_PORTION = 2;
	private static final double MULTIPLY_BY = 2.0/3;
	
	static{
		//gather the 3-grams, fill in threeGramMap
		//get thmList from CollectThm
		List<String> thmList = ProcessInput.processInput(CollectThm.ThmList.get_thmList(), true);
		
		fluffWordsSet = WordForms.makeFluffSet();
		threeGramCountsMap = new HashMap<String, Integer>();		
		//build the threeGramCountsMap
		get3GramCounts(thmList, threeGramCountsMap);		
		
		//create Comparator instances		
		//create Multimap with custom value comparator
		ThreeGramComparator threeGramComparator = new ThreeGramComparator();
		StringComparator stringComparator = new StringComparator();
		threeGramMap = TreeMultimap.create(stringComparator, threeGramComparator);
		buildThreeGramMap(threeGramMap);
		
		//System.out.println(threeGramMap);
		
		threeGramFreqMap = new HashMap<String, Integer>();
		threeGramList = filterThreeGrams(threeGramMap, threeGramFreqMap);
		System.out.println("Done with 3 grams.");
	}		
	
	/**
	 * Get 3 grams from thmList.
	 * @param thmList
	 */
	private static void get3GramCounts(List<String> thmList, Map<String, Integer> threeGramCountsMap){
		//build the 3 gram map using 
		for(String thm : thmList){
			
			//if(thm.matches("\\s*")) continue;
			
			String[] thmAr = thm.toLowerCase().split(WordForms.splitDelim());
			String word0;
			String word1;
			String word2;
			
			//get the next three words, if they don't start or end with fluff words
			for(int i = 0; i < thmAr.length-2; i++){
				
				word0 = thmAr[i];
				//shouldn't happen because the way thm is split
				if(word0.matches("\\s*|tex") || word0.contains("\\")){
					//if(word0.matches("(?:\\s+)|(?:(?:[^\\]*)(?:\\\\)(?:[.]*))")){
					continue;
				}
				word1 = thmAr[i+1];
				//int j = i;
				
				while((word1.matches("\\s+|tex") || word1.contains("\\")) && i < thmAr.length-2){
					word1 = thmAr[i+1];
					i++;
				}
				
				if(i == thmAr.length - 2){
					break;
				}
				
				word2 = thmAr[i+2];
				
				while((word2.matches("\\s+|tex") || word2.contains("\\")) && i < thmAr.length-2){
					word1 = thmAr[i+2];
					i++;
				}
				
				word0 = WordForms.getSingularForm(word0);
				word1 = WordForms.getSingularForm(word1);
				word2 = WordForms.getSingularForm(word2);
				
				//if(FLUFF_WORDS.contains(word2) || FLUFF_WORDS.contains(word0)){
				if(fluffWordsSet.contains(word0) || fluffWordsSet.contains(word2)){
					//if(nonMathFluffWordsSet.contains(word0) || nonMathFluffWordsSet.contains(word2)){
					continue;
				}				
				String threeGram = word0 + " " + word1 + " " + word2;
				
				//update the counts frequency count of this 3 gram, for equals
				Integer threeGramCurCount = threeGramCountsMap.get(threeGram);
				if(threeGramCurCount != null){
					threeGramCountsMap.put(threeGram, threeGramCurCount+1);
				}else{
					threeGramCountsMap.put(threeGram, 1);
				}
			}
			
		}
		
	}
	
	private static void buildThreeGramMap(TreeMultimap<String, String> threeGramMap){
		
		for(Map.Entry<String, Integer> entry : threeGramCountsMap.entrySet()){
			//put to threeGramMap
			String threeGram = entry.getKey();
			//System.out.println(threeGram);
			String[] threeGramAr = threeGram.split("\\s+");
			//shouldn't actually happen
			if(threeGramAr.length < 3) continue;
			
			String firstWord = threeGramAr[0];
			threeGramMap.put(firstWord, threeGram);			
		}
	}
	
	/**
	 * Obtains the most frequent three grams from threeGramMap, 
	 * @param threeGramMap
	 */
	private static List<String> filterThreeGrams(TreeMultimap<String, String> threeGramMap, Map<String, Integer> threeGramFreqMap){
		List<String> threeGramList = new ArrayList<String>();
		//System.out.println(threeGramMap);
		
		for(String firstWord : threeGramMap.keySet()){
			NavigableSet<String> threeGramSet = threeGramMap.get(firstWord);
			Iterator<String> threeGramSetIter = threeGramSet.descendingIterator();
			
			//int upTo = (int)Math.round(threeGramSet.size()*MAP_PORTION);
			int upTo = threeGramSet.size()/MAP_PORTION;
			//upTo = (int)Math.floor(((double)threeGramSet.size())*((double)2/3));
			//System.out.println(2/3);
			upTo = (int)(threeGramSet.size()*MULTIPLY_BY);
			
			//get the most frequent ones
			while(upTo > 0){
				String threeGram = threeGramSetIter.next();
				threeGramList.add(threeGram);
				threeGramFreqMap.put(threeGram, threeGramCountsMap.get(threeGram));
				upTo--;
			}
			//pick the rest according to pairs frequencies gathered in twoGramFreqMap
			while(threeGramSetIter.hasNext()){
				String threeGram = threeGramSetIter.next();
				String[] threeGramAr = threeGram.split("\\s+");
				
				//shouldn't happen at this point
				if(threeGramAr.length < 3) continue;
				
				String word1 = threeGramAr[1];
				
				//maps of 2 grams that start with firstWord or word1
				Map<String, Integer> firstWordMap = twoGramFreqMap.get(firstWord);
				
				int threeGramFreq = threeGramCountsMap.get(threeGram);
				
				if(firstWordMap != null ){
					boolean added = addTo3GramList(threeGramList, firstWordMap, threeGram, word1, threeGramFreq);
					if(!added){
						String word2 = threeGramAr[2];
						Map<String, Integer> word1Map = twoGramFreqMap.get(word1);
						if(word1Map != null){
							addTo3GramList(threeGramList, word1Map, threeGram, word2, threeGramFreq);
						}
					}
				}
			}
		}
		return threeGramList;
	}

	/**
	 * Auxiliary method for filterThreeGrams(). 
	 * @param wordMap map to get word freq out of.
	 * @param threeGram the 3-gram
	 * @param word Either second or third word in the 3-gram
	 */
	private static boolean addTo3GramList(List<String> threeGramList, Map<String, Integer> wordMap, String threeGram, String word,
			int threeGramFreq){
		Integer totalPairFreq = wordMap.get(word);
		boolean added = false;
		//
		if((totalPairFreq != null && threeGramFreq > totalPairFreq/2)){
			threeGramList.add(threeGram);
			threeGramFreqMap.put(threeGram, threeGramFreq);
			added = true;
		}
		return added;
	}
	/**
	 * String comparator that uses String's natural lexicographical ordering.
	 */
	private static class StringComparator implements Comparator<String>{
		
		public int compare(String s1, String s2){
			return s1.compareTo(s2);
		}
	}
	
	/**
	 * ThreeGram comparator based on the three gram counts in threeGramCountsMap
	 */
	private static class ThreeGramComparator implements Comparator<String>{
		/**
		 * Compare based on counts
		 * @param s1
		 * @param s2
		 * @return
		 */
		@Override
		public int compare(String threeGram1, String threeGram2){
			int count1 = threeGramCountsMap.get(threeGram1);
			int count2 = threeGramCountsMap.get(threeGram2);
			return count1 > count2 ? 1 : (count1 < count2 ? -1 : 0);			
		}		
	}
	
	/**
	 * Map of 3-grams and their frequencies
	 * @return
	 */
	public static Map<String, Integer> get3GramsMap(){
		return threeGramFreqMap;
	}
	
	public static void main(String[] args){
		//System.out.println(threeGramFreqMap);
		//System.out.println(threeGramList);
		boolean write2gramsToFile = false;
		if(write2gramsToFile){ 
			FileUtils.writeToFile(threeGramList, threeGramsFilePath);
		}
	}
}
