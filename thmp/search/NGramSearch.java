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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import javax.servlet.ServletContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multiset;

import thmp.search.Searcher.SearchMetaData;
import thmp.utils.FileUtils;
import thmp.utils.WordForms;

/**
 * Fish out 2-grams, compute probabilities of one word following another.
 * O(N^2) time performance, where N is number of words.
 * Put words in HashMaps, where each entry is another map, with frequencies of words
 * that follow. N^2 space. Skip if common word.
 * 
 * The second word in two-grams are singularized, but not normalized. I.e. not reduced to 
 * their word stems.
 * 
 * @author yihed
 *
 */

public class NGramSearch {

	private static final Logger logger = LogManager.getLogger(NGramSearch.class);
	//get the non math fluff words
	private static final Set<String> nonMathFluffWordsSet = WordFrequency.ComputeFrequencyData.trueFluffWordsSet();	
	//file to write 2 grams to
	private static final Path twoGramsFilePath = Paths.get("src","thmp","data","twoGrams.txt"); //class.forname
	
	private static volatile ServletContext servletContext;
	
	// default two-gram averageFreqCount when total number of two grams is 0
	private static final int ADDITIONAL_TWO_GRAM_DEFAULT_COUNT = 7;
	
	private static final Pattern WHITESPACE_PATTERN = WordForms.getWhiteEmptySpacePattern();
	//last is Replacement Character �
	protected static final Pattern INVALID_WORD_PATTERN = Pattern.compile("(?:\\s*|.*[\\|$].*|.*\\d+.*|.*\\uFFFD.*)"); 
	private static final Pattern SENTENCE_SPACE_PATTERN = Pattern.compile("[-.;:,]");
	private static final Pattern SENTENCE_SPLIT_PATTERN = Pattern.compile("(?:[A-Za-z]*[-\\{\\[\\)\\(\\}\\]$\\\\/*|%.;,:_~!+^&\"\'+<>=#][A-Za-z]*|\\s+)");
	
	//words to be taken off of two-gram map
	private static final String[] NOT_TWO_GRAMS = new String[]{"field is", "more generally", "polynomial $a$", 
			"polynomial $f$", "over ring", "exist integer", "over field", "many zero"};
	
	//additional two grams not in serialized data file. After the last two-gram-gathering operation.
	//This is added at *each* app runtime. 
	private static final String[] ADDITIONAL_TWO_GRAMS_RUNTIME = new String[]{"stone weierstrass"};
	
	//should use this to detect fluff in first word.
	private static final Set<String> fluffWordsSet = WordForms.getFluffSet();
	//invalid ending for 2nd word
	private static final Set<String> VALID_ING_WORD_ENDING_SET;
	//invalid if 2nd word
	private static final Set<String> INVALID_FIRST_WORD_SET;
	//invalid if 1st word
	private static final Set<String> INVALID_SECOND_WORD_SET;
	
	public static final Pattern ALPHA_PATTERN = Pattern.compile("[a-z\\s]+"); //"(?:[a-z]+\\s*[a-z]*\\s*)+"
	
	static{
		String[] ingEndingAr = new String[]{"ring","mapping","embedding","ordering","branching","programming",
				"ending","mixing","covering","pairing","subring","tilting","string","thickening","matching",
				"crossing","encoding","annealing","splitting","linking","nonvanishing","alternating","filling",
				"vanishing","preserving","stabilizing","smoothing","bounding","packing", "plus"};
		VALID_ING_WORD_ENDING_SET = new HashSet<String>();
		for(String word : ingEndingAr){
			VALID_ING_WORD_ENDING_SET.add(word);
		}
		
		String[] invalidFirstWordAr = new String[]{"satisfying","forthcoming", "fascinating", "resulting",
				"neither", "either", "carried", "def", "nous", "respectively", "suite"};		
		INVALID_FIRST_WORD_SET = new HashSet<String>();
		for(String word : invalidFirstWordAr){
			INVALID_FIRST_WORD_SET.add(word);
		}
		
		String[] invalidSecondWordAr = new String[]{"neither", "either", "carried", "def", "nous", "suite", "respectively",
				"absolutely"};		
		INVALID_SECOND_WORD_SET = new HashSet<String>();
		for(String word : invalidSecondWordAr){
			INVALID_SECOND_WORD_SET.add(word);
		}
		//INVALID_SECOND_WORD_SET.add( );
		logger.info("Finished initializing NGramSearch");
	}
	/**
	 * Set the BufferedReader for scraped two grams.
	 * @param bf
	 */
	/*public static void setTwoGramBR(BufferedReader br){
		TWO_GRAM_DATA_BR = br;
	}*/
	
