package thmp.search;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.NavigableMap;
import java.util.Scanner;
import java.util.Set;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.TreeMultimap;

import thmp.search.SearchCombined.ThmHypPair;
import thmp.search.ThmHypPairGet.ThmHypPairBundle;
import thmp.utils.GatherRelatedWords.RelatedWords;
import thmp.utils.WordForms;

/**
 * Searches by finding intersections in theorems that contain given keywords.
 * 
 * @author yihed
 */
public class SearchIntersection {

	// bonus points for matching context better, eg hyp or stm
	// disable bonus now, since not using annotated words
	private static final int CONTEXT_WORD_BONUS = 0;
	private static final int NUM_NEAREST_VECS = SearchCombined.NUM_NEAREST;

	private static final Logger logger = LogManager.getLogger(SearchIntersection.class);

	/**
	 * Map of keywords and their scores in document, the higher freq in doc, the
	 * lower score, say 1/(log freq + 1) since log 1 = 0.
	 */
	private static final ImmutableMap<String, Integer> wordsScoreMap;

	//**private static final ImmutableList<String> thmList;
	// private static final ImmutableList<String> webDisplayThmList;

	/**
	 * Multimap of words, and the theorems (their indices) in thmList, the word
	 * shows up in.
	 */
	//private static final ImmutableMultimap<String, Integer> wordThmMMap;
	private static final ImmutableMultimap<String, Integer> wordThmsIndexMMap1;
	/* Keys to relatedWordsMap are not necessarily normalized, only normalized if key not 
	 * already contained in docWordsFreqMapNoAnno. */
	private static final Map<String, RelatedWords> relatedWordsMap;

	// these maps are not immutable, they are not modified during runtime.
	private static final Map<String, Integer> twoGramsMap = NGramSearch.get2GramsMap();
	private static final Map<String, Integer> threeGramsMap = ThreeGramSearch.get3GramsMap();

	// debug flag for development. Prints out the words used and their scores.
	private static final boolean DEBUG = true;
	
	/**
	 * Static initializer, retrieves maps from CollectThm.java.
	 */
	static {
		// System.out.println(thmList);
		wordsScoreMap = CollectThm.ThmWordsMaps.get_wordsScoreMapNoAnno();
		// System.out.println(CollectThm.get_wordsScoreMap());
		//wordThmMMap = CollectThm.ThmWordsMaps.get_wordThmsMMap();//HERE
		wordThmsIndexMMap1 = CollectThm.ThmWordsMaps.get_wordThmsMMapNoAnno();
		relatedWordsMap = CollectThm.ThmWordsMaps.getRelatedWordsMap();
		// System.out.println(wordsScoreMap);
		// thmList = CollectThm.ThmList.get_macroReplacedThmList();
		//****thmList = CollectThm.ThmList.allThmsWithHypList();
		// webDisplayThmList = CollectThm.ThmList.get_webDisplayThmList();
	}

	/**
	 * Pair of theorem index and its span score.
	 */
	private static class ThmSpanPair implements Comparable<ThmSpanPair> {
		private int thmIndex;
		private int spanScore;

		public ThmSpanPair(int index, int spanScore) {
			this.thmIndex = index;
			this.spanScore = spanScore;
		}

		public int thmIndex() {
			return thmIndex;
		}

		public int spanScore() {
			return spanScore;
		}

		public int compareTo(ThmSpanPair other) {
			// return this.spanScore > other.spanScore ? 1 : (this.spanScore <
			// other.spanScore ? -1 : 0);
			// reverse because treemap naturally has ascending order
			return this.spanScore > other.spanScore ? -1
					: (this.spanScore < other.spanScore ? 1 : (this == other ? 0 : -1));
		}
	}
	
	/**
	 * @param input
	 * @param contextSearch
	 *            Whether to use context search.
	 * @param num
	 * @return List of thm Strings.
	 */
	public static List<ThmHypPair> getHighestThmStringList(String input, Set<String> searchWordsSet,
			SearchState searchState, boolean contextSearchBool, boolean searchRelationalBool) {
		intersectionSearch(input, searchWordsSet, searchState, contextSearchBool, searchRelationalBool);
		if (null == searchState){
			return Collections.<ThmHypPair>emptyList();
		}
		List<Integer> highestThmsList = searchState.intersectionVecList();
		if (null == highestThmsList){
			return Collections.<ThmHypPair>emptyList();
		}
		return SearchCombined.thmListIndexToThmHypPair(highestThmsList);
	}

