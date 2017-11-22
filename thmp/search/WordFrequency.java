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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;

import thmp.parse.ProcessInput;
import thmp.search.CollectThm.ThmList;
import thmp.search.Searcher.SearchMetaData;
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
	//**June 2017 private static final Map<String, Integer> corpusWordFreqMap = new HashMap<String, Integer>();
	private static final Logger logger = LogManager.getLogger(WordFrequency.class);
	
	//private static final int TOTAL_CORPUS_WORD_COUNT;
	private static final Path trueFluffWordsPath = Paths.get("src/thmp/data/trueFluffWords.txt");
	
	//450 million total words in stock word frequency list
	private static final int TOTAL_STOCK_WORD_COUNT = (int)Math.pow(10, 7)*45;
	static {		
		// build wordFreqMap
		//List<String> extractedThms = ThmList.allThmsWithHypList();
		// the boolean argument indicates whether to replace tex symbols with "tex"
		//fills corpusWordFreqMap and gets total word count
		//serialize 
		//TOTAL_CORPUS_WORD_COUNT = extractFreq(thmList);
	}

	/**
	 * fills corpusWordFreqMap and gets total word count.
	 * @param thmList
	 * @param corpusWordFreqMap
	 * @return
	 */
	public static int extractThmAllWordsFrequency(List<String> thmList, 
			Map<String, Integer> corpusWordFreqMap) {
		int totalCorpusWordCount = 0;
		for (int i = 0; i < thmList.size(); i++) {
			String thm = thmList.get(i);
			//String[] thmAr = thm.toLowerCase().split(WordForms.splitDelim());
			List<String> thmAr = WordForms.splitThmIntoSearchWordsList(thm.toLowerCase());
			int thmArSz = thmAr.size();
			for (int j = 0; j < thmArSz; j++) {
				
				String word = thmAr.get(j);
				// only keep words with lengths > 2
				if (word.length() < 3 || WordForms.SPECIAL_CHARS_PATTERN.matcher(word).matches()){
					continue;
				}
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

	/**
	 * Dealing with frequency data as stored in wordFrequency.txt.
	 * @author yihed
	 */
	public static class ComputeFrequencyData{
		
		private static final String WORDS_FILE_STR = "src/thmp/data/wordFrequency.txt";
		//private static BufferedReader wordFrequencyBR;
		private static final ImmutableMap<String, String> wordPosMap;
		// map of common words and freq as read in from wordFrequency.txt
		private static final Map<String, Integer> stockFreqMap = new HashMap<String, Integer>();
		
		//subset of words and their pos from the stock file that only stores word and its pos of true fluff words
		//***private static ListMultimap<String, String> trueFluffWordsPosMap;
		
		private static final Set<String> trueFluffWordsSet;

		//words that should be included in trueFluffWordsSet, but were left out by algorithm.
		private static final String[] ADDITIONAL_FLUFF_WORDS = new String[]{"an", "are", "has", "tex", "between",
				"call", "does", "do", "equation"/*equation because we don't strip \begin{equation} during preprocessing
				which inflates this word, and incorrectly displays it in the web frontend*/
				};
		
		static{
			Map<String, String> wordPosPreMap = new HashMap<String, String>();
			ServletContext servletContext = CollectThm.getServletContext();
			//wordFrequencyBR = CollectThm.get_wordFrequencyBR();
			BufferedReader wordsFileBufferedReader = null;
			if(null == servletContext){
				try {
					FileReader wordsFileReader = new FileReader(WORDS_FILE_STR);
					wordsFileBufferedReader = new BufferedReader(wordsFileReader);					
				} catch (FileNotFoundException e) {
					e.printStackTrace();
					throw new RuntimeException(e);
				}
			}else{
				InputStream wordsInputStream = servletContext.getResourceAsStream(WORDS_FILE_STR);
				wordsFileBufferedReader = new BufferedReader(new InputStreamReader(wordsInputStream));				
			}
			
			//fills in trueFluffWordsSet, based on freq in stockFreqMap. Updates stockFreqMap.
			getStockFreq(wordsFileBufferedReader, wordPosPreMap);
			try{
				wordsFileBufferedReader.close();
			}catch(IOException e){
				e.printStackTrace();
				String msg = "IOException while closing buffered reader! " + e.getStackTrace();
				logger.error(msg);
				throw new IllegalStateException(msg);
				
			}			
			wordPosMap = ImmutableMap.copyOf(wordPosPreMap);
			
			String trueFluffWordsSetPath = FileUtils.getPathIfOnServlet(SearchMetaData.trueFluffWordsSetPath());
			@SuppressWarnings("unchecked")
			Set<String> set = ((List<Set<String>>)FileUtils.deserializeListFromFile(trueFluffWordsSetPath)).get(0);
			trueFluffWordsSet = set;
			
			//build trueFluffWordsSet
			//buildTrueFluffWordsSet(totalCorpusWordCount, stockFreqMap, wordPosMap, corpusWordFreqMap);
		}
		
		/**
		 * Common English words and their absolute frequencies in an 
		 * externally obtained table.
		 * @return
		 */
		public static Map<String, Integer> englishStockFreqMap(){
			return stockFreqMap;
		}
		
		/**
		 * computes list of "true" fluff words, ie the ones where
		// whose freq in math texts are not higher than their freq in English text.
		// have to be careful for words such as "let", "say"
		 * 
		 * @param wordsFileBufferedReader
		 * @param wordPosPreMap
		 */
		private static void getStockFreq(BufferedReader wordsFileBufferedReader,
				Map<String, String> wordPosPreMap) {
			
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
					String wordPos = getPos(word, lineAr[2].trim());					
					wordPosPreMap.put(word, wordPos);					
					int wordFreq = Integer.valueOf(lineAr[3].trim());					
					stockFreqMap.put(word, wordFreq);				
				}
				
			} catch (IOException e) {
				e.printStackTrace();
				String msg = "IOException thrown in getStockFreq()" + e.getStackTrace();
				logger.error(msg);
				throw new IllegalStateException(msg);
			}
		}
	
		/**
		 * Builds trueFluffWordsSet.
		 * Deliberately include the static variables in the parameter set, to highlight the dependency on them.
		 * @param totalCorpusWordCount
		 */
		public static void buildTrueFluffWordsSet(Set<String> trueFluffWordsSet,
				//Map<String, Integer> stockFreqMap, Map<String, String> wordPosMap, 
				Map<String, Integer> corpusWordFreqMap,
				int TOTAL_CORPUS_WORD_COUNT){
			
			//trueFluffWordsPosMap = ArrayListMultimap.create();
			 //= new HashSet<String>();
			
			for(Map.Entry<String, Integer> wordFreqEntry: stockFreqMap.entrySet()){
				String word = wordFreqEntry.getKey();
				Integer wordFreq = wordFreqEntry.getValue();
				String wordPos = wordPosMap.get(word);
				
				Integer wordMathCorpusFreq = corpusWordFreqMap.get(word);
				if(wordMathCorpusFreq != null){					
					//put in trueFluffWordsSet if below twice the stock freq, to filter out English words such as
					// "is", "the", etc. Math words occur much more frequently in math corpus than their avg English
					//frequency. Experiment with the 2 here!!
					if((double)wordMathCorpusFreq / TOTAL_CORPUS_WORD_COUNT < 2*(double)wordFreq / TOTAL_STOCK_WORD_COUNT){
						trueFluffWordsSet.add(word);
						//trueFluffWordsPosMap.put(word, wordPos);
					}
				}else{
					trueFluffWordsSet.add(word);
					//trueFluffWordsPosMap.put(word, wordPos);
				}
			}			
			for(String fluffWord : ADDITIONAL_FLUFF_WORDS){
				trueFluffWordsSet.add(fluffWord);
			}			
		}

		/**
		 * Gets only the non math common words.
		 * Gets non math fluff words differently, by taking the math words that
		 * are marked as fluff out of the wordPosMap.
		 * 
		 * @return
		 */
		public static ImmutableSet<String> get_nonMathFluffWordsSet2() {
			Set<String> nonMathFluffWordsSet = new HashSet<String>(wordPosMap.keySet());
			String[] score1mathWords = CollectThm.scoreAvgMathWords();
			String[] additionalFluffWords = CollectThm.additionalFluffWords();

			for (String word : score1mathWords) {
				nonMathFluffWordsSet.remove(word);
			}

			for (String word : additionalFluffWords) {
				nonMathFluffWordsSet.add(word);
			}
			return ImmutableSet.copyOf(nonMathFluffWordsSet);
		}
		
		/**
		 *  Gets the most frequent words.
		 * @return List of frequent word forms, used to not unnecessarily
		 * further process words.
		 */
		public static ImmutableSet<String> get_FreqWords() {
			
			Set<String> freqWordsSet = new HashSet<String>(wordPosMap.keySet());		
			String[] additionalFluffWords = CollectThm.additionalFluffWords();
			
			for (String word : additionalFluffWords) {
				freqWordsSet.add(word);
			}
			
			return ImmutableSet.copyOf(freqWordsSet);
		}
		
		/**
		 * Retrieves set of true fluff words.
		 * @return
		 */
		public static Set<String> trueFluffWordsSet() {	
			return trueFluffWordsSet;
		}	

		/**
		 * Retrieves map of true fluff words and their pos. Subset of
		 * freqWordsPosMap().
		 * @return
		 */
		/*public static ListMultimap<String, String> trueFluffWordsPosMap() {
			if(trueFluffWordsPosMap == null){
				synchronized(WordFrequency.class){
					if(trueFluffWordsPosMap == null){
						//build trueFluffWordsSet
						buildTrueFluffWordsSet(stockFreqMap, wordPosMap, corpusWordFreqMap);
					}
				}
			}
			return trueFluffWordsPosMap;
		}*/	

		/**
		 * Retrieves map of true fluff words and their pos. 
		 * @return
		 */
		public static Map<String, String> freqWordsPosMap() {			
			return wordPosMap;
		}
		
		/**
		 * Get the part of speech corresponding to the pos tag/symbol.
		 * E.g. i -> "pre". Placed here instead of in subclass, so it can
		 * be used by WordFrequency.java as well.
		 * @param word
		 * @param wordPos
		 * @return
		 */
		public static String getPos(String word, String wordPos){
			String pos;
			switch (wordPos) {
			case "i":
				pos = "pre";
				break;
			case "p":
				pos = "pro";
				break;
			case "v":
				pos = "verb";
				break;
			case "n":
				pos = "ent"; //="noun"
				break;
			case "x":
				// not, no etc
				pos = "not";
				break;
			case "d":
				// determiner
				pos = "det";
				break;
			case "j":
				pos = "adj";
				break;
			case "r":
				pos = "adverb";
				break;
			case "e":
				// "existential there"
				pos = "det";
				break;
			case "a":
				// article, eg the, every, a.
				// classify as adj because of the rules for
				// fusing adj and ent's
				pos = "adj";
				break;
			case "m":
				pos = "num";
				break;
			case "u":
				// interjection, eg oh, yes, um.
				pos = "intj";
				break;
			case "c":
				// conjunctions, eg before, until, although
				// and/or should be parsed as conj/disj, will
				// be overwritten in Maps.java
				pos = "con";
				break;
			case "t":
				//only word with this type is "to"
				pos = "pre";
				break;
			default:
				pos = word;
				//System.out.println("default pos: "+ word + " "+ lineAr[2]);
				// defaultList.add(lineAr[2]);
			}
			return pos;
		}
		
	}//end of ComputeFrequencyData class
	
	/**
	 * Retrieves map of true fluff words and their pos. 
	 * @return
	 */
	/*public static ListMultimap<String, String> trueFluffWordsPosMap() {
		return ComputeFrequencyData.trueFluffWordsPosMap();
	}
	
	public static Set<String> trueFluffWordsSet() {
		return ComputeFrequencyData.trueFluffWordsSet();
	}*/
	
	
	/**
	 * Retrieves word-freq map for every word in corpus.
	 * 
	 * @return
	 */
	/*public static Map<String, Integer> corpusWordFreqMap() {
		return corpusWordFreqMap;
	}*/

	public static void main(String[] args) {
		//System.out.println(trueFluffWordsSet.size());
		//System.out.println(trueFluffWordsSet);
		boolean writeToFile = false;
		if(writeToFile){
			List<String> trueFluffWordsList = new ArrayList<String>(ComputeFrequencyData.trueFluffWordsSet);
			FileUtils.writeToFile(trueFluffWordsList, trueFluffWordsPath);
		}
	}
}