	/**
	 * Set the BufferedReader for scraped two grams.
	 * @param bf
	 */
	public static void setServletContext(ServletContext servletContext_){
		servletContext = servletContext_;
	}
	
	/**
	 * Set the BufferedReader for scraped two grams.
	 * @param bf
	 */
	public static ServletContext getServletContext(){
		return servletContext;
	}
	
	/**
	 * Set the BufferedReader for scraped two grams.
	 * @param bf
	 */
	/*public static void setThreeGramBR(BufferedReader br){
		THREE_GRAM_DATA_BR = br;
	}*/
	
	/*public static BufferedReader THREE_GRAM_DATA_BR(){
		return THREE_GRAM_DATA_BR;
	}*/
	
	public static class TwoGramSearch{

		//list of 2 grams that show up with above-average frequency, with their frequencies
		private static final Map<String, Integer> twoGramsMap;
		
		//undesirable part of speech combos for two grams, e.g. ent_verb.
		private static final Set<String> UNDESIRABLE_POS_COMBO_SET;
		
		//additional two grams at *build* time. I.e. when these data are generated locally from raw files.
		private static final String[] ADDITIONAL_TWO_GRAMS_BUILDTIME = new String[]{"local field", "direct sum",
				"finitely many", "open mapping", "finite type", "finite presentation", "principal ideal",
				"noetherian ring", "generated by", "haar measure", "stone weierstrass"};
		
		// name of two gram data file containing additional 2-grams that should be included. These don't have
		// frequencies associated with them. Load these into maps first and accumulate their frequencies.
		private static final String TWO_GRAM_DATA_FILESTR = "src/thmp/data/twoGramData.txt";		
		//set that contains the first word of each n-grams.
		private static final Set<String> twoGramFirstWordsSet;
		//factor used when the second word occurs with probability higher than 
		private static final Double SECOND_WORD_PROBA_MULT_FACTOR = 2.5;
		