	/**
	 * @param input
	 * @param contextSearch
	 *            Whether to use context search.
	 * @param num
	 * @return
	 */
	public static List<Integer> getHighestThmList(String input, Set<String> searchWordsSet, SearchState searchState,
			boolean contextSearchBool, boolean searchRelationalBool,
			int... num) {
		intersectionSearch(input, searchWordsSet, searchState, contextSearchBool, searchRelationalBool, num);
		if (null == searchState){
			return Collections.emptyList();
		}
		return searchState.intersectionVecList();
	}

	/**
	 * Computes singleton scores for words in list.
	 * Fills inputWordsArUpdated with normalized forms of words in inputWordsAr.
	 * @param wordWrapperList
	 * @param singletonScoresAr
	 * @return
	 */
	private static int computeSingletonScores(String[] inputWordsAr, int[] singletonScoresAr,
			String[] inputWordsArUpdated) {
		int inputWordsArSz = inputWordsAr.length;
		int totalSingletonAdded = 0;
		for (int i = 0; i < inputWordsArSz; i++) {
			String word = inputWordsAr[i];			
			Integer score = wordsScoreMap.get(word);
			if (null == score) {
				word = WordForms.getSingularForm(word);
				score = wordsScoreMap.get(word);
			}
			if(null == score){				
				word = WordForms.normalizeWordForm(word);
				score = wordsScoreMap.get(word);
			}
			if (null != score) {
				singletonScoresAr[i] = score;				
				totalSingletonAdded++;
			} else {
				singletonScoresAr[i] = 0;
			}
			inputWordsArUpdated[i] = word;
		}
		return totalSingletonAdded;
	}

