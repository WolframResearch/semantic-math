package thmp.search;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.regex.Pattern;

import javax.servlet.ServletContext;

import com.google.common.collect.TreeMultimap;

import thmp.parse.ProcessInput;
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
	//map of three grams and their frequencies, filtered from threeGramCountsMap.
	private static final Map<String, Integer> threeGramFreqMap;
	//
	private static final Set<String> threeGramFirstWordsSet = new HashSet<String>();
	
	private static final Path threeGramsFilePath = Paths.get("src/thmp/data/threeGramData.txt");
	//additional three grams to be intetionally added. 
	private static final String[] ADDITIONAL_THREE_GRAMS = new String[]{"formal power series", "one to one"};
	// name of two gram data file containing additional 2-grams that should be included. These don't have
	//frequencies associated with them.
	private static final String THREE_GRAM_DATA_FILESTR = "src/thmp/data/threeGramData.txt";	
	private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
	
	//map of maps containing first words of 2 grams as keys, and maps of 2nd words and their counts as values
	private static final Map<String, Map<String, Integer>> twoGramFreqMap = NGramSearch.get_nGramMap();
	private static int averageThreeGramFreqCount;
	private static final int DEFAULT_THREE_GRAM_FREQ_COUNT = 5;
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
		List<String> thmList = ProcessInput.processInput(CollectThm.ThmList.allThmsWithHypList(), true);
		
		fluffWordsSet = WordForms.getFluffSet();
		threeGramCountsMap = new HashMap<String, Integer>();		
		//build the threeGramCountsMap
		get3GramCounts(thmList, threeGramCountsMap);		
		
		//create Comparator instances used to compare frequencies.
		//create Multimap with custom value comparator.
		ThreeGramComparator threeGramComparator = new ThreeGramComparator();
		StringComparator stringComparator = new StringComparator();
		threeGramMap = TreeMultimap.create(stringComparator, threeGramComparator);
		
		buildThreeGramMap(threeGramMap);
		
		Set<String> initialThreeGramsSet = new HashSet<String>();
		//fill in initial default set of scraped three grams
		BufferedReader threeGramBR = null;
		ServletContext servletContext = NGramSearch.getServletContext();
		if(null == servletContext){
			try{
				threeGramBR = new BufferedReader(new FileReader(THREE_GRAM_DATA_FILESTR));
				readAdditionalThreeGrams(threeGramBR, initialThreeGramsSet);				
			}catch(FileNotFoundException e){
				e.printStackTrace();
			}finally{
				FileUtils.silentClose(threeGramBR);
			}
		}else{
			InputStream twoGramInputStream = servletContext.getResourceAsStream(THREE_GRAM_DATA_FILESTR);
			threeGramBR = new BufferedReader(new InputStreamReader(twoGramInputStream));		
			readAdditionalThreeGrams(threeGramBR, initialThreeGramsSet);
			FileUtils.silentClose(twoGramInputStream);
			FileUtils.silentClose(threeGramBR);
		}
		threeGramFreqMap = new HashMap<String, Integer>();
		//obtain the most frequent three grams from the previous threeGramMap, definitely
		//keep the ones from initialThreeGramsSet
		threeGramList = filterThreeGrams(threeGramMap, threeGramFreqMap, initialThreeGramsSet);
		//System.out.println(threeGramFreqMap);
		System.out.println("ThreeGramSearch - Done with gathering 3-grams!");
	}		
	
	/**
	 * Collect 3 grams from thmList.
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
				//was word0.matches("\\s*"), instead of \\s+
				if(WHITESPACE_PATTERN.matcher(word0).matches() || word0.contains("\\") || word0.contains("$")){
					//if(word0.matches("(?:\\s+)|(?:(?:[^\\]*)(?:\\\\)(?:[.]*))")){
					continue;
				}
				word1 = thmAr[i+1];
				//int j = i;
				
				while((WHITESPACE_PATTERN.matcher(word1).matches() || word1.contains("\\")) && i < thmAr.length-2){
					word1 = thmAr[i+1];
					i++;
				}
				
				if(i == thmAr.length - 2){
					break;
				}
				
				word2 = thmAr[i+2];
				
				while((WHITESPACE_PATTERN.matcher(word2).matches() || word2.contains("\\") || word2.contains("$")) && i < thmAr.length-2){
					word1 = thmAr[i+2];
					i++;
				}
				
				word0 = WordForms.getSingularForm(word0);
				word1 = WordForms.getSingularForm(word1);
				word2 = WordForms.getSingularForm(word2);
				
				//if(FLUFF_WORDS.contains(word2) || FLUFF_WORDS.contains(word0)){
				//first and last words are not allowed to be fluff words.
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
	 * to build final threeGramMap.
	 * @param threeGramMap
	 * @param threeGramFreqMap Collected three grams and their frequencies.
	 * @param initialThreeGramsSet Scraped set of three grams that definitely should be kept.
	 */
	private static List<String> filterThreeGrams(TreeMultimap<String, String> threeGramMap, Map<String, Integer> threeGramFreqMap,
			Set<String> initialThreeGramsSet){
		List<String> threeGramList = new ArrayList<String>();
		//System.out.println(threeGramMap);
		int threeGramFreqSum = 0;
		int totalThreeGramAdded = 0;
		
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
				int threeGramFreq = threeGramCountsMap.get(threeGram);
				threeGramFreqMap.put(threeGram, threeGramFreq);
				threeGramFreqSum += threeGramFreq;
				totalThreeGramAdded++;
				threeGramFirstWordsSet.add(firstWord);
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
				boolean added = false;
				if(firstWordMap != null ){
					added = addTo3GramList(threeGramList, firstWordMap, threeGram, word1, threeGramFreq, firstWord);
					if(!added){
						String word2 = threeGramAr[2];
						Map<String, Integer> word1Map = twoGramFreqMap.get(word1);
						if(word1Map != null){
							added = addTo3GramList(threeGramList, word1Map, threeGram, word2, threeGramFreq, firstWord);							
						}
					}					
				}	
				if(!added && initialThreeGramsSet.contains(threeGram)){
					threeGramList.add(threeGram);
					threeGramFreqMap.put(threeGram, threeGramFreq);
					threeGramFirstWordsSet.add(firstWord);
					initialThreeGramsSet.remove(threeGram);
					added = true;
				}
				if(added){
					threeGramFreqSum += threeGramFreq;
					totalThreeGramAdded++;
				}
			}
		}
		if(totalThreeGramAdded != 0){			
			averageThreeGramFreqCount = threeGramFreqSum / totalThreeGramAdded;
		}else{
			averageThreeGramFreqCount = DEFAULT_THREE_GRAM_FREQ_COUNT;
		}
		//double averageThreeGramFreqCountHalved = averageThreeGramFreqCount*2.0/3;
		//add the remaining threegrams from initialThreeGramsSet
		for(String threeGram : initialThreeGramsSet){
			addAdditionalThreeGramsToMap(threeGramFreqMap, threeGramList, threeGram);
		}
		for(String threeGram : ADDITIONAL_THREE_GRAMS){
			addAdditionalThreeGramsToMap(threeGramFreqMap, threeGramList, threeGram);
		}
		return threeGramList;
	}

	/**
	 * @param threeGramFreqMap
	 * @param threeGramList
	 * @param threeGram
	 */
	private static void addAdditionalThreeGramsToMap(Map<String, Integer> threeGramFreqMap, List<String> threeGramList,
			String threeGram) {
		if(!threeGramFreqMap.containsKey(threeGram)){
			threeGramList.add(threeGram);
			threeGramFreqMap.put(threeGram, averageThreeGramFreqCount);
			threeGramFirstWordsSet.add(threeGram.split("\\s+")[0]);
		}
	}

	/**
	 * Auxiliary method for filterThreeGrams(). 
	 * @param wordMap map to get word freq out of.
	 * @param threeGram the 3-gram
	 * @param word Either second or third word in the 3-gram
	 * @param firstWord The first word in the 3-gram
	 */
	private static boolean addTo3GramList(List<String> threeGramList, Map<String, Integer> wordMap, String threeGram, String word,
			int threeGramFreq, String firstWord){
		Integer totalPairFreq = wordMap.get(word);
		boolean added = false;
		//
		if((totalPairFreq != null && threeGramFreq > totalPairFreq/2)){
			threeGramList.add(threeGram);
			threeGramFreqMap.put(threeGram, threeGramFreq);
			threeGramFirstWordsSet.add(firstWord);
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
	 * Adds additional words scraped from resources.
	 * @param NGRAM_DATA_FILESTR name of file containing additional n-grams
	 * and words scraped from the web.
	 * @param initialTwoGramsSet Initial set of two grams to put into map.
	 */
	private static void readAdditionalThreeGrams(BufferedReader threeGramBR, Set<String> initialThreeGramsSet){
		/*BufferedReader fileBufferedReader;
		try{
			fileBufferedReader = new BufferedReader(new FileReader(THREE_GRAM_DATA_FILESTR));
		}catch(FileNotFoundException e){
			e.printStackTrace();
			return;
		}*/
		String word;
		try{
			while((word = threeGramBR.readLine()) != null){
				//this should have been done when sorting the words into maps
				//word = word.toLowerCase();						
				initialThreeGramsSet.add(word);				
			}
		}catch(IOException e){
			e.printStackTrace();
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
	
	/**
	 * Retrieve average frequency of three grams.
	 * @return
	 */
	public static int averageThreeGramFreqCount(){
		return averageThreeGramFreqCount;
	}
	
	/** 
	 * @return Set of first words in the collected 3-grams.
	 */
	public static Set<String> get_3GramFirstWordsSet(){
		return threeGramFirstWordsSet;
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