		static{			
			UNDESIRABLE_POS_COMBO_SET = new HashSet<String>();
			String[] undesirablePosAr = new String[]{"ent_verb", "ent_vbs", "pre_ent", "poss_ent", "pre_adj", "ent_verbAlone"};
			UNDESIRABLE_POS_COMBO_SET.addAll(Arrays.asList(undesirablePosAr));
			
			//*****start method here
			//System.out.println("Gathering 2-grams...");
			//Map<String, Map<String, Integer>> nGramMap;//////
			//***twoGramTotalOccurenceMap = new HashMap<String, Map<String, Integer>>();
			//total word counts of all 2 grams
			/* Commented out June 2017.
			 * Map<String, Integer> totalWordCountsMap = new HashMap<String, Integer>();
			 
			//build nGram map, 
			buildNGramMap(twoGramTotalOccurenceMap, totalWordCountsMap);
			
			//computes the average frequencies of words that follow the first word in all 2-grams
			Map<String, Integer> averageWordCounts = computeAverageFreq(twoGramTotalOccurenceMap, totalWordCountsMap);
			*/
			//set of scraped two grams read in from two grams file.
			/*Set<String> initialTwoGramsSet = new HashSet<String>();
			//fills in initialTwoGramsSet with scraped two gram starter set
			if(null == servletContext){
				try{
					BufferedReader twoGramBF = new BufferedReader(new FileReader(TWO_GRAM_DATA_FILESTR));
					readAdditionalTwoGrams(twoGramBF, initialTwoGramsSet);
				}catch(FileNotFoundException e){
					logger.error(e);
					throw new IllegalStateException(e);
				}
			}else{
				InputStream twoGramInputStream = servletContext.getResourceAsStream(TWO_GRAM_DATA_FILESTR);
				BufferedReader twoGramBF = new BufferedReader(new InputStreamReader(twoGramInputStream));				
				readAdditionalTwoGrams(twoGramBF, initialTwoGramsSet);
			}*/
			//build this set from twoGramsMap by taking first words at initialization.
			
			//get list of 2 grams that show up frequently. Read from serialized version.
			String twoGramsSerialPath = FileUtils.getPathIfOnServlet(SearchMetaData.twoGramsFreqMapPath());
			
			
			@SuppressWarnings("unchecked")
			Map<String, Integer> twoGramPreMap = ((List<Map<String, Integer>>)FileUtils.deserializeListFromFile(twoGramsSerialPath)).get(0);
			//remove false positives
			for(String falseTwoGram : NOT_TWO_GRAMS){
				twoGramPreMap.remove(falseTwoGram);
			}
			int avgTwoGramScore = 0;
			for(int score : twoGramPreMap.values()) {
				avgTwoGramScore += score;
			}
			avgTwoGramScore = avgTwoGramScore/twoGramPreMap.size() + 1;
			
			for(String newTwoGram : ADDITIONAL_TWO_GRAMS_RUNTIME) {
				twoGramPreMap.put(newTwoGram, avgTwoGramScore);
			}
			twoGramsMap = ImmutableMap.copyOf(twoGramPreMap);	
			twoGramFirstWordsSet = WordForms.gatherKeyFirstWordSetFromMap(twoGramsMap);
			/*String temp = "src/thmp/data/TwoGramsCheck.txt";
				FileUtils.writeToFile(twoGramsMap, temp);*/
			
			//twoGramsMap = compile2grams(twoGramTotalOccurenceMap, averageWordCounts, nGramFirstWordsSet, initialTwoGramsSet);
			//****
			System.out.println("NGramSearch - Done with deserializing 2-grams!");
		}
		
		public static Map<String, Integer> gatherAndBuild2GramsMaps(List<String> thmList){
			Map<String, Map<String, Integer>> twoGramTotalOccurenceMap 
				= new HashMap<String, Map<String, Integer>>();
			return gatherAndBuild2GramsMaps(thmList, twoGramTotalOccurenceMap);
		}
		
		/**
		 * Gather and build two-gram map from given (comprehensive) list of theorems. To be used
		 * to serialize the map later. averageTwoGramFreqCount.
		 * @param thmList
		 * @param twoGramTotalOccurenceMap A map of maps. Keys are words in text, and entries are maps whose keys are 2nd terms
			in 2 grams, and entries are frequency counts.
			twoGramTotalOccurenceMap is used to build 3-grams, and so far only for that purpose.
		 * @return
		 */
		public static Map<String, Integer> gatherAndBuild2GramsMaps(List<String> thmList, 
				Map<String, Map<String, Integer>> twoGramTotalOccurenceMap){
			//total word counts of all 2 grams
			Map<String, Integer> totalWordCountsMap = new HashMap<String, Integer>();
			
			Multiset<String> allWordsMSet = HashMultiset.create();
			//build2GramMap(thmList, twoGramTotalOccurenceMap, totalWordCountsMap);	
			build2GramMap2(twoGramTotalOccurenceMap, totalWordCountsMap, thmList, allWordsMSet);		
			//computes the average frequencies of words that follow the first word in all 2-grams
			Map<String, Integer> averageWordCountsMap = computeAverageFreq(twoGramTotalOccurenceMap, totalWordCountsMap);
			//System.out.println("averageWordCountsMap "+averageWordCountsMap);
			//set of scraped two grams read in from two grams file.
			Set<String> initialTwoGramsSet = new HashSet<String>();
			//fills in initialTwoGramsSet with scraped two gram starter set
			if(null == servletContext){
				try{
					BufferedReader twoGramBF = new BufferedReader(new FileReader(TWO_GRAM_DATA_FILESTR));
					readAdditionalTwoGrams(twoGramBF, initialTwoGramsSet);
				}catch(FileNotFoundException e){
					logger.error(e);
					throw new IllegalStateException(e);
				}
			}else{
				InputStream twoGramInputStream = servletContext.getResourceAsStream(TWO_GRAM_DATA_FILESTR);
				BufferedReader twoGramBF = new BufferedReader(new InputStreamReader(twoGramInputStream));				
				readAdditionalTwoGrams(twoGramBF, initialTwoGramsSet);
			}		
			Set<String> nGramFirstWordsSet = new HashSet<String>();
			//get list of 2 grams that show up frequently
			return compile2grams(twoGramTotalOccurenceMap, averageWordCountsMap, nGramFirstWordsSet, initialTwoGramsSet, allWordsMSet);
		}
		