	/**
	 * Uses and manages cache, calls search on the bundles of cached thms. 
	 * @param input
	 * @param searchWordsSet
	 * @param contextSearchBool
	 * @param num
	 * @return
	 */
	public static SearchState intersectionSearchUseCache(String input, Set<String> searchWordsSet, boolean contextSearchBool,
			int... num) {
		//retrieves and manages cache
		Iterator<ThmHypPairBundle> thmCacheIter = ThmHypPairGet.createThmCacheIterator();
		//System.out.println("Dimensions@First@Transpose[q] " + evaluateWLCommand(ml, "Dimensions[First@Transpose[q]]", true, true));
		//String vMx = TermDocumentMatrix.COMBINED_PROJECTED_TERM_DOCUMENT_MX_NAME;
		//List<Integer> closeVecIndices = new ArrayList<Integer>();
		//attach names of bundles searched to searchState??
		while(thmCacheIter.hasNext()){
			//use this only if the thmIndexMap is split into multiple parts
		}
		return null;		
	}
	/**
	 * Builds scoreThmMMap. Main intersection search method.
	 * 
	 * @param input
	 *            input String
	 * @param contextSearchBool
	 *            whether to context search.
	 * @param searchWordsSet
	 *            set of words used in search. Will accurately reflect the words used in search,
	 *            as per the standard way thms are split into words.
	 * @param numHighest
	 *            number of highest-scored thms to retrieve.
	 * @param wordThmsIndexMMap
	 * @return SearchState containing list of indices of highest-scored thms.
	 *         Sorted in ascending order, best first. List is 0-based.
	 */
	public static SearchState intersectionSearch(String input, Set<String> searchWordsSet, 
			SearchState searchState, boolean contextSearchBool, boolean searchRelationalBool,
			//ListMultimap<String, Integer> wordThmsIndexMMap,
			int... numNearestVecs) {

		if (WordForms.getWhiteEmptySpacePattern().matcher(input).matches()){
			return null;
		}
		// map containing the indices of theorems added so far, where values are
		// sets (hashset)
		// of indices of words that have been added. This is to reward theorems
		// that cover
		// the more number of words. Actually just use SetMultimap.
		// if 2/3-grams added, add indices of all words in 2/3-gram to set for
		// that thm.
		logger.info("Starting intersection search...");
		/*Multimap of thmIndex, and the (index of) set of words in query 
		 that appear in the thm*/
		SetMultimap<Integer, Integer> thmWordSpanMMap = HashMultimap.create();

		String[] inputWordsAr = WordForms.splitThmIntoSearchWords(input.toLowerCase());
		
		// determine if first token is integer, if yes, use it as the number of
		// closest thms. Else use NUM_NEAREST_VECS as default value.
		int numHighest = NUM_NEAREST_VECS;
		// whether to skip first token
		int firstIndex = 0;
		if (numNearestVecs.length > 0) {
			numHighest = numNearestVecs[0];
		}
		/*
		 * Map of theorems, in particular their indices in thmList, and the
		 * scores corresponding to the keywords they contain. The rarer a
		 * keyword is in the doc, the higher its score is.
		 */
		Map<Integer, Integer> thmScoreMap = new HashMap<Integer, Integer>();
		// theorem span map, theorem index and their span scores.
		Map<Integer, Integer> thmSpanMap = new HashMap<Integer, Integer>();

		/* Multimap of ints and ints, where key is score, and the value Integers
		 * are the indices of the thms having this score.
		 */
		TreeMultimap<Integer, Integer> scoreThmMMap = TreeMultimap.create();

		// total score of all words, used for computing bonus spanning scores,
		// and lowering
		// scores of n-grams if to dominant. Approximate, for instance does not
		// de-singularize.
		int totalWordsScore = 0;
		int numWordsAdded = 0;

		// multimap of words, and the list of thm indices that have been added
		ListMultimap<String, Integer> wordThmIndexAddedMMap = ArrayListMultimap.create();

		// map of dominant words and the number of times they've been added,
		// whose theorem scores might need to be lowered later
		// the words that have been added multiple times in 1, 2-grams and
		// 3-grams
		// values are the number of times they've been added
		// Map<String, Integer> dominantWordsMap = new HashMap<String,
		// Integer>();
		// multimap of indices in wrapper list and the words that start at that
		// index
		Multimap<Integer, String> indexStartingWordsMMap = ArrayListMultimap.create();

		int inputWordsArSz = inputWordsAr.length;
		// array instead of list for lower overhead.
		int[] singletonScoresAr = new int[inputWordsArSz];
		String[] inputWordsArUpdated = new String[inputWordsArSz];
		// pre-compute the scores for singleton words in query.
		int totalSingletonAdded = computeSingletonScores(inputWordsAr, singletonScoresAr, inputWordsArUpdated);
		searchState.set_totalWordAdded(totalSingletonAdded);
		
		// array of words to indicate frequencies that this word was included in
		// either a singleton or n-gram
		int[] wordCountArray = new int[inputWordsArSz];
		for (int i = firstIndex; i < inputWordsArSz; i++) {
			String word = inputWordsAr[i];
			// elicit higher score if wordLong fits
			// also turn into singular form if applicable			
			int scoreAdded = 0;
			// check for 2 grams
			if (i < inputWordsArSz - 1) {
				String nextWord = inputWordsAr[i + 1];
				String twoGram = word + " " + nextWord;
				twoGram = WordForms.normalizeTwoGram(twoGram);
				// check for 3 grams.
				if (i < inputWordsArSz - 2) {
					String thirdWord = inputWordsAr[i + 2];
					String threeGram = twoGram + " " + thirdWord;
					if (threeGramsMap.containsKey(threeGram)) {
						scoreAdded = addWordThms(thmScoreMap, scoreThmMMap, thmWordSpanMMap, wordThmIndexAddedMMap,
								wordThmsIndexMMap1, 
								threeGram, i, WordForms.TokenType.THREEGRAM,
								singletonScoresAr, searchWordsSet);
						if (scoreAdded > 0) {
							wordCountArray[i] += 1;
							wordCountArray[i + 1] = wordCountArray[i + 1] + 1;
							wordCountArray[i + 2] = wordCountArray[i + 2] + 1;
							totalWordsScore += scoreAdded;
							numWordsAdded++;
							indexStartingWordsMMap.put(i, threeGram);
						}
					}
				}
				if (twoGramsMap.containsKey(twoGram)) {
					scoreAdded = addWordThms(thmScoreMap, scoreThmMMap, thmWordSpanMMap, wordThmIndexAddedMMap, 
							wordThmsIndexMMap1, twoGram, //nextWordCombined, 
							i, WordForms.TokenType.TWOGRAM, singletonScoresAr,
							searchWordsSet);
					if (scoreAdded > 0) {
						wordCountArray[i] += 1;
						wordCountArray[i + 1] = wordCountArray[i + 1] + 1;
						totalWordsScore += scoreAdded;
						searchState.addTokenScore(twoGram, scoreAdded);
						numWordsAdded++;
						indexStartingWordsMMap.put(i, twoGram);
					}
				}
			}
			// if the words in a three gram collectively (3 gram + 2 gram +
			// individual words) weigh a lot,
			// then scale down the overall words? e.g. "linear map with closed
			// range", "closed", "range",
			// "closed range" all weigh a lot. Scale proportionally down with
			// respect to the average
			// score of all words added.
			word = inputWordsArUpdated[i];
			scoreAdded = addWordThms(thmScoreMap, scoreThmMMap, thmWordSpanMMap, wordThmIndexAddedMMap, 
					wordThmsIndexMMap1, word, i, WordForms.TokenType.SINGLETON, singletonScoresAr, searchWordsSet);
			if (scoreAdded > 0) {
				wordCountArray[i] += 1;
				totalWordsScore += scoreAdded;
				searchState.addTokenScore(word, scoreAdded);
				numWordsAdded++;
				indexStartingWordsMMap.put(i, word);
			}
		}
		// System.out.println("BEFORE "+scoreThmMMap);
		// Map<Integer, Integer> g = new HashMap<Integer, Integer>(thmScoreMap);
		// add bonus points to thms with most number of query words, judging
		// from size of value set
		// in thmWordSpanMMap
		addWordSpanBonus(searchState, thmScoreMap, scoreThmMMap, thmWordSpanMMap, thmSpanMap, numHighest,
				((double) totalWordsScore) / numWordsAdded);
		// System.out.println("AFTER " + g.equals(scoreThmMMap));
		searchState.addThmSpan(thmSpanMap);
		searchState.setThmScoreMap(thmScoreMap);

		// new map to record of the final scores (this obliterates scoreThmMMap)
		// make values into pairs of thms with their span scores
		TreeMultimap<Integer, ThmSpanPair> scoreThmMMap2 = TreeMultimap.create();
		for (Map.Entry<Integer, Integer> thmScoreEntry : thmScoreMap.entrySet()) {
			int thmIndex = thmScoreEntry.getKey();
			int thmScore = thmScoreEntry.getValue();
			int spanScore = thmSpanMap.get(thmIndex);
			scoreThmMMap2.put(thmScore, new ThmSpanPair(thmIndex, spanScore));
		}
		List<Integer> highestThmList = new ArrayList<Integer>();
		// get the thms having the highest k scores. Keys are scores.
		// ****** does this order the value set accordingly as well? <--it
		// should
		NavigableMap<Integer, Collection<ThmSpanPair>> thmMap = scoreThmMMap2.asMap().descendingMap();

		// pick up numHighest number of unique thms
		Set<Integer> pickedThmSet = new HashSet<Integer>();

		// list to track the top entries
		//int counter = numHighest * 2;
		int counter = numHighest;
		for (Entry<Integer, Collection<ThmSpanPair>> entry : thmMap.entrySet()) {
			for (ThmSpanPair pair : entry.getValue()) {
				Integer thmIndex = pair.thmIndex;
				if (counter == 0)
					break;
				// avoid duplicates, since the scoreThmMMap leaves outdated
				// score-thm pair in map, rather than deleting them, after
				// updating score
				if (pickedThmSet.contains(thmIndex))
					continue;
				pickedThmSet.add(thmIndex);
				highestThmList.add(thmIndex);
				counter--;
				if (DEBUG) {
					ThmHypPair thmHypPair = ThmHypPairGet.retrieveThmHypPairWithThm(thmIndex);
					System.out.println(
							"thm Score " + entry.getKey() + " thmIndex " + thmIndex + " thm " 
									+ thmHypPair.thmStr() + " HYP " + thmHypPair.hypStr());
				}
			}
		}		
		int tupleSz = SearchCombined.CONTEXT_SEARCH_TUPLE_SIZE;
		//combine with ranking from relational search, reorganize within each tuple
		//of fixed size. Try relation search first, then context search.
		if(searchRelationalBool){
			Searcher<BigInteger> searcher = new RelationalSearch();
			//bestCommonVecsList = searchVecWithTuple(input, bestCommonVecsList, tupleSz, searcher, searchState);
			highestThmList = SearchCombined.searchVecWithTuple(input, highestThmList, tupleSz, searcher, searchState);					
		}
		
		// re-order top entries based on context search, if enabled
		if (contextSearchBool) {
			Searcher<Map<Integer, Integer>> searcher = new ContextSearch();			
			highestThmList = SearchCombined.searchVecWithTuple(input, highestThmList, tupleSz, searcher, searchState);
		}
		logger.info("Highest thm list obtained, intersection search done!");
		searchState.set_intersectionVecList(highestThmList);
		return searchState;
	}

