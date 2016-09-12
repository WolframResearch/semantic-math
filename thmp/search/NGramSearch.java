package thmp.search;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import thmp.ProcessInput;
import thmp.utils.FileUtils;
import thmp.utils.WordForms;

/**
 * Fish out 2-grams, compute probabilities of one word following another.
 * O(N^2) time performance, where N is number of words.
 * Put words in HashMaps, where each entry is another map, with frequencies of words
 * that follow. N^2 space. Skip if common word.
 * F
 * @author yihed
 *
 */

public class NGramSearch {

	//get the non math fluff words
	private static final Set<String> nonMathFluffWordsSet = CollectFreqWords.GetFreqWords.get_nonMathFluffWordsSet2();	
	private static final Path twoGramsFilePath = Paths.get("src/thmp/data/twoGrams.txt");
	//private static final File twoGramsFile = new File("src/thmp/data/twoGrams.txt");
	//list of 2 grams that show up with above-average frequency, with their frequencies
	private static final Map<String, Integer> twoGramsMap;
	private static final String[] ADDITIONAL_TWO_GRAMS = new String[]{"local ring", "local field"};
	
	static{
		//System.out.println("Gathering 2-grams...");
		Map<String, Map<String, Integer>> nGramMap = new HashMap<String, Map<String, Integer>>();
		Map<String, Integer> totalWordCounts = new HashMap<String, Integer>();
		recordCounts(nGramMap, totalWordCounts);
		
		Map<String, Integer> averageWordCounts = computeAverageFreq(nGramMap, totalWordCounts);
		
		//get list of 2 grams that show up frequently
		twoGramsMap = compile2grams(nGramMap, averageWordCounts);
		//System.out.println("twoGramsMapSz" + twoGramsMap.size());
		//System.out.println(twoGramsMap);
		System.out.println("Done with 2-grams.");
	}
	
	//private static Map<String, HashMap<String, Integer>> nGramMap;
	
	/**
	 * Iterate through all the words. Record the frequency counts.
	 * Skip if a frequent word.
	 * @param averageCounts records the average frequency counts of words that follow
	 * each word that's its key in this map. This is used to compute the average freq of words
	 * that follow a given word. This is less than the total count of that key word in the document, 
	 * as fluff words are skipped.
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
				
				if(nonMathFluffWordsSet.contains(curWord) || curWord.matches("\\s*") || curWord.length() < 2){					
					continue;
				}
				
				if(nonMathFluffWordsSet.contains(nextWord)
					|| nextWord.matches("\\s*") || nextWord.length() < 2){					
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
	 * compile list of 2 grams. Retain ones that show up higher than 3/2 of avg frequency.
	 * @return 2 grams, and their raw frequencies in doc.
	 */
	private static Map<String, Integer> compile2grams(Map<String, Map<String, Integer>> nGramMap, Map<String, Integer> averageWordCounts){
		Map<String, Integer> twoGramMap = new HashMap<String, Integer>();
		//System.out.print(nGramMap);
		
		int totalFreqCount = 0;	
		int totalTwoGrams = 0;
		for(Map.Entry<String, Map<String, Integer>> wordMapEntry : nGramMap.entrySet()){
			String word = wordMapEntry.getKey();
			int averageWordCount = averageWordCounts.get(word);
			Map<String, Integer> nextWordsMap = wordMapEntry.getValue();
			for(Map.Entry<String, Integer> nextWordsMapEntry : nextWordsMap.entrySet()){
				int nextWordCount = nextWordsMapEntry.getValue();
				//this constant 3/2 needs to be tuned!
				if(nextWordCount > averageWordCount*3/2){
					String nextWord = nextWordsMapEntry.getKey();
					String twoGram = word + " " + nextWord;					
					twoGramMap.put(twoGram, nextWordCount);
					totalFreqCount += nextWordCount;
					totalTwoGrams++;
					//System.out.println(twoGram + " " + nextWordCount);
				}
			}
		}
		//add additional two grams that were not programmatically selected
		//
 		int averageFreqCount = totalFreqCount/totalTwoGrams;
		for(String twoGram : ADDITIONAL_TWO_GRAMS){
			twoGramMap.put(twoGram, averageFreqCount);
		}
		return twoGramMap;
	}

	/**
	 * Get 2-grams and their frequencies.
	 * @return
	 */
	public static Map<String, Integer> get2GramsMap(){
		return twoGramsMap;
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
		if(write2gramsToFile) write2gramsToFile(twoGramsMap);
	}
	
}
