package thmp.search;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;

import thmp.ProcessInput;
import thmp.utils.FileUtils;
import thmp.utils.WordForms;

/**
 * Fish out 2-grams, compute probabilities of one word following another.
 * O(N^2) time performance, where N is number of words.
 * Put words in HashMaps, where each entry is another map, with frequencies of words
 * that follow. N^2 space. Skip if common word.
 * 
 * @author yihed
 *
 */

public class NGramSearch {

	//get the non math fluff words
	private static final Set<String> nonMathFluffWordsSet = WordFrequency.ComputeFrequencyData.trueFluffWordsSet();	
	//file to write 2 grams to
	private static final Path twoGramsFilePath = Paths.get("src/thmp/data/twoGrams.txt");
	
	private static volatile BufferedReader TWO_GRAM_DATA_BR;
	private static volatile BufferedReader THREE_GRAM_DATA_BR;
	
	// default two-gram averageFreqCount when total number of two grams is 0
	private static final int ADDITIONAL_TWO_GRAM_DEFAULT_COUNT = 5;
	//words to be taken off of two-gram map
	private static final String[] NOT_TWO_GRAMS = new String[]{"field is", "more generally"};
	
	//should use this to detect fluff in first word.
	private static final Set<String> fluffWordsSet = WordForms.makeFluffSet();
	
	/**
	 * Set the BufferedReader for scraped two grams.
	 * @param bf
	 */
	public static void setTwoGramBR(BufferedReader br){
		TWO_GRAM_DATA_BR = br;
	}
	
	/**
	 * Set the BufferedReader for scraped two grams.
	 * @param bf
	 */
	public static void setThreeGramBR(BufferedReader br){
		THREE_GRAM_DATA_BR = br;
	}
	
	public static BufferedReader THREE_GRAM_DATA_BR(){
		return THREE_GRAM_DATA_BR;
	}
	
	public static class TwoGramSearch{
		//private static final File twoGramsFile = new File("src/thmp/data/twoGrams.txt");
		//list of 2 grams that show up with above-average frequency, with their frequencies
		private static final Map<String, Integer> twoGramsMap;
		
		//undesirable part of speech combos for two grams, e.g. ent_verb.
		private static final Set<String> UNDESIRABLE_POS_COMBO_SET;
		
		private static final String[] ADDITIONAL_TWO_GRAMS = new String[]{"local field", "direct sum",
				"finitely many", "open mapping", "finite type", "finite presentation", "principal ideal",
				"noetherian ring"};
		
		// name of two gram data file containing additional 2-grams that should be included. These don't have
		// frequencies associated with them. Load these into maps first and accumulate their frequencies.
		private static final String TWO_GRAM_DATA_FILESTR = "src/thmp/data/twoGramData.txt";		
		
		private static int averageTwoGramFreqCount;
		
		//nGramMap Map of maps. Keys are words in text, and entries are maps whose keys are 2nd terms
		// in 2 grams, and entries are frequency counts.
		//this field will be exposed to build 3-grams
		private static final Map<String, Map<String, Integer>> nGramMap;
		
		//set that contains the first word of each n-grams.
		private static final Set<String> nGramFirstWordsSet;
		static{
			
			UNDESIRABLE_POS_COMBO_SET = new HashSet<String>();
			String[] undesirablePosAr = new String[]{"ent_verb", "ent_vbs", "ent_verbAlone"};
			UNDESIRABLE_POS_COMBO_SET.addAll(Arrays.asList(undesirablePosAr));
			
			//System.out.println("Gathering 2-grams...");
			nGramMap = new HashMap<String, Map<String, Integer>>();
			//total word counts of all 2 grams
			Map<String, Integer> totalWordCounts = new HashMap<String, Integer>();
			//build nGram map, 
			recordCounts(nGramMap, totalWordCounts);
	
			//computes the average frequencies of words that follow the first word in all 2-grams
			Map<String, Integer> averageWordCounts = computeAverageFreq(nGramMap, totalWordCounts);
			
			//set of scraped two grams read in from two grams file.
			Set<String> initialTwoGramsSet = new HashSet<String>();
			//fills in initialTwoGramsSet with scraped two gram starter set
			if(TWO_GRAM_DATA_BR == null){
				try{
					BufferedReader twoGramBF = new BufferedReader(new FileReader(TWO_GRAM_DATA_FILESTR));
					readAdditionalTwoGrams(twoGramBF, initialTwoGramsSet);
				}catch(FileNotFoundException e){
					e.printStackTrace();
					throw new RuntimeException(e);
				}
			}else{
				readAdditionalTwoGrams(TWO_GRAM_DATA_BR, initialTwoGramsSet);
			}
			nGramFirstWordsSet = new HashSet<String>();
			//get list of 2 grams that show up frequently
			twoGramsMap = compile2grams(nGramMap, averageWordCounts, nGramFirstWordsSet, initialTwoGramsSet);
			//System.out.println("twoGramsMapSz" + twoGramsMap.size());
			System.out.println(twoGramsMap);
			System.out.println("Done with 2-grams.");
		}
	