		private static class TwoGramFreqPair implements Comparable<TwoGramFreqPair>{
			String twoGram;
			int freq;
			TwoGramFreqPair(String twoGram_, int freq_){
				this.twoGram = twoGram_;
				this.freq = freq_;
			}
			@Override
			public int compareTo(TwoGramFreqPair other){
				return this.freq < other.freq ? -1 : this.freq > other.freq ? 1 : (this == other ? 0 : 1);
			}
			@Override
			public String toString(){
				return this.twoGram;
			}
		}
	/**
	 * compile list of 2 grams from nGramMap. Retain ones that show up higher than 3/2 of avg frequency.
	 * @return 2 grams, and their raw frequencies in doc.
	 * @param nGramMap Contains frequency data on words in corpus.
	 * @param averageWordCounts
	 * @param nGramFirstWordsSet
	 * @param initialTwoGramsSet
	 * @param allWordsMSet
	 * @return
	 */
	private static Map<String, Integer> compile2grams(Map<String, Map<String, Integer>> twoGramTotalOccurenceMap, 
			Map<String, Integer> averageWordCounts, Set<String> nGramFirstWordsSet, Set<String> initialTwoGramsSet,
			Multiset<String> allWordsMSet
			//int averageTwoGramFreqCount
			){		
		ListMultimap<String, String> posMMap = thmp.parse.Maps.posMMap();
		//one-off, for building two grams that haven't been filtered
		//probability of singleton words, use data in allWordsMSet.
		Map<String, Double> allWordsProbMap = new HashMap<String, Double>();
		int totalNumWords = allWordsMSet.size();
		for(String singletonWord : allWordsMSet.elementSet()){
			allWordsProbMap.put(singletonWord, ((double)allWordsMSet.count(singletonWord))/totalNumWords);
		}
		Set<TwoGramFreqPair> rawTwoGramSet = new TreeSet<TwoGramFreqPair>();
		Set<TwoGramFreqPair> firstWordSet = new TreeSet<TwoGramFreqPair>();
		//Set<TwoGramFreqPair> secondWordSet = new TreeSet<TwoGramFreqPair>();
		/*end one-off*/
		Map<String, Integer> twoGramMap = new HashMap<String, Integer>();		
		int totalFreqCount = 0;	
		int totalTwoGrams = 0;
		for(Map.Entry<String, Map<String, Integer>> wordMapEntry : twoGramTotalOccurenceMap.entrySet()){		
			String word = wordMapEntry.getKey();
			int averageWordCount = averageWordCounts.get(word);
			//System.out.println("NGramSearch - averageWordCount "+averageWordCount +" for word "+word);
			List<String> wordPosList = posMMap.get(word);
			String firstWordPos = null;
			if(!wordPosList.isEmpty()){
				//get the top one only
				firstWordPos = wordPosList.get(0);
			}
			//this constant 8/7 is being tuned! <--then make into private final int rather than 
			//leaving it here after experimentation.			
			double freqThreshold = 8/7;
			double avgWordCountWeighted = averageWordCount*freqThreshold;
			Map<String, Integer> nextWordsMap = wordMapEntry.getValue();
			int firstWordUsedCount = 0;
			for(Map.Entry<String, Integer> nextWordsMapEntry : nextWordsMap.entrySet()){				
				int nextWordCount = nextWordsMapEntry.getValue();
				String nextWord = nextWordsMapEntry.getKey();	
				if(isInvalid2ndWordEnding(nextWord)){
					continue;
				}
				nextWord = WordForms.getSingularForm(nextWord);
				//first check if undesired part of speech combination, for instance ent_verb. This is used
				//to eliminate faulty two-grams such as "field split"				
				List<String> nextWordPosList = posMMap.get(nextWord);
				if(null != firstWordPos && !nextWordPosList.isEmpty()){
					if(UNDESIRABLE_POS_COMBO_SET.contains(firstWordPos + "_" + nextWordPosList.get(0))){
						continue;
					}
				}
				//call common method, to ensure uniformity in future.
				String twoGram = WordForms.normalizeTwoGram2(word, nextWord);	
				rawTwoGramSet.add(new TwoGramFreqPair(twoGram, nextWordCount));
				firstWordUsedCount++;				
				boolean addTwoGram = false;
				if(nextWordCount > avgWordCountWeighted //|| initialTwoGramsSet.contains(twoGram)
						){
					addTwoGram = true;
				}else if(allWordsMSet.contains(word) && allWordsProbMap.containsKey(nextWord)){
					if((((double)nextWordCount)/allWordsMSet.count(word)) > SECOND_WORD_PROBA_MULT_FACTOR*allWordsProbMap.get(nextWord)){
						addTwoGram = true;
					}
				}				
				if(addTwoGram){					
					twoGramMap.put(twoGram, nextWordCount);
					//initialTwoGramsSet.remove(twoGram);
					nGramFirstWordsSet.add(word);
					totalFreqCount += nextWordCount;
					totalTwoGrams++;
					//System.out.println("new twoGram "+twoGram + " " + nextWordCount);
				}
			}
			firstWordSet.add(new TwoGramFreqPair(word, firstWordUsedCount));
		}		
		int averageTwoGramFreqCount;
		//totalTwoGrams should not be 0, unless really tiny source text set.
		if(totalTwoGrams != 0){ 
			averageTwoGramFreqCount = totalFreqCount/totalTwoGrams;
		}else{
			averageTwoGramFreqCount = ADDITIONAL_TWO_GRAM_DEFAULT_COUNT;
		}	
		System.out.println("NGramSearch - averageTwoGramFreqCount: "+ averageTwoGramFreqCount);
		averageTwoGramFreqCount = averageTwoGramFreqCount < 2 ? 2 : averageTwoGramFreqCount;
		//add additional two grams that were not programmatically selected
		for(String twoGram : ADDITIONAL_TWO_GRAMS_BUILDTIME){
			if(!twoGramMap.containsKey(twoGram)){
				twoGramMap.put(twoGram, averageTwoGramFreqCount);
			}
		}
		for(String twoGram : initialTwoGramsSet){
			//should not be in twoGramMap.
			twoGramMap.put(twoGram, averageTwoGramFreqCount);
		}
		//take away false positive two-grams
		for(String token : NOT_TWO_GRAMS){
			twoGramMap.remove(token);
		}
		
		/*StringBuilder rawTwoGramSb = new StringBuilder(100000);
		for(String twoGram : rawTwoGramSet){			
		}*/	
		FileUtils.writeToFile(rawTwoGramSet, "src/thmp/data/rawTwoGramSet.txt");
		FileUtils.writeToFile(firstWordSet, "src/thmp/data/twoGramFirstWordSet.txt");
		//System.out.println("averageFreqCount " + averageFreqCount);
		return twoGramMap;
	}
	}
	/**
	 * Adds additional words scraped from resources.
	 * @param NGRAM_DATA_FILESTR name of file containing additional n-grams
	 * and words scraped from the web.
	 * @param initialTwoGramsSet Initial set of two grams to put into map.
	 */
	private static void readAdditionalTwoGrams(BufferedReader twoGramBR, Set<String> initialTwoGramsSet){
		String word;
		try{
			while((word = twoGramBR.readLine()) != null){
				//this should have been done when sorting the words into maps
				//word = word.toLowerCase();
				if(ALPHA_PATTERN.matcher(word).matches()){
					initialTwoGramsSet.add(word);				
				}
			}
		}catch(IOException e){
			String msg = "IOException in readAdditionalTwoGrams: " +e.getMessage();
			logger.error(msg);
			throw new IllegalStateException(msg);
		}		
	}
	