	/**
	 * Auxiliary method to lower the scores. if the words in a three gram
	 * collectively (3 gram + 2 gram + individual words) weigh a lot, then scale
	 * down the overall words proportionally. e.g.
	 * "linear map with closed range", "closed", "range", "closed range" all
	 * weigh a lot. Scale proportionally down with respect to the average score.
	 * Just reduce token initial scores instead!
	 * @deprecated June 2017. Keep for a few months if the need for improved version arises.
	 * @param thmScoreMap
	 * @param scoreThmMMap
	 * @param thmWordSpanMMap
	 * @param wordThmMMap
	 * @param dominantWordsMap
	 * @param indexStartingWordsMMap
	 * @param wordCountArray
	 * @param wordWrapperList
	 * @param avgWordScore
	 */
	private static void lowerThmScores(Map<Integer, Integer> thmScoreMap, TreeMultimap<Integer, Integer> scoreThmMMap,
			Multimap<Integer, Integer> thmWordSpanMMap, ListMultimap<String, Integer> wordThmIndexMMap,
			Multimap<Integer, String> indexStartingWordsMMap, int[] wordCountArray, //List<WordWrapper> wordWrapperList,
			double avgWordScore) {

		// if freq above certain level
		for (int i = 0; i < wordCountArray.length; i++) {

			// String word = wordWrapperList.get(i).word();
			// dominant map
			if (wordCountArray[i] > 1) {
				// set of words that start at this index
				Collection<String> indexWordsCol = indexStartingWordsMMap.get(i);

				for (String indexWord : indexWordsCol) {
					String[] wordAr = indexWord.split(" ");
					int len = indexWord.split(" ").length;
					// and score above averg
					if (len == 1 && wordsScoreMap.get(indexWord) > avgWordScore * 3.0 / 2) {
						adjustWordClusterScore(thmScoreMap, scoreThmMMap, wordThmIndexMMap, avgWordScore, indexWord);
					} else if (len == 2) {
						// 2 tuple, only lower if second word also included
						// often with high score
						if (wordsScoreMap.get(wordAr[1]) > avgWordScore * 3.0 / 2 && wordCountArray[i + 1] > 1) {
							adjustWordClusterScore(thmScoreMap, scoreThmMMap, wordThmIndexMMap, avgWordScore,
									indexWord);
						}
					} else if (len == 3) {
						// adjust score only if either the second or third word
						// gets counted multiple times, and weigh
						// more than 3/2 of the average score.
						if (wordsScoreMap.get(wordAr[1]) > avgWordScore * 3.0 / 2 && wordCountArray[i + 1] > 1
								|| wordsScoreMap.get(wordAr[2]) > avgWordScore * 3.0 / 2 && wordCountArray[i + 2] > 1) {
							adjustWordClusterScore(thmScoreMap, scoreThmMMap, wordThmIndexMMap, avgWordScore,
									indexWord);
						}
					}
				}
			}
		}
	}

