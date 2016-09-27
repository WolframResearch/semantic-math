package thmp.search;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import thmp.ProcessInput;
import thmp.search.CollectThm.ThmList;
import thmp.utils.FileUtils;
import thmp.utils.WordForms;

/**
 * Produces set of words that are English fluff words, and less likely to be
 * useful math words, e.g. excludes words such as "prime", "regular", "ring"
 * Computes word frequency in input. Computes the
 * frequency of words in math text, if drastically higher than common English
 * words frequency, then don't count as fluff word, e.g. regular, prime. Words
 * are processed the same way as the in CollectThm, eg singularize, etc.
 */
public class WordFrequency {

	// map of every word in thmList and their frequencies
	private static final Map<String, Integer> corpusWordFreqMap = new HashMap<String, Integer>();
	// map of common words and freq as read in from wordFrequency.txt
	private static final Map<String, Integer> stockFreqMap = new HashMap<String, Integer>();
	
	//subset of words and their pos from the stock file that only stores word and its pos of true fluff words
	private static final Map<String, String> trueFluffWordsPosMap = new HashMap<String, String>();
	
	private static final Set<String> trueFluffWordsSet = new HashSet<String>();
	private static final String WORDS_FILE_STR = "src/thmp/data/wordFrequency.txt";
	//450 million total words in stock word frequency list
	private static final int TOTAL_STOCK_WORD_COUNT = (int)Math.pow(10, 7)*45;
	private static final Path trueFluffWordsPath = Paths.get("src/thmp/data/trueFluffWords.txt");
	
	//words that should be included in trueFluffWordsSet, but were left out by algorithm.
	private static final String[] ADDITIONAL_FLUFF_WORDS = new String[]{"an", "are", "has", "tex"};
	
	static {
		
		// build wordFreqMap
		List<String> extractedThms = ThmList.get_thmList();
		// the third boolean argument means to extract words from latex symbols,
		// eg oplus->direct sum.
		List<String> thmList = ProcessInput.processInput(extractedThms, true, false);
		
		int totalCorpusWordCount = extractFreq(thmList);
		
		FileReader wordsFileReader;
		try {
			wordsFileReader = new FileReader(WORDS_FILE_STR);
			BufferedReader wordsFileBufferedReader = new BufferedReader(wordsFileReader);
			//fills in trueFluffWordsSet, based on freq in stockFreqMap
			getStockFreq(wordsFileBufferedReader, totalCorpusWordCount);
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		
	}

	private static int extractFreq(List<String> thmList) {
		int totalCorpusWordCount = 0;
		for (int i = 0; i < thmList.size(); i++) {
			String thm = thmList.get(i);

			String[] thmAr = thm.toLowerCase().split(WordForms.splitDelim());

			for (int j = 0; j < thmAr.length; j++) {

				String word = thmAr[j];
				// only keep words with lengths > 2
				// System.out.println(word);
				if (word.length() < 3 || word.contains("\\"))
					continue;

				// get singular forms if plural, put singular form in map
				// Note, some words shouldn't need to be converted to singular
				// form!
				word = WordForms.getSingularForm(word);

				Integer wordFreq = corpusWordFreqMap.get(word);
				if (wordFreq != null) {
					corpusWordFreqMap.put(word, wordFreq + 1);
				} else {
					corpusWordFreqMap.put(word, 1);
				}
				totalCorpusWordCount++;
			}
			
		}
		return totalCorpusWordCount;
	}

	// computes and returns list of "true" fluff words, ie the ones where
	// whose freq in math texts are not higher than their freq in English text.
	// have to be careful for words such as "let", "say"
	private static void getStockFreq(BufferedReader wordsFileBufferedReader, int totalCorpusWordCount) {
		
		try {
			wordsFileBufferedReader.readLine();
			/*int N = 80;
			//the first N lines are both math and English fluff words, eg "the"
			for(int i = 0; i < N; i++){
				wordsFileBufferedReader.readLine();				
			}*/
			
			String line;
			//read in words and 
			while ((line = wordsFileBufferedReader.readLine()) != null) {
				String[] lineAr = line.split("\\s+");
				
				if(lineAr.length < 4) continue;
				
				// 2nd is word, 4rd is freq
				String word = lineAr[1].trim();
				
				String wordPos = CollectFreqWords.getPos(word, lineAr[2].trim());
				
				int wordFreq = Integer.valueOf(lineAr[3].trim());
				
				stockFreqMap.put(word, wordFreq);
				Integer wordCorpusFreq = corpusWordFreqMap.get(word);
				if(wordCorpusFreq != null){
					
					//put in trueFluffWordsSet if below twice the stock freq
					if((double)wordCorpusFreq / totalCorpusWordCount < 2*(double)wordFreq / TOTAL_STOCK_WORD_COUNT){
						trueFluffWordsSet.add(word);
						trueFluffWordsPosMap.put(word, wordPos);
					}
				}else{
					trueFluffWordsSet.add(word);
					trueFluffWordsPosMap.put(word, wordPos);
				}
			}
			for(String fluffWord : ADDITIONAL_FLUFF_WORDS){
				trueFluffWordsSet.add(fluffWord);
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Retrieves word-freq map for every word in corpus.
	 * 
	 * @return
	 */
	public static Map<String, Integer> corpusWordFreqMap() {
		return corpusWordFreqMap;
	}

	/**
	 * Retrieves set of true fluff words.
	 * @return
	 */
	public static Set<String> trueFluffWordsSet() {
		return trueFluffWordsSet;
	}	

	/**
	 * Retrieves map of true fluff words and their pos. 
	 * @return
	 */
	public static Map<String, String> trueFluffWordsPosMap() {
		return trueFluffWordsPosMap;
	}	

	/**
	 * Computes frequency of words in a sentence, store in map.
	 * *Not* used right now.
	 * @deprecated
	 * @param sentence
	 * @return
	 */
	public static Map<String, Integer> wordFreq(String input) {
		// keys are words, values are frequencies
		Map<String, Integer> freqMap = new HashMap<String, Integer>();
		// split into words
		String[] inputAr = input.split(" |\\.|\\,|\'|\"|\\(|\\)");
		for (String word : inputAr) {
			Integer wordFreq = freqMap.get(word);
			if (wordFreq == null) {
				freqMap.put(word, wordFreq + 1);
			} else {
				freqMap.put(word, 1);
			}
		}

		return freqMap;
	}

	public static void main(String[] args) {
		//System.out.println(trueFluffWordsSet.size());
		//System.out.println(trueFluffWordsSet);
		boolean writeToFile = false;
		if(writeToFile){
			List<String> trueFluffWordsList = new ArrayList<String>(trueFluffWordsSet);
			FileUtils.writeToFile(trueFluffWordsList, trueFluffWordsPath);
		}
	}
}
