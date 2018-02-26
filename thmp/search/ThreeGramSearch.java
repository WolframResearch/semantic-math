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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.TreeMultimap;

import thmp.parse.ProcessInput;
import thmp.search.Searcher.SearchMetaData;
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

	
	//frequency counts of three-grams
	//***private static final Map<String, Integer> threeGramCountsMap;
	
	//**private static final List<String> threeGramList;
	//map of three grams and their frequencies, filtered from threeGramCountsMap.
	private static final Map<String, Integer> threeGramFreqMap;
	//
	private static final Set<String> threeGramFirstWordsSet;
	
	//private static final Path threeGramsFilePath = Paths.get("src/thmp/data/threeGramData.txt");
	//additional three grams to be intetionally added. 
	private static final String[] ADDITIONAL_THREE_GRAMS = new String[]{"formal power series", "one to one"};
	// name of two gram data file containing additional 2-grams that should be included. These don't have
	//frequencies associated with them.
	private static final String THREE_GRAM_DATA_FILESTR = "src/thmp/data/threeGramData.txt";	
	private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
	
	//three-grams gathered from MathOverflow
	private static final String THREE_GRAM_DATA_SO_FILESTR = "src/thmp/data/threeGramsSO.txt";
	
	//map of maps containing first words of 2 grams as keys, and maps of 2nd words and their counts as values
	//private static final Map<String, Map<String, Integer>> twoGramFreqMap = NGramSearch.twoGramTotalOccurenceMap();
	private static int averageThreeGramFreqCount;
	private static final Set<String> nonMathFluffWordsSet = WordFrequency.ComputeFrequencyData.trueFluffWordsSet();	
	private static final Pattern INVALID_THIRD_WORD_PATTERN = Pattern.compile("(?:.+ed)");
	private static final Set<String> INVALID_MIDDLE_WORD_SET;
	private static final int DEFAULT_THREE_GRAM_FREQ_COUNT = 5;
	//should put these in map
	//private static final String FLUFF_WORDS = "a|the|tex|of|and|on|let|lemma|for|to|that|with|is|be|are|there|by"
		//	+ "|any|as|if|we|suppose|then|which|in|from|this|assume|this|have";
	private static final Set<String> fluffWordsSet;
	//private static final String threeGramsMapPath = "src/thmp/data/threeGramsMap.dat";
	
	//four grams that are in the three-gram data file.
	private static final Set<String> fourGramPreSet = new HashSet<String>();
	//the reciprocal of this is the portion of the map we want to take
	private static final int MAP_PORTION = 2;
	private static final double MULTIPLY_BY = 2.0/3;
	
	static{
		//gather the 3-grams, fill in threeGramMap
		//get thmList from CollectThm
		//List<String> thmList = ProcessInput.processInput(CollectThm.ThmList.allThmsWithHypList(), true);
		////List<String> thmList = CollectThm.ThmList.allThmsWithHypList();
		INVALID_MIDDLE_WORD_SET = new HashSet<String>();
		String[] invalidMiddleWordSet = new String[]{"is", "and", "with"};
		for(String word : invalidMiddleWordSet){
			INVALID_MIDDLE_WORD_SET.add(word);
		}
		fluffWordsSet = WordForms.stopWordsSet();
		//*****
		/* Commented out June 2017.
		 * threeGramCountsMap = new HashMap<String, Integer>();		
		//build the threeGramCountsMap
		get3GramCounts(thmList, threeGramCountsMap);		
		
		//create Comparator instances used to compare frequencies.
		//create Multimap with custom value comparator.
		ThreeGramComparator threeGramComparator = new ThreeGramComparator();
		StringComparator stringComparator = new StringComparator();
		
		TreeMultimap<String, String> threeGramMap;///used internally during building
		threeGramMap = TreeMultimap.create(stringComparator, threeGramComparator);*/
		
		//buildThreeGramMap(threeGramMap);
		
		/*Set<String> initialThreeGramsSet = new HashSet<String>();
		//fill in initial default set of scraped three grams
		BufferedReader threeGramBR = null;
		ServletContext servletContext = NGramSearch.getServletContext();*/
		/*if(null == servletContext){
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
		}*/
		//obtain the most frequent three grams from the previous threeGramMap, definitely
		//keep the ones from initialThreeGramsSet

		//read threeGramMap from serialized file
		String threeGramsFreqMapPath = FileUtils.getPathIfOnServlet(SearchMetaData.threeGramsFreqMapPath());
		@SuppressWarnings("unchecked")
		Map<String, Integer> threeGramPreMap = ((List<Map<String, Integer>>)FileUtils.deserializeListFromFile(threeGramsFreqMapPath)).get(0);
		
		int avgScore = 0;
		for(int score : threeGramPreMap.values()) {
			avgScore += score;
		}
		avgScore = avgScore / threeGramPreMap.size() + 1;
		
		String threeGramSOStr = FileUtils.readStrFromFile(THREE_GRAM_DATA_SO_FILESTR).toLowerCase();
		
		String[] threeGramsSO = NGramSearch.TwoGramSearch.TWO_THREE_SO_GRAM_FileStr_SPLIT_PATT.split(threeGramSOStr);
		
		for(String threeGram : threeGramsSO) {
			String[] threeGramAr = NGramSearch.TwoGramSearch.TWO_THREE_GRAM_SPLIT_PATT.split(threeGram);
			int threeGramArLen = threeGramAr.length;
			if(threeGramArLen == 3){
				StringBuilder sb = new StringBuilder();
				for(String w : threeGramAr) {
					sb.append(w).append(" ");
				}
				String threeGram2 = WordForms.normalizeNGram(sb.subSequence(0, sb.length()-1).toString());
				threeGramPreMap.put(threeGram2, avgScore);
			}else if(threeGramAr.length == 4) {

				StringBuilder sb = new StringBuilder();
				for(String w : threeGramAr) {
					sb.append(w).append(" ");
				}
				String fourGram = WordForms.normalizeNGram(sb.subSequence(0, sb.length()-1).toString());
				fourGramPreSet.add(fourGram);

				//get last three words, which are usually reasonable three grams.
				sb = new StringBuilder();
				for(int i = 1; i < threeGramArLen; i++) {
					String w = threeGramAr[i];
					sb.append(w).append(" ");
				}
				String threeGram2 = WordForms.normalizeNGram(sb.subSequence(0, sb.length()-1).toString());				
				threeGramPreMap.put(threeGram2, avgScore);
			}
		}
		
		for(String prevThreeGram : NGramSearch.TwoGramSearch.threeGramPreSet()) {
			threeGramPreMap.put(prevThreeGram, avgScore);
		}
		
		threeGramFreqMap = ImmutableMap.copyOf(threeGramPreMap);
		threeGramFirstWordsSet = WordForms.gatherKeyFirstWordSetFromMap(threeGramFreqMap);
		//threeGramList = filterThreeGrams(threeGramMap, threeGramFreqMap, initialThreeGramsSet);
		//*****
		//System.out.println(threeGramFreqMap);		
		System.out.println("ThreeGramSearch - Done deserializing 3-grams!");
	}		
	
	/**
	 * Gather and build three-gram map from given (comprehensive) list of theorems. To be used
		 * to serialize the map later.
	 * @param thmList
	 * @return Three grams and their frequencies
	 */
	public static Map<String, Integer> gatherAndBuild3GramsMap(List<String> thmList, 
			Map<String, Map<String, Integer>> twoGramTotalOccurenceMap){
		//fluffWordsSet = WordForms.getFluffSet();
		Map<String, Integer> threeGramCountsMap = new HashMap<String, Integer>();		
		//build the threeGramCountsMap
		get3GramCounts(thmList, threeGramCountsMap);		
		
		//create Comparator instances used to compare frequencies.
		//create Multimap with custom value comparator.
		ThreeGramComparator threeGramComparator = new ThreeGramComparator(threeGramCountsMap);
		StringComparator stringComparator = new StringComparator();
		TreeMultimap<String, String> threeGramMap = TreeMultimap.create(stringComparator, threeGramComparator);
		
		buildThreeGramMap(threeGramMap, threeGramCountsMap);
		
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
		Map<String, Integer> threeGramFreqMap = new HashMap<String, Integer>();
		//obtain the most frequent three grams from the previous threeGramMap, definitely
		//keep the ones from initialThreeGramsSet
		filterThreeGrams(threeGramMap, threeGramFreqMap, initialThreeGramsSet, twoGramTotalOccurenceMap,
				threeGramCountsMap);
		return threeGramFreqMap;
	}
	
	/**
	 * Collect 3 grams from thmList.
	 * @param thmList
	 */
	private static void get3GramCounts(List<String> thmList, Map<String, Integer> threeGramCountsMap){
		//build the 3 gram map using 
		for(String thm : thmList){
			
			//if(thm.matches("\\s*")) continue;
			
			List<String> thmAr = WordForms.splitThmIntoSearchWordsList(thm.toLowerCase());
			String word0;
			String word1;
			String word2;
			int thmArSz = thmAr.size();
			
			//get the next three words, if they don't start or end with fluff words
			for(int i = 0; i < thmArSz-2; i++){
				
				word0 = thmAr.get(i);
				//shouldn't happen because the way thm is split
				//was word0.matches("\\s*"), instead of \\s+
				if(WHITESPACE_PATTERN.matcher(word0).matches() || word0.length() < 3
						|| !NGramSearch.ALPHA_PATTERN.matcher(word0).matches()
						|| WordForms.SPECIAL_CHARS_PATTERN.matcher(word0).matches()
						|| NGramSearch.INVALID_WORD_PATTERN.matcher(word0).matches()
						|| NGramSearch.isInvalid1stWord(word0)
						|| fluffWordsSet.contains(word0) || nonMathFluffWordsSet.contains(word0)
						){
					continue;
				}
				word1 = thmAr.get(i+1);
				
				if(WHITESPACE_PATTERN.matcher(word1).matches() || word1.length() < 2
						|| !NGramSearch.ALPHA_PATTERN.matcher(word1).matches()
						|| INVALID_MIDDLE_WORD_SET.contains(word1)
						|| WordForms.SPECIAL_CHARS_PATTERN.matcher(word1).matches()
						|| NGramSearch.INVALID_WORD_PATTERN.matcher(word1).matches()
						|| NGramSearch.isInvalid2ndWord(word1)
						|| nonMathFluffWordsSet.contains(word1)
						){
					i++;
					continue;
				}				
				word2 = thmAr.get(i+2);				
				if(WHITESPACE_PATTERN.matcher(word2).matches() || word2.length() < 4
						|| !NGramSearch.ALPHA_PATTERN.matcher(word2).matches()
						|| WordForms.SPECIAL_CHARS_PATTERN.matcher(word2).matches()
						|| NGramSearch.INVALID_WORD_PATTERN.matcher(word2).matches()
						|| fluffWordsSet.contains(word2) || nonMathFluffWordsSet.contains(word2)
						|| NGramSearch.isInvalid2ndWordEnding(word2)
						|| NGramSearch.isInvalid2ndWord(word2)
						|| INVALID_THIRD_WORD_PATTERN.matcher(word2).matches()
						){
					i += 2;
					continue;
				}
				
				//only get singularized form for last word.
				//word0 = WordForms.getSingularForm(word0);
				//word1 = WordForms.getSingularForm(word1);
				word2 = WordForms.getSingularForm(word2);
				
				//if(FLUFF_WORDS.contains(word2) || FLUFF_WORDS.contains(word0)){
				//first and last words are not allowed to be fluff words.
				/*if(fluffWordsSet.contains(word0) || fluffWordsSet.contains(word2)){
					//if(nonMathFluffWordsSet.contains(word0) || nonMathFluffWordsSet.contains(word2)){
					continue;
				}*/				
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
	
	/**
	 * Fills in threeGramMap using threeGramCountsMap.
	 * @param threeGramMap
	 * @param threeGramCountsMap
	 */
	private static void buildThreeGramMap(TreeMultimap<String, String> threeGramMap,
			Map<String, Integer> threeGramCountsMap){
		
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
	 * @param threeGramCountsMap three gram frequency counts.
	 * @return List of three grams, usually used for human inspection, not at runtime.
	 */
	private static List<String> filterThreeGrams(TreeMultimap<String, String> threeGramMap, Map<String, Integer> threeGramFreqMap,
			Set<String> initialThreeGramsSet, Map<String, Map<String, Integer>> twoGramTotalOccurenceMap,
			Map<String, Integer> threeGramCountsMap){
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
				//threeGramFirstWordsSet.add(firstWord);
				upTo--;
			}
			
			//pick the rest according to pairs frequencies gathered in twoGramFreqMap
			while(threeGramSetIter.hasNext()){
				String threeGram = threeGramSetIter.next();
				String[] threeGramAr = WordForms.getWhiteNonEmptySpaceNotAllPattern().split(threeGram);
				
				//shouldn't happen at this point
				if(threeGramAr.length < 3) continue;
				
				String word1 = threeGramAr[1];
				
				//maps of 2 grams that start with firstWord or word1
				Map<String, Integer> firstWordMap = twoGramTotalOccurenceMap.get(firstWord);
				
				int threeGramFreq = threeGramCountsMap.get(threeGram);
				boolean added = false;
				if(firstWordMap != null ){
					added = addTo3GramList(threeGramFreqMap, threeGramList, firstWordMap, threeGram, word1, threeGramFreq, firstWord);
					if(!added){
						String word2 = threeGramAr[2];
						Map<String, Integer> word1Map = twoGramTotalOccurenceMap.get(word1);
						if(word1Map != null){
							added = addTo3GramList(threeGramFreqMap, threeGramList, word1Map, threeGram, word2, threeGramFreq, firstWord);							
						}
					}					
				}	
				if(!added && initialThreeGramsSet.contains(threeGram)){
					threeGramList.add(threeGram);
					threeGramFreqMap.put(threeGram, threeGramFreq);
					//threeGramFirstWordsSet.add(firstWord);
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
			//threeGramFirstWordsSet.add(threeGram.split("\\s+")[0]);
		}
	}

	/**
	 * Auxiliary method for filterThreeGrams(). 
	 * @param wordMap map to get word freq out of.
	 * @param threeGram the 3-gram
	 * @param word Either second or third word in the 3-gram
	 * @param firstWord The first word in the 3-gram
	 */
	private static boolean addTo3GramList(Map<String, Integer> threeGramFreqMap, List<String> threeGramList, 
			Map<String, Integer> wordMap, String threeGram, String word, int threeGramFreq, String firstWord){
		Integer totalPairFreq = wordMap.get(word);
		boolean added = false;
		//
		if((totalPairFreq != null && threeGramFreq > totalPairFreq/2)){
			threeGramList.add(threeGram);
			threeGramFreqMap.put(threeGram, threeGramFreq);
			//threeGramFirstWordsSet.add(firstWord);
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
				if(NGramSearch.ALPHA_PATTERN.matcher(word).matches()){
					initialThreeGramsSet.add(word);						
				}
			}
		}catch(IOException e){
			e.printStackTrace();
		}		
	}
	
	/**
	 * ThreeGram comparator based on the three gram counts in threeGramCountsMap
	 */
	private static class ThreeGramComparator implements Comparator<String>{
		
		Map<String, Integer> threeGramCountsMap;		
		public ThreeGramComparator(Map<String, Integer> threeGramCountsMap_){
			this.threeGramCountsMap = threeGramCountsMap_;
		}
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
			//FileUtils.writeToFile(threeGramList, threeGramsFilePath);
		}
	}
}