	/**
	 * Auxiliary method to adjust scores of word clusters.
	 * 
	 * @param thmScoreMap
	 * @param scoreThmMMap
	 * @param wordThmMMap
	 * @param avgWordScore
	 * @param indexWord
	 *            word whose score is being reduced
	 */
	private static void adjustWordClusterScore(Map<Integer, Integer> thmScoreMap,
			TreeMultimap<Integer, Integer> scoreThmMMap, ListMultimap<String, Integer> wordThmIndexMMap,
			double avgWordScore, String indexWord) {
		// get list of theorems
		List<Integer> thmList = wordThmIndexMMap.get(indexWord);
		int prevWordScore = wordsScoreMap.get(indexWord);
		int scoreToDeduct = (int) (prevWordScore - avgWordScore / 3.0);
		System.out.println("word being deducted: " + indexWord + " score being deducted " + scoreToDeduct);

		// lower their scores
		for (int thmIndex : thmList) {
			int prevScore = thmScoreMap.get(thmIndex);
			// removing the highest might not be enough! There might be other
			// score entries
			// for this thm already that's higher than the new score.
			// scoreThmMMap.remove(prevScore, thmIndex);
			int newThmScore = prevScore - scoreToDeduct;
			// customize this score more based on avg score
			scoreThmMMap.put(newThmScore, thmIndex);
			thmScoreMap.put(thmIndex, newThmScore);
		}
	}