	/**
	 * 
	 * @param nGramMap Expected to be empty at input
	 * @param totalWordCountsMap
	 * @param thmList
	 * @param allWordsMSet Multiset of all singleton-words. Used for computing probability (redundant with totalWordCountsMap!?)
	 * of any given word.
	 */
	private static void build2GramMap2(Map<String, Map<String, Integer>> nGramMap, Map<String, Integer> totalWordCountsMap,
			List<String> thmList, Multiset<String> allWordsMSet) {
		//skip nonMathFluffWords, collapse list
		for(String thm : thmList){
			//split into words
			thm = SENTENCE_SPACE_PATTERN.matcher(thm).replaceAll(" ");
			String[] thmAr = SENTENCE_SPLIT_PATTERN.split(thm.toLowerCase());			
			//System.out.println("thmAr: "+Arrays.toString(thmAr));
			String curWord;
			String nextWord;			
			for(int i = 0; i < thmAr.length-1; i++){
				curWord = thmAr[i];
				if("".equals(curWord)){
					continue;
				}
				nextWord = thmAr[i+1];				
				//this is a rather large set, should not include so many cases.
				//words such as "purely inseparable" might be filtered out.
				//maybe first word could be the small fluff words list from ThreeGramSearch?
				if(fluffWordsSet.contains(curWord) || curWord.length() < 3 
						|| !ALPHA_PATTERN.matcher(curWord).matches()
						|| isInvalid1stWord(curWord)
						|| WordForms.SPECIAL_CHARS_PATTERN.matcher(curWord).matches()
						|| INVALID_WORD_PATTERN.matcher(curWord).matches()){	
					//if(nonMathFluffWordsSet.contains(curWord) || curWord.length() < 2 
						//	|| curWord.matches("\\s*") || curWord.contains("\\")){	
					continue;
				}
				//screen off 2nd word more discriminantly.
				if(nextWord.length() < 3 || WordForms.SPECIAL_CHARS_PATTERN.matcher(nextWord).matches()
						|| !ALPHA_PATTERN.matcher(curWord).matches()
						|| isInvalid2ndWord(nextWord)
						|| INVALID_WORD_PATTERN.matcher(nextWord).matches()
						//|| isInvalid2ndWordEnding(nextWord)
						|| fluffWordsSet.contains(nextWord) || nonMathFluffWordsSet.contains(nextWord) 
						){					
					i++;
					continue;
				}
				//take singular forms
				//curWord = WordForms.getSingularForm(curWord);
				nextWord = WordForms.getSingularForm(nextWord);	
				
				if(fluffWordsSet.contains(WordForms.getSingularForm(curWord))){
					continue;
				}
				if(fluffWordsSet.contains(nextWord)){
					i++;
					continue;
				}				
				allWordsMSet.add(WordForms.getSingularForm(curWord));
				//add to nGramMap
				Map<String, Integer> wordMap = nGramMap.get(curWord);
				if(wordMap != null){
					Integer wordCount = wordMap.get(nextWord);
					if(wordCount != null){
						wordMap.put(nextWord, wordCount+1);
					}else{
						wordMap.put(nextWord, 1);
					}
				}else{
					wordMap = new HashMap<String, Integer>();
					wordMap.put(nextWord, 1);
					nGramMap.put(curWord, wordMap);
				}
				Integer curWordCount = totalWordCountsMap.get(curWord);
				if(curWordCount != null){
					totalWordCountsMap.put(curWord, curWordCount+1);
				}else{
					totalWordCountsMap.put(curWord, 1);
				}				
			}
		}		
	}
	