	/**
	 * compile list of 2 grams. Retain ones that show up higher than 3/2 of avg frequency.
	 * @return 2 grams, and their raw frequencies in doc.
	 */
	private static Map<String, Integer> compile2grams(Map<String, Map<String, Integer>> nGramMap, Map<String, Integer> averageWordCounts,
			Set<String> nGramFirstWordsSet, Set<String> initialTwoGramsSet){
		
		ListMultimap<String, String> posMMap = thmp.Maps.posMMap();
		
		Map<String, Integer> twoGramMap = new HashMap<String, Integer>();		
		int totalFreqCount = 0;	
		int totalTwoGrams = 0;
		for(Map.Entry<String, Map<String, Integer>> wordMapEntry : nGramMap.entrySet()){
			String word = wordMapEntry.getKey();
			int averageWordCount = averageWordCounts.get(word);
			List<String> wordPosList = posMMap.get(word);
			String firstWordPos = null;
			if(!wordPosList.isEmpty()){
				//get the top one only
				firstWordPos = wordPosList.get(0);
			}
			
			Map<String, Integer> nextWordsMap = wordMapEntry.getValue();
			
			for(Map.Entry<String, Integer> nextWordsMapEntry : nextWordsMap.entrySet()){
				int nextWordCount = nextWordsMapEntry.getValue();
				String nextWord = nextWordsMapEntry.getKey();
				String twoGram = word + " " + nextWord;
				//this constant 3/2 needs to be tuned! <--then make into private final int rather than 
				//leaving it here after experimentation.
				double freqThreshold = 3/2;
				//first check if undesired part of speech combination, for instance ent_verb. This is used
				//to eliminate faulty two-grams such as "field split"				
				List<String> nextWordPosList = posMMap.get(nextWord);
				if(null != firstWordPos && !nextWordPosList.isEmpty()){
					if(UNDESIRABLE_POS_COMBO_SET.contains(firstWordPos + "_" + nextWordPosList.get(0))){
						continue;
					}
				}
				
				if(nextWordCount > averageWordCount*freqThreshold || initialTwoGramsSet.contains(twoGram)){							
					twoGramMap.put(twoGram, nextWordCount);
					initialTwoGramsSet.remove(twoGram);
					nGramFirstWordsSet.add(word);
					totalFreqCount += nextWordCount;
					totalTwoGrams++;
					//System.out.println("new twoGram "+twoGram + " " + nextWordCount);
				}
			}
		}
		//totalTwoGrams should not be 0, unless really tiny source text set.
		if(totalTwoGrams != 0){ 
			averageTwoGramFreqCount = totalFreqCount/totalTwoGrams;
		}else{
			averageTwoGramFreqCount = ADDITIONAL_TWO_GRAM_DEFAULT_COUNT;
		}	
		//add additional two grams that were not programmatically selected		
		for(String twoGram : ADDITIONAL_TWO_GRAMS){
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
		/*BufferedReader fileBufferedReader;
		try{
			fileBufferedReader = new BufferedReader(new FileReader(TWO_GRAM_DATA_FILESTR));
		}catch(FileNotFoundException e){
			e.printStackTrace();
			return;
		}*/
		String word;
		try{
			while((word = twoGramBR.readLine()) != null){
				//this should have been done when sorting the words into maps
				//word = word.toLowerCase();						
				initialTwoGramsSet.add(word);				
			}
		}catch(IOException e){
			e.printStackTrace();
		}		
	}
	
	/**
	 * Iterate through all the words. Record the frequency counts.
	 * Skip if a frequent word.
	 * @param averageCounts records the average frequency counts of words that follow
	 * each word that's its key in this map. This is used to compute the average freq of words
	 * that follow a given word. This is less than the total count of that key word in the document, 
	 * as fluff words are skipped.
	 * @param totalWordCounts Total counts of words that follow this word, equivalent to size of map in
	 * nGramMap corresponding to this key word.
	 * @param nGramMap Map of maps. Keys are words in text, and entries are maps whose keys are 2nd terms
	 * in 2 grams, and entries are frequency counts.
	 */
	private static void recordCounts(Map<String, Map<String, Integer>> nGramMap, Map<String, Integer> totalWordCounts){
		//map of words that show up, and the words that immediately follow and their counts.
		//nGramMap = new HashMap<String, Map<String, Integer>>();
		//get thmList from CollectThm
		List<String> thmList = ProcessInput.processInput(CollectThm.ThmList.get_thmList(), true);
		//System.out.println(thmList);
		//skip nonMathFluffWords, collapse list
		for(String thm : thmList){
			//split into words
			String[] thmAr = thm.toLowerCase().split(WordForms.splitDelim());
			String curWord;
			String nextWord;
			
			for(int i = 0; i < thmAr.length-1; i++){
				curWord = thmAr[i];
				nextWord = thmAr[i+1];
				
				//take singular forms
				curWord = WordForms.getSingularForm(curWord);
				nextWord = WordForms.getSingularForm(nextWord);					
				
				//this is a rather large set, should not include so many words.
				//words such as "purely inseparable" might be filtered out.
				//maybe first word could be the small fluff words list from ThreeGramSearch?
				if(fluffWordsSet.contains(curWord) || curWord.length() < 2 
						|| curWord.matches("\\s*") || curWord.contains("\\") || curWord.contains("$")){	
					//if(nonMathFluffWordsSet.contains(curWord) || curWord.length() < 2 
						//	|| curWord.matches("\\s*") || curWord.contains("\\")){	
					continue;
				}
				//screen off 2nd word more discriminantly.
				if(fluffWordsSet.contains(nextWord) || nonMathFluffWordsSet.contains(nextWord) 
						|| nextWord.length() < 2 || nextWord.matches("\\s*") || nextWord.contains("\\")
						|| nextWord.contains("$")){					
					i++;
					continue;
				}
				
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
				Integer curWordCount = totalWordCounts.get(curWord);
				if(curWordCount != null){
					totalWordCounts.put(curWord, curWordCount+1);
				}else{
					totalWordCounts.put(curWord, 1);
				}
				
			}
		}
	}
	
	/**
	 * Computes the average frequency counts following each word from the given maps.
	 * @param nGramMap
	 * @param totalWordCounts
	 * @return map 
	 */
	private static Map<String, Integer> computeAverageFreq(Map<String, Map<String, Integer>> nGramMap, Map<String, Integer> totalWordCounts){
		Map<String, Integer> averageWordCounts = new HashMap<String, Integer>();
		
		for(Map.Entry<String, Map<String, Integer>> wordMapEntry : nGramMap.entrySet()){
			String word = wordMapEntry.getKey();
			int wordMapSz = wordMapEntry.getValue().size();
			int totalWordCount = totalWordCounts.get(word);
			int averageWordCount = Math.round(wordMapSz/totalWordCount);
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

	public static int averageTwoGramFreqCount(){
		return TwoGramSearch.averageTwoGramFreqCount;
	}
	
	/**
	 * Get 2-grams and their frequencies.
	 * @return
	 */
	public static Map<String, Map<String, Integer>> get_nGramMap(){
		return TwoGramSearch.nGramMap;
	}	
	
	/**
	 * 
	 * @return Set of first words in the collected 2-grams.
	 */
	public static Set<String> get_2GramFirstWordsSet(){
		return TwoGramSearch.nGramFirstWordsSet;
	}

	/**
	 * Write 2-grams to file.
	 * Should also write freqencies to file.
	 */
	private static void write2gramsToFile(Map<String, Integer> twoGramsMap) {		
		FileUtils.writeToFile(new ArrayList<String>(twoGramsMap.keySet()), twoGramsFilePath);
	}	
	
	public static void main(String[] args){
		//System.out.println(CollectThm.get_wordsScoreMapNoAnno());
		boolean write2gramsToFile = false;
		if(write2gramsToFile){ 
			write2gramsToFile(TwoGramSearch.twoGramsMap);		
		}
	}
	
}