	/**
	 * Auxiliary method to add bonus points to theorems containing more words.
	 * Bonus is proportional to the highest thm score,
	 * 
	 * @param thmScoreMap
	 * @param scoreThmMMap
	 * @param thmWordSpanMMap
	 * @param thmSpanMap
	 *            map of theorem indices and their spans
	 */
	private static void addWordSpanBonus(SearchState searchState, Map<Integer, Integer> thmScoreMap, 
			TreeMultimap<Integer, Integer> scoreThmMMap,
			SetMultimap<Integer, Integer> thmWordSpanMMap, Map<Integer, Integer> thmSpanMap, int numHighest,
			double avgWordScore) {
		// add according to score
		// gather the sizes of the value maps for thmWordSpanMMap, and keep
		// track of order based on scores using a TreeMultimap
		TreeMultimap<Integer, Integer> spanScoreThmMMap = TreeMultimap.create();
		int largestWordSpan = 0;
		for (int thmIndex : thmWordSpanMMap.keySet()) {
			// System.out.println(thmWordSpanMMap.get(thmIndex));
			int thmWordsSetSize = thmWordSpanMMap.get(thmIndex).size();
			thmSpanMap.put(thmIndex, thmWordsSetSize);
			if(thmWordsSetSize > largestWordSpan){
				largestWordSpan = thmWordsSetSize;
			}
			spanScoreThmMMap.put(thmWordsSetSize, thmIndex);
		}
		searchState.setLargestWordSpan(largestWordSpan);
		
		// add bonus proportional to the avg word score (not span score)
		NavigableMap<Integer, Collection<Integer>> orderedSpanScoreMMap = spanScoreThmMMap.asMap().descendingMap();

		int counter = numHighest;
		// counts which span level is being iterated over currently
		int spanCounter = 1;
		for (Entry<Integer, Collection<Integer>> entry : orderedSpanScoreMMap.entrySet()) {

			for (int thmIndex : entry.getValue()) {
				if (counter == 0){
					break;	
				}
				int prevScore = thmScoreMap.get(thmIndex);
				
				int bonusScore = (int) (avgWordScore / ((double) spanCounter * 2));
				bonusScore = bonusScore == 0 ? 1 : bonusScore;
				int newThmScore = prevScore + bonusScore;
				scoreThmMMap.put(newThmScore, thmIndex);
				thmScoreMap.put(thmIndex, newThmScore);
				if (DEBUG) {
					ThmHypPair thmHypPair = ThmHypPairGet.retrieveThmHypPairWithThm(thmIndex);
					String thm = thmHypPair.thmStr();
					String hyp = thmHypPair.hypStr();
					System.out.println("Adding bonus " + bonusScore + ". num words hit: " + entry.getKey()
							+ ". newThmScore: " + newThmScore + ". thm: " + thm + " HYP "+ hyp);
					// System.out.println("PREV SCORE " + prevScore + " NEW
					// SCORE " + newThmScore + thm);
				}
				counter--;
			}
			spanCounter++;
		}
	}