	/**
	 * Invalid -ing ending of 2nd word, such as "number dividing", but not 
	 * "sobolev embedding", or "associative ring", "... mapping"
	 * @param nextWord
	 * @return
	 */
	protected static boolean isInvalid2ndWordEnding(String nextWord) {
		int nextWordLen = nextWord.length();
		if(nextWordLen < 5 || !"ing".equals(nextWord.substring(nextWordLen-3)) 
				|| VALID_ING_WORD_ENDING_SET.contains(nextWord)){
			return false;
		}
		return true;
	}

	/**
	 * Invalid -ing ending of first word, such as "satisfying", but not 
	 * "embedding dimension", "mapping group", etc
	 * @param nextWord
	 * @return
	 */
	protected static boolean isInvalid1stWord(String nextWord) {		
		return INVALID_FIRST_WORD_SET.contains(nextWord);		
	}
	
	protected static boolean isInvalid2ndWord(String nextWord) {		
		return INVALID_SECOND_WORD_SET.contains(nextWord);		
	}
	
	/**
	 * Iterate through all the words. Record the frequency counts.
	 * Skip if a frequent word as recorded in English frequent words list.
	 * @param totalWordCounts Total counts of words that follow this word, equivalent to size of map in
	 * nGramMap corresponding to this key word.
	 * @param nGramMap Map of maps. Keys are words in text, and entries are maps whose keys are 2nd terms
	 * in 2 grams, and entries are frequency counts. I.e. map of words that show up, and the words that 
	 * immediately follow and their counts.
	 */
	public static void build2GramMap(List<String> thmList,
			Map<String, Map<String, Integer>> nGramMap, Map<String, Integer> totalWordCounts){	
		Multiset<String> allWordsMSet = HashMultiset.create();
		build2GramMap2(nGramMap, totalWordCounts, thmList, allWordsMSet);
	}
	