	/**
	 * Auxiliary method for getHighestVecs. Retrieves thms that contain
	 * wordLong, add these thms to map. Annotated 2 grams only have annotation
	 * at start of first word.
	 * 
	 * @param thmScoreMap
	 * @param scoreThmMMap
	 * @param word
	 * @param wordIndices
	 *            array of indices of words in query
	 * @param singletonScoresAr
	 *            Array of scores for singleton words
	 * @param set
	 *            of words, separated into singletons, used during search
	 * @param thmWordSpanMMap Multimap of thmIndex, and the (index of) set of words in query 
	 * that appear in the thm.
	 * @param wordThmIndexAddedMMap Thms that have already been added for the input.
	 * @param wordThmIndexMMap MMap created from tars used to look up thms containing words.
	 * @return scoreAdded
	 */
	private static int addWordThms(Map<Integer, Integer> thmScoreMap, TreeMultimap<Integer, Integer> scoreThmMMap,
			Multimap<Integer, Integer> thmWordSpanMMap, ListMultimap<String, Integer> wordThmIndexAddedMMap,
			Multimap<String, Integer> wordThmIndexMMap,
			String word, int wordIndexInThm, WordForms.TokenType tokenType,
			int[] singletonScoresAr, Set<String> searchWordsSet) {
		// update scores map
		int curScoreToAdd = 0;
		int scoreAdded = 0;
		String wordOriginalForm = word;
		List<String> relatedWordsList = null;
		// for every word, get list of thms containing this word
		Collection<Integer> wordThms;
		wordThms = wordThmsIndexMMap1.get(word);
		
		// only going through the no annotation path
		RelatedWords relatedWords = relatedWordsMap.get(word);
		if (null != relatedWords) {
			relatedWordsList = relatedWords.getCombinedList();
		}
		String wordSingForm = word;
		Integer wordScore = 0;
		if (!wordThms.isEmpty()) {
			wordScore = wordsScoreMap.get(word);
			wordScore = wordScore == null ? 0 : wordScore;
			curScoreToAdd = wordScore + CONTEXT_WORD_BONUS;
		} else {
			wordSingForm = WordForms.getSingularForm(word);
			Integer wordSingFormScore = wordsScoreMap.get(wordSingForm);
			
			if (null != wordSingFormScore) {				
				//wordThms = wordThmMMap.get(singFormLong);
				wordThms = wordThmsIndexMMap1.get(wordSingForm);				
				// wordScore = wordsScoreMap.get(singFormLong);
				wordScore = wordSingFormScore;
				curScoreToAdd = wordScore + CONTEXT_WORD_BONUS;
				word = wordSingForm;
				if (null == relatedWordsList) {
					relatedWords = relatedWordsMap.get(word);
					if (null != relatedWords) {
						relatedWordsList = relatedWords.getCombinedList();
					}
				}
			} 
		}
		if (wordThms.isEmpty()) {
			String normalizedWord = WordForms.normalizeWordForm(word);
			Integer tempWordScore = wordsScoreMap.get(normalizedWord);
			if(null == tempWordScore){
				normalizedWord = WordForms.normalizeWordForm(wordSingForm);
				tempWordScore = wordsScoreMap.get(normalizedWord);
			}
			//System.out.println("SEARCHINTERSECTION - normalizedWord "+normalizedWord + " wordThmMMapNoAnno.contains() "
				//	+ wordThmMMapNoAnno.containsKey(normalizedWord));
			if (null != tempWordScore) {
				wordThms = wordThmsIndexMMap1.get(normalizedWord);
				// wordScore = wordsScoreMap.get(singFormLong);
				// wordScore = wordsScoreMap.get(word);
				// wordScore = wordScore == null ? 0 : wordScore;
				wordScore = tempWordScore;
				curScoreToAdd = wordScore + CONTEXT_WORD_BONUS;
				word = normalizedWord;
				// try to get related words,
				if (null == relatedWordsList) {
					relatedWords = relatedWordsMap.get(word);
					if (null != relatedWords) {
						relatedWordsList = relatedWords.getCombinedList();
					}
				}
			}
		}
		// removes endings such as -ing, and uses synonym rep.
		
		// adjust curScoreToAdd, boost 2, 3-gram scores when applicable
		curScoreToAdd = tokenType.adjustNGramScore(curScoreToAdd, singletonScoresAr, wordIndexInThm);

		if (!wordThms.isEmpty() && curScoreToAdd != 0) {
			// System.out.println("wordThms " + wordThms);
			wordThmIndexAddedMMap.putAll(word, wordThms);
			if (DEBUG) {
				System.out.println("SearchIntersection-Word added: " + word + ". Score: " + curScoreToAdd);
			}
			for (Integer thmIndex : wordThms) {
				// skip thm if current word already been covered by previous
				// 2/3-gram
				if (tokenType.ifAddedToMap(thmWordSpanMMap, thmIndex, wordIndexInThm)){
					continue;
				}
				Integer prevScore = thmScoreMap.get(thmIndex);
				prevScore = prevScore == null ? 0 : prevScore;
				Integer newScore = prevScore + curScoreToAdd;
				// this mapping is not being used in the end right now,
				// since the top N are picked, regardless of their scores.
				thmScoreMap.put(thmIndex, newScore);
				// System.out.println("*** " + thmScoreMap);
				scoreThmMMap.put(newScore, thmIndex);
				// put in thmIndex, and the index of word in the query, to
				// thmWordSpanMMap.
				tokenType.addToMap(thmWordSpanMMap, thmIndex, wordIndexInThm);
			}
			scoreAdded = curScoreToAdd;
			// add singletons to searchWordsSet, so
			// searchWordsSet could be null if not interested in searchWordsSet.
			if (scoreAdded > 0 && searchWordsSet != null) {
				String[] wordAr = word.split("\\s+");
				for (String w : wordAr) {
					searchWordsSet.add(w);
				}
				if(!word.equals(wordOriginalForm)){
					searchWordsSet.add(wordOriginalForm);
				}
			}
		}		
		addRelatedWordsThms(thmScoreMap, scoreThmMMap, thmWordSpanMMap, wordIndexInThm, tokenType, scoreAdded,
				relatedWordsList);

		return scoreAdded;
	}

	/**
	 * @param thmScoreMap
	 * @param scoreThmMMap
	 * @param thmWordSpanMMap
	 * @param wordIndexInThm
	 * @param tokenType
	 * @param scoreAdded
	 * @param relatedWordsList
	 */
	private static void addRelatedWordsThms(Map<Integer, Integer> thmScoreMap,
			TreeMultimap<Integer, Integer> scoreThmMMap, Multimap<Integer, Integer> thmWordSpanMMap, int wordIndexInThm,
			WordForms.TokenType tokenType, int scoreAdded, List<String> relatedWordsList) {
		// add thms for related words found, with some reduction factor;
		// make global after experimentation.
		double RELATED_WORD_MULTIPLICATION_FACTOR = 4 / 5.0;
		if (null != relatedWordsList) {
			// wordScore = wordsScoreMap.get(word);
			int relatedWordScore = (int) Math.ceil(scoreAdded * RELATED_WORD_MULTIPLICATION_FACTOR);
			for (String relatedWord : relatedWordsList) {
				// Multimap, so return empty collection rather than null, if no
				// hit.
				// relatedWordThmIndices.addAll();
				Collection<Integer> relatedWordThms = wordThmsIndexMMap1.get(relatedWord);
				if (!relatedWordThms.isEmpty() && relatedWordScore == 0) {
					Integer score = wordsScoreMap.get(relatedWord);
					if (null == score){
						continue;
					}
					relatedWordScore = (int) Math.ceil(score * RELATED_WORD_MULTIPLICATION_FACTOR);
				}
				for (Integer thmIndex : relatedWordThms) {
					// related words count towards span, only if the original
					// word not added.
					if (!tokenType.ifAddedToMap(thmWordSpanMMap, thmIndex, wordIndexInThm)) {
						// put in thmIndex, and the index of word in the query,
						// to thmWordSpanMMap.
						tokenType.addToMap(thmWordSpanMMap, thmIndex, wordIndexInThm);
					}
					Integer prevScore = thmScoreMap.get(thmIndex);
					prevScore = prevScore == null ? 0 : prevScore;
					Integer newScore = prevScore + relatedWordScore;
					// this mapping is not being used in the end right now,
					// since the top N are picked, regardless of their scores.
					thmScoreMap.put(thmIndex, newScore);
					scoreThmMMap.put(newScore, thmIndex);
				}
			}
		}
	}

	/**
	 * Searches the theorem base using just the intersection algorithm. Public
	 * facing, don't call within this class, call getHighestThm directly instead
	 * (so not to duplicate work).
	 * 
	 * @param inputStr
	 *            Query string.
	 * @param searchWordsSet
	 *            set of terms (singletons) used during search. Used later for
	 *            web display.
	 * @return
	 */
	/*public static List<ThmHypPair> search(String inputStr, Set<String> searchWordsSet) {
		
		boolean contextSearchBool = false;
		List<Integer> highestThms = getHighestThmList(inputStr, searchWordsSet, contextSearchBool);

		if (highestThms == null) {
			// foundThmList.add("Close, but no cigar. I don't have a theorem on
			// that yet.");
			// return thmList;
			return Collections.<ThmHypPair>emptyList();
		}
		return SearchCombined.thmListIndexToThmHypPair(highestThms);
	}*/

	/**
	 * Reads in keywords. Gets theorems with highest scores for this.
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		System.out.println("searchIntersection- Total number of thms " + ThmHypPairGet.totalThmsCount());
		Scanner sc = new Scanner(System.in);
	
		while (sc.hasNextLine()) {
			String thm = sc.nextLine();

			boolean searchRelationalBool = false;
			boolean contextSearchBool = false;
			String[] thmAr = thm.split("\\s+");
			if (thmAr.length > 2 ) {
				if(thmAr[0].equals("context")){
					contextSearchBool = true;
					//skip space after first word
					thm = thm.substring(8);
				}else if(thmAr[0].equals("relation")){
					searchRelationalBool = true;
					thm = thm.substring(9);
				}
				// highestThms = ContextSearch.contextSearch(thm, highestThms);
			}

			SearchState searchState = new SearchState();
			
			// user's input overrides default num
			StringBuilder inputSB = new StringBuilder();
			int numHighest = SearchCombined.getNumCommonVecs(inputSB, thm);
			thm = inputSB.toString();
			
			// searchWordsSet is null.
			List<Integer> highestThms = getHighestThmList(thm, null, searchState, contextSearchBool, 
					searchRelationalBool, numHighest);
			
			if (highestThms == null){
				continue;
			}
			/*
			 * String[] thmAr = thm.split("\\s+"); if(thmAr.length > 1 &&
			 * thmAr[0].equals("context")){ highestThms =
			 * ContextSearch.contextSearch(thm, highestThms); }
			 */
			int counter = 0;
			System.out.println("~ SEARCH RESULTS ~");
			for (Integer thmIndex : highestThms) {
				System.out.println(counter++ + " ++ " + ThmHypPairGet.retrieveThmHypPairWithThm(thmIndex));
			}
		}
		sc.close();
	}

}