	/**
	 * Computes the average frequency counts following each word from the given maps.
	 * @param nGramMap
	 * @param totalWordCounts Number of times the word shows up in corpus (and followed by valid word).
	 * @return map 
	 */
	private static Map<String, Integer> computeAverageFreq(Map<String, Map<String, Integer>> nGramMap, Map<String, Integer> totalWordCounts){
		Map<String, Integer> averageWordCounts = new HashMap<String, Integer>();
		//map of second words and the frequencies of the first+second words occurring together
		for(Map.Entry<String, Map<String, Integer>> wordMapEntry : nGramMap.entrySet()){
			String word = wordMapEntry.getKey();
			int wordMapSz = wordMapEntry.getValue().size();
			int totalWordCount = totalWordCounts.get(word);
			//System.out.println("totalWordCount "+totalWordCount);
			int averageWordCount = Math.round(totalWordCount/wordMapSz);
			averageWordCounts.put(word, averageWordCount);
		}
		return averageWordCounts;
	}
	
	/**
	 * Get 2-grams and their frequencies.
	 * @return
	 */
	public static Map<String, Integer> get2GramsMap(){
		return TwoGramSearch.twoGramsMap;
	}

	/*public static int averageTwoGramFreqCount(){
		return TwoGramSearch.averageTwoGramFreqCount;
	}*/
	
	/**
	 * Get 2-grams and their frequencies.
	 * @return
	 */
	/*public static Map<String, Map<String, Integer>> get_twoGramTotalOccurenceMap(){
		return TwoGramSearch.twoGramTotalOccurenceMap;
	}*/	
	
	/**
	 * 
	 * @return Set of first words in the collected 2-grams.
	 */
	public static Set<String> get_2GramFirstWordsSet(){
		return TwoGramSearch.twoGramFirstWordsSet;
	}

	/**
	 * Write 2-grams to file.
	 * Should also write freqencies to file.
	 */
	private static void write2gramsToFile(Map<String, Integer> twoGramsMap) {		
		FileUtils.writeToFile(new ArrayList<String>(twoGramsMap.keySet()), twoGramsFilePath);
	}	
	
	/*public static void main(String[] args){
		//System.out.println(CollectThm.get_wordsScoreMapNoAnno());
		boolean write2gramsToFile = false;
		if(write2gramsToFile){ 
			write2gramsToFile(TwoGramSearch.twoGramsMap);		
		}
	}*/
	
}
