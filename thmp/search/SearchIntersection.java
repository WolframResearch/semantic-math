package thmp.search;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.PriorityQueue;
import java.util.Scanner;
import java.util.Set;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.TreeMultimap;

import thmp.search.CollectThm.ThmWordsMaps.IndexPartPair;
import thmp.search.SearchCombined.ThmHypPair;
import thmp.search.ThmHypPairGet.ThmHypPairBundle;
import thmp.utils.DBUtils.AuthorName;
import thmp.utils.DBUtils.ConjDisjType;
import thmp.utils.FileUtils;
import thmp.utils.GatherRelatedWords.RelatedWords;
import thmp.utils.WordForms;
import thmp.utils.WordForms.ThmPart;

/**
 * Searches by finding intersections in theorems that contain given keywords.
 * 
 * @author yihed
 */
public class SearchIntersection {
	
	// determine if first token is integer, if yes, use it as the number of
	// closest thms. Else use NUM_NEAREST_VECS as default value.
	private static final int NUM_NEAREST_VECS = SearchCombined.NUM_NEAREST;

	private static final Pattern BY_AUTHORS_PATT = Pattern.compile("(.*)\\s*by authors*\\s+(.*?)\\s*");
	private static final Pattern AND_OR_PATT = Pattern.compile("\\s+(and|or)\\s+");

	private static final boolean profileTiming = false; 
	private static final Logger logger = LogManager.getLogger(SearchIntersection.class);

	/**
	 * Map of keywords and their scores in document, the higher freq in doc, the
	 * lower score, say 1/(log freq + 1) since log 1 = 0.
	 */
	private static final ImmutableMap<String, Integer> wordsScoreMap;

	/**
	 * Multimap of words, and the theorems (their indices) in thmList, the word
	 * shows up in.
	 */
	//private static final ImmutableMultimap<String, Integer> wordThmMMap;
	private static final ImmutableMultimap<String, IndexPartPair> wordThmsIndexMMap1;
	/* Keys to relatedWordsMap are not necessarily normalized, only normalized if key not 
	 * already contained in docWordsFreqMapNoAnno. */
	private static final Map<String, RelatedWords> relatedWordsMap;

	// these maps are not immutable, they are not modified during runtime.
	private static final Map<String, Integer> twoGramsMap = NGramSearch.get2GramsMap();
	private static final Map<String, Integer> threeGramsMap = ThreeGramSearch.get3GramsMap();

	// debug flag for development. Prints out the words used and their scores.
	private static final boolean DEBUG = FileUtils.isOSX();
	
	/**
	 * Static initializer, retrieves maps from CollectThm.java.
	 */
	static {		
		wordsScoreMap = CollectThm.ThmWordsMaps.get_wordsScoreMap();
		wordThmsIndexMMap1 = CollectThm.ThmWordsMaps.get_wordThmsMMap();
		relatedWordsMap = CollectThm.ThmWordsMaps.getRelatedWordsMap();		
	}

	/**
	 * Pair of theorem index and its span score.
	 */
	public static class ThmScoreSpanPair implements Comparable<ThmScoreSpanPair> {
		private int thmIndex;
		/*score based on words*/
		private int score;
		private int spanScore;

		public ThmScoreSpanPair(int index_, int score_, int spanScore_) {
			this.thmIndex = index_;
			this.score = score_;
			this.spanScore = spanScore_;
		}

		public int thmIndex() {
			return thmIndex;
		}

		public int spanScore() {
			return spanScore;
		}
		
		public int score() {
			return score;
		}

		/**
		 * First uses intersection word scores to rank, then use span scores to tiebreak.
		 */
		@Override
		public int compareTo(ThmScoreSpanPair other) {
			// reverse because treemap naturally has ascending order
			return this.score > other.score ? -1
					//: (this.spanScore < other.spanScore ? 1 : (this == other ? 0 : -1));
					//need explicit equals, so not all instances are recognized to be the same in map.
					: (this.score < other.score ? 1 : (this.equals(other) ? 0 : tieBreakWithSpan(other)));
		}

		private int tieBreakWithSpan(ThmScoreSpanPair other) {
			// reverse because treemap naturally has ascending order
			return this.spanScore > other.spanScore ? -1
					//: (this.spanScore < other.spanScore ? 1 : (this == other ? 0 : -1));
					//need explicit equals, so not all instances are recognized to be the same in map.
					: (this.spanScore < other.spanScore ? 1 : (this.equals(other) ? 0 : -1));
		}
		
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + spanScore;
			result = prime * result + score;
			result = prime * result + thmIndex;
			return result;
		}
		
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (!(obj instanceof ThmScoreSpanPair)){
				return false;
			}
			ThmScoreSpanPair other = (ThmScoreSpanPair) obj;
			if (spanScore != other.spanScore)
				return false;
			if (score != other.score)
				return false;
			if (thmIndex != other.thmIndex)
				return false;
			return true;
		}		
	}
	
	/**
	 * Word and its corresponding theorems list. 
	 */
	private static class WordThmsList implements Comparable<WordThmsList>{
		
		String word;
		Collection<IndexPartPair> thmsList;
		//word score as used in intersection search.
		int score;
		//index of word in the thm
		int wordIndexInThm;
		
		WordForms.TokenType tokenType;
		
		WordThmsList(String word_, Collection<IndexPartPair> thmsList_, int score_,
				WordForms.TokenType tokenType_, int wordIndexInThm_){
			this.word = word_;
			this.thmsList = thmsList_;
			this.score = score_;
			this.tokenType = tokenType_;
			this.wordIndexInThm = wordIndexInThm_;
		}
		
		/**
		 * shorter theorem lists are prioritized.
		 */
		@Override
		public int compareTo(WordThmsList other) {
			int thisSz = thmsList.size();
			int otherSz = other.thmsList.size();
			return thisSz > otherSz ? 1 : thisSz < otherSz ? -1 : 
				//if same size:
				(this.score < other.score ? 1 : this.score > other.score ? -1 :
					(this.wordIndexInThm > other.wordIndexInThm ? 1 : 
							this.wordIndexInThm < other.wordIndexInThm ? -1 : 0)
					);
		}
		
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + this.thmsList.size();
			result = result * prime + this.score;
			result = result * prime + this.wordIndexInThm;
			return result;
		}
		
		@Override
		public boolean equals(Object obj) {
			if(!(obj instanceof WordThmsList)) {
				return false;				
			}
			WordThmsList other = (WordThmsList)obj;
			if(thmsList.size() != other.thmsList.size()) {
				return false;
			}
			if(this.score != other.score) {
				return false;
			}
			if(this.wordIndexInThm != other.wordIndexInThm) {
				return false;
			}
			return true;
		}
		
		@Override
		public String toString() {
			return this.word;
		}
	}
	
	/**
	 * @param input
	 * @param contextSearch
	 *            Whether to use context search.
	 * @param num
	 * @return List of thm Strings.
	 */
	public static List<Integer> getHighestThmStringList(String input, Set<String> searchWordsSet,
			SearchState searchState, boolean contextSearchBool, boolean searchRelationalBool) {
		
		input = input.toLowerCase();
		//List<Integer> authorThmList = null;
		int numSearchResults = NUM_NEAREST_VECS;
		
		/*parse here for queries that require search by authors in database table, get list of thm indices, 
		  pass to intersection search. Triggered by "by author". */
		Matcher m;
		if((m=BY_AUTHORS_PATT.matcher(input)).matches()) {
			input = m.group(1);			
			boolean searchAuthorOnly = WordForms.getWhiteEmptySpacePattern().matcher(input).matches();
			logger.info("input:"+input, " searchAuthorOnly ",searchAuthorOnly);
			//parse the authors string 
			String authorStr = m.group(2);
			//but and/or could be more complicated with compositions!!
			
			ConjDisjType conjDisjType = ConjDisjType.DISJ;
			
			List<AuthorName> authorList = new ArrayList<AuthorName>();
			
			if((m = AND_OR_PATT.matcher(authorStr)).matches()){
				conjDisjType = ConjDisjType.getType(m.group(1));
				String[] authorAr;
				authorAr = AND_OR_PATT.split(authorStr);
				for(String author : authorAr) {
					authorList.add(new AuthorName(author));					
				}
			}else {
				authorList.add(new AuthorName(authorStr));
			}
			DBSearch.AuthorRelation authorRelation = new DBSearch.AuthorRelation(authorStr);
			//by the regexes construction, there should be no spaces around authors
			boolean hasSearched = false;
			List<Integer> authorThmList = null;
			Set<Integer> authorThmSet = null;
			
			try {
				authorThmList = DBSearch.searchByAuthor(authorRelation, conjDisjType);
				authorThmSet = new HashSet<Integer>(authorThmList);
				if(searchAuthorOnly) {
					searchState.set_intersectionVecList(authorThmList);
				}
			} catch (SQLException e) {
				logger.error("SQLException when searching for author! " + e);
				intersectionSearch(input, searchWordsSet, searchState, contextSearchBool, searchRelationalBool, 
						numSearchResults);
				hasSearched = true;
			}
			if(!searchAuthorOnly && !hasSearched) {
				intersectionSearch(input, searchWordsSet, searchState, contextSearchBool, searchRelationalBool, 
						numSearchResults, authorThmSet);
			}
		}else {
			intersectionSearch(input, searchWordsSet, searchState, contextSearchBool, searchRelationalBool, numSearchResults);			
		}
		
		if (null == searchState){
			return Collections.<Integer>emptyList();
		}
		List<Integer> highestThmsList = searchState.intersectionVecList();
		if (null == highestThmsList){
			return Collections.<Integer>emptyList();
		}
		//return SearchCombined.thmListIndexToThmHypPair(highestThmsList);
		return highestThmsList;
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
		
		int numSearchResults = NUM_NEAREST_VECS;
		if(num.length > 0) {
			numSearchResults = num[0];
		}
		intersectionSearch(input, searchWordsSet, searchState, contextSearchBool, searchRelationalBool, numSearchResults);
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
	private static int computeSingletonScores(List<String> inputWordsAr, int[] singletonScoresAr,
			String[] inputWordsArUpdated) {
		int inputWordsArSz = inputWordsAr.size();
		int totalSingletonAdded = 0;
		for (int i = 0; i < inputWordsArSz; i++) {
			String word = inputWordsAr.get(i);			
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
	/*Commented out Dec 12, delete a few weeks later.
	 * public static SearchState intersectionSearchUseCache(String input, Set<String> searchWordsSet, boolean contextSearchBool,
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
	}*/
	
	public static SearchState intersectionSearch(String input, Set<String> searchWordsSet, 
			SearchState searchState, boolean contextSearchBool, boolean searchRelationalBool,
			int numHighest) {		
		return intersectionSearch(input, searchWordsSet, searchState, contextSearchBool, 
				searchRelationalBool, numHighest, null);
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
	 * @param dbThmList list from database query. Optional. Only consider intersection search 
	 * results that are also in this list.
	 * @return SearchState containing list of indices of highest-scored thms.
	 *         Sorted in ascending order, best first. List is 0-based.
	 */
	public static SearchState intersectionSearch(String input, Set<String> searchWordsSet, 
			SearchState searchState, boolean contextSearchBool, boolean searchRelationalBool,
			int numHighest, Set<Integer> dbThmSet) {
		
		if (WordForms.getWhiteEmptySpacePattern().matcher(input).matches()){
			return null;
		}
		input = input.toLowerCase();
		
		/*Multimap of thmIndex, and the (index of) set of words in query 
		 that appear in the thm. Important that this is *Hash*Multimap */
		SetMultimap<Integer, Integer> thmWordSpanMMap = HashMultimap.create();
		
		List<String> inputWordsList;
		//take tokens in quotes as literal words
		if(searchState.allowLiteralSearch()) {
			inputWordsList = WordForms.splitThmIntoQuotedSections(input);
		}else {		
			inputWordsList = WordForms.splitThmIntoSearchWordsList(input);
		}
		
		/*
		 * Map of theorems, in particular their indices in thmList, and the
		 * scores corresponding to the keywords they contain. The rarer a
		 * keyword is in the doc, the higher its score is.
		 */
		Map<Integer, Integer> thmScoreMap = new HashMap<Integer, Integer>();
		/* theorem span map, theorem index and their span scores.*/
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

		// multimap of words, and the list of thm indices that have been added. Words.
		//ListMultimap<String, Integer> wordThmIndexAddedMMap = ArrayListMultimap.create();
		
		// multimap of indices in wrapper list and the words that start at that index
		Multimap<Integer, String> indexStartingWordsMMap = ArrayListMultimap.create();

		int inputWordsArSz = inputWordsList.size();
		// array instead of list for lower overhead.
		int[] singletonScoresAr = new int[inputWordsArSz];
		String[] inputWordsArUpdated = new String[inputWordsArSz];
		// pre-compute the scores for singleton words in query.
		int totalSingletonAdded = computeSingletonScores(inputWordsList, singletonScoresAr, inputWordsArUpdated);
		searchState.set_totalWordAdded(totalSingletonAdded);
		//map of word in singularized form, with list of thms for that word.
		Map<String, Collection<IndexPartPair>> wordIndexPartPairMap = new HashMap<String, Collection<IndexPartPair>>();
		
		Set<ThmScoreSpanPair> thmScoreSpanSet = new HashSet<ThmScoreSpanPair>();
		Map<Integer, ThmPart> thmPartMap = new HashMap<Integer, ThmPart>();
		
		List<WordThmsList> wordThmsListList = new ArrayList<WordThmsList>();
		// array of words to indicate frequencies that this word was included in
		// either a singleton or n-gram
		int[] wordCountArray = new int[inputWordsArSz];
		
		for (int i = 0; i < inputWordsArSz; i++) {
			//long time0 = System.nanoTime();
			String word = inputWordsList.get(i);
			// elicit higher score if wordLong fits
			// also turn into singular form if applicable			
			int scoreAdded = 0;
			// check for 2 grams
			if (i < inputWordsArSz - 1) {
				String nextWord = inputWordsList.get(i+1);
				String twoGram = word + " " + nextWord;
				twoGram = WordForms.normalizeTwoGram(twoGram);
				// check for 3 grams.
				//long time1 = SimilarThmSearch.printElapsedTime(time0, "time1");
				
				if (i < inputWordsArSz - 2) {
					String thirdWord = inputWordsList.get(i+2);
					String threeGram = twoGram + " " + thirdWord;
					if (threeGramsMap.containsKey(threeGram)) {
						scoreAdded = addWordThms(thmScoreMap, scoreThmMMap, thmWordSpanMMap,// thmWordsMMap, 
								thmScoreSpanSet, thmPartMap, wordThmsListList, wordIndexPartPairMap,//normalizedWordsSet,
								threeGram, i, WordForms.TokenType.THREEGRAM,
								singletonScoresAr, searchWordsSet, dbThmSet, searchState);
						if (scoreAdded > 0) {
							wordCountArray[i] += 1;
							wordCountArray[i + 1] = wordCountArray[i + 1] + 1;
							wordCountArray[i + 2] = wordCountArray[i + 2] + 1;
							totalWordsScore += scoreAdded;
							searchState.addTokenScore(threeGram, scoreAdded);
							numWordsAdded++;
							indexStartingWordsMMap.put(i, threeGram);
						}
					}
				}
				//long time2 = SimilarThmSearch.printElapsedTime(time1, "time2");
				if (twoGramsMap.containsKey(twoGram)) {
					scoreAdded = addWordThms(thmScoreMap, scoreThmMMap, thmWordSpanMMap, //thmWordsMMap, 
							thmScoreSpanSet, thmPartMap, wordThmsListList, wordIndexPartPairMap, twoGram,//nextWordCombined, 
							i, WordForms.TokenType.TWOGRAM, singletonScoresAr,
							searchWordsSet, dbThmSet, searchState);
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
			// "closed range" all weigh a lot. 
			word = inputWordsArUpdated[i];
			
			//long time3 = System.nanoTime();
			//note many of these 
			scoreAdded = addWordThms(thmScoreMap, scoreThmMMap, thmWordSpanMMap, //thmWordsMMap, 
					thmScoreSpanSet, thmPartMap, wordThmsListList, wordIndexPartPairMap,
					word, i, WordForms.TokenType.SINGLETON, singletonScoresAr, searchWordsSet, dbThmSet, searchState);
			if (scoreAdded > 0) {
				wordCountArray[i] += 1;
				totalWordsScore += scoreAdded;
				searchState.addTokenScore(word, scoreAdded);
				numWordsAdded++;
				indexStartingWordsMMap.put(i, word);
			}			
			//SimilarThmSearch.printElapsedTime(time3, "time3");
		}
		/*sort here to avoid looping over unnecessary thms, since thms lists can be large, e.g. O(10^4) 
		  or sometimes O(10^5)*/
		Collections.sort(wordThmsListList);
		//System.out.println("Sorted wordThmsListList "+wordThmsListList);
		
		/*Only count existing thms as soon as current score exceeds half of total score. */		
		Iterator<WordThmsList> wordThmsListIter = wordThmsListList.iterator();
		Set<IndexPartPair> selectedThmsSet = new HashSet<IndexPartPair>();
		int curScore = 0;
		
		int halfScore = totalWordsScore / 2;
		//avoid duplicating words, or parts, e.g. many times subwords
		//of previous two grams or three grams are check again.  <--maybe don't add them to start with?!
		//but requested thms might be very large, exceeding that of the two gram.
		StringBuilder searchedWordsSb = new StringBuilder(100);
		
		while(wordThmsListIter.hasNext()) {
			
			WordThmsList wordThmsList = wordThmsListIter.next();
			
			String word = wordThmsList.word;
			//If n-gram 
			//already checked, don't check individual words again. E.g. "simplicial object"
			//followed by "simplicia" and "object". "relatively prime" then "relativ", "prime".
			if(searchedWordsSb.toString().contains(word)) {
				continue;
			}			
			searchedWordsSb.append(word).append(" ");
			
			int wordScore = wordThmsList.score;
			Collection<IndexPartPair> wordThms = wordThmsList.thmsList;
			
			if(curScore >= halfScore && selectedThmsSet.size() >= numHighest) {
				//filter out the thms that haven't already been selected on previous words. For efficiency.
				Collection<IndexPartPair> updatedWordThms = new ArrayList<IndexPartPair>();
				for(IndexPartPair pair : wordThms) {
					if(selectedThmsSet.contains(pair)) {
						updatedWordThms.add(pair);
					}
				}				
				wordThms = updatedWordThms;
			}else {
				selectedThmsSet.addAll(wordThms);
				curScore += wordScore;
			}
			
			gatherWordThmsAPosteriori(thmScoreMap, thmWordSpanMMap, thmScoreSpanSet,
					thmPartMap, wordThmsList.wordIndexInThm, wordThmsList.tokenType,
					searchWordsSet, dbThmSet, wordScore, wordThms);			
		}
		
		// add bonus points to thms with most number of query words, judging
		// from size of value set in thmWordSpanMMap
		if(searchState.allowLiteralSearch()) {
			addWordSpanBonus(searchState, thmScoreMap, scoreThmMMap, thmWordSpanMMap, thmSpanMap, numHighest,
					((double) totalWordsScore) / numWordsAdded, inputWordsArSz);
		}else {
			computeLargestSpan(searchState, thmWordSpanMMap, thmSpanMap);
		}
		
		searchState.addThmSpan(thmSpanMap);	
		searchState.setThmScoreMap(thmScoreMap);
		int resultWordSpan = searchState.largestWordSpan();
		/**short circuit if number of token below threshold*/
		if(searchState.allowLiteralSearch() && LiteralSearch.spanBelowThreshold(resultWordSpan, inputWordsArSz)) {
			System.out.println("Initializing literal search...");
			//here
			List<Integer> highestThmList = LiteralSearch.literalSearch(input, resultWordSpan, searchWordsSet, numHighest);
			searchState.set_intersectionVecList(highestThmList);
			return searchState;
		}
		
		// new map to record of the final scores (this obliterates scoreThmMMap)
		// make values into pairs of thms with their span scores
		
		//////TreeMultimap<Integer, ThmSpanPair> scoreThmMMap2 = TreeMultimap.create();
		/***Set<Integer> descendingKeySet = scoreThmMMap.asMap().descendingKeySet();
		Iterator<Integer> descendingKeySetIter = descendingKeySet.iterator(); */
		
		//add all at once to heapify, should be faster than inserting individually, hopefully O(n)
		PriorityQueue<ThmScoreSpanPair> thmScorePQ = new PriorityQueue<ThmScoreSpanPair>(thmScoreSpanSet);
		
		List<ThmScoreSpanPair> thmScoreSpanList = new ArrayList<ThmScoreSpanPair>();
		//***boolean topScorer = true;
		PriorityQueue<ThmScoreSpanPair> thmScorePQ2 = new PriorityQueue<ThmScoreSpanPair>();
		List<Integer> highestThmList = new ArrayList<Integer>();
		int counter = numHighest; //(int)(numHighest*1.5);
		
		ThmScoreSpanPair pair;
		while(counter-- > 0 && null != (pair=thmScorePQ.poll())) {
			
			int index = pair.thmIndex;
			int score = pair.score;
			
			if(ThmPart.HYP == thmPartMap.get(index)) {
				int penalty = score > 7 ? score / 4 : 1;
				score -= penalty;
			}
			
			int thmWordSpan = pair.spanScore;
			thmScorePQ2.add(new ThmScoreSpanPair(index, score, thmWordSpan) );
			
		}
		
		counter = numHighest;
		while(counter-- > 0 && null != (pair=thmScorePQ2.poll())) {
			
			thmScoreSpanList.add(pair);
			highestThmList.add(pair.thmIndex);
		}
		/****Dec 6 outerWhile: while(descendingKeySetIter.hasNext()){
			int curScore = descendingKeySetIter.next();
			Collection<Integer> thmIndices = scoreThmMMap.get(curScore);
			innerFor: for(int thmIndex : thmIndices){
				if(counter-- < 1 ){
					if(!searchState.allowLiteralSearch() || !topScorer) {
						break outerWhile;						
					}
				}
				if(searchState.allowLiteralSearch()) {
					if(!topScorer) {
						//prune away irelevant results
						List<String> thmWords = new ArrayList<String>(thmWordsMMap.get(thmIndex));
						
						//note low scorers can combine to form , e.g. "vanish" and
						//"module". 
						if(inputWordsArSz > 1 && thmWords.size() == 1 ) {
							
							String word = thmWords.get(0);						
							final int lowScoreThreshold = 3;
							Integer wordScore = wordsScoreMap.get(word);
							if(null == wordScore || wordScore <= lowScoreThreshold 
									|| WordForms.genericSearchTermsSet().contains(word)) {
								continue innerFor;
							}
						}
						if(inputWordsArSz > 2 && thmWords.size() < 3) {
							boolean allWordsLowScore = true;
							for(String word : thmWords) {						
								if(!WordForms.genericSearchTermsSet().contains(word)) {
									allWordsLowScore = false;
									break;
								}
							}
							if(allWordsLowScore) {
								continue innerFor;
							}
						}
					}
				}
				int thmWordSpan = thmSpanMap.get(thmIndex);
				////scoreThmMMap2.put(curScore, new ThmSpanPair(thmIndex, thmWordSpan));
				thmScoreSpanPQ.add(new ThmScoreSpanPair(thmIndex, curScore, thmWordSpan) );
			}
			topScorer = false;
		}		*/
		
		// get the thms having the highest k scores. Keys are scores.		
		////NavigableMap<Integer, Collection<ThmSpanPair>> scoreThmDescMMap = scoreThmMMap2.asMap().descendingMap();
		
		// pick up numHighest number of unique thms
		////Set<Integer> pickedThmSet = new HashSet<Integer>();
		// list to track the top entries
		//counter = numHighest;
		/***Searcher<Set<Integer>> relationSearcher = new RelationalSearch();
		Searcher<Map<Integer, Integer>> contextSearcher = new ContextSearch();	
		topScorer = true;*/
		
		if(searchState.largestWordSpan() > 1) {
			//combine with ranking from relational search, reorganize within each tuple
			//of fixed size. Try relation search first, then context search.
			/*if(searchRelationalBool){
				highestThmList = SearchCombined.searchVecWithTuple(input, highestThmList, tupleSz, relationSearcher, searchState);					
			}*/			
			// re-order top entries based on context search, if enabled
			//temporary false - Dec 13
			boolean b = true;
			if (b && contextSearchBool) {						
				Searcher<Map<Integer, Integer>> contextSearcher = new ContextSearch();
				//contextSearcher.setSearcherState(parseState.con);
				highestThmList = SearchCombined.searchVec(input, highestThmList, contextSearcher, searchState);
			}
		}
		/******* DON'T delete Dec 6
		 * outerWhile: for (Entry<Integer, Collection<ThmSpanPair>> entry : scoreThmDescMMap.entrySet()) {		
			List<Integer> tempHighestThmList = new ArrayList<Integer>();
			for (ThmSpanPair pair : entry.getValue()) {
				Integer thmIndex = pair.thmIndex;
				if (counter-- < 1 && !topScorer){
					if(!topScorer || !searchState.allowLiteralSearch()) {
						break outerWhile;
					}
				}
				// avoid duplicates, since the scoreThmMMap leaves outdated
				// score-thm pair in map, rather than deleting them, after
				// updating score
				//if (pickedThmSet.contains(thmIndex)){
				//	continue;
				//} Commented out Dec 6, deleted previous score.
				//pickedThmSet.add(thmIndex);
				tempHighestThmList.add(thmIndex);
				
				if (DEBUG) {
					ThmHypPair thmHypPair = ThmHypPairGet.retrieveThmHypPairWithThm(thmIndex);
					System.out.println(
							"ThmScore " + entry.getKey() + " Span: "+pair.spanScore + " thmIndex " + thmIndex + " thm " 
									+ thmHypPair.thmStr() + " HYP " + thmHypPair.hypStr());
				}
			}
			topScorer = false;
			int tupleSz = tempHighestThmList.size();	
			if(0 == tupleSz){
				continue;
			}
			
			if(searchState.largestWordSpan() > 1) {
				//combine with ranking from relational search, reorganize within each tuple
				//of fixed size. Try relation search first, then context search.
				if(searchRelationalBool){
					tempHighestThmList = SearchCombined.searchVecWithTuple(input, tempHighestThmList, tupleSz, relationSearcher, searchState);					
				}			
				// re-order top entries based on context search, if enabled
				if (contextSearchBool) {						
					tempHighestThmList = SearchCombined.searchVecWithTuple(input, tempHighestThmList, tupleSz, contextSearcher, searchState);
				}
			}
			highestThmList.addAll(tempHighestThmList);
		} */
		logger.info("Highest thm list obtained, intersection search done!");
		searchState.set_intersectionVecList(highestThmList);
		searchState.set_thmScoreSpanList(thmScoreSpanList);
		
		return searchState;
	}
	
	/**
	 * Auxiliary method to add bonus points to theorems containing more words.
	 * Bonus is proportional to the highest thm score.
	 * If the max span is below certain threshold, short-circuit, and don't update spans.
	 * 
	 * @param thmScoreMap
	 * @param scoreThmMMap
	 * @param thmWordSpanMMap
	 * @param thmSpanMap
	 *            map of theorem indices and their spans
	 * @param inputWordsArSz the total number of singleton tokens in query, i.e. max achievable span.
	 */
	private static void addWordSpanBonus(SearchState searchState, Map<Integer, Integer> thmScoreMap, 
			TreeMultimap<Integer, Integer> scoreThmMMap,
			SetMultimap<Integer, Integer> thmWordSpanMMap, Map<Integer, Integer> thmSpanMap, int numHighest,
			double avgWordScore, int inputWordsArSz) {
		// add according to score
		
		int largestWordSpan = computeLargestSpan(searchState, thmWordSpanMMap, thmSpanMap);
		//short-circuit is span insufficient
		if(searchState.allowLiteralSearch() && LiteralSearch.spanBelowThreshold(largestWordSpan, inputWordsArSz)) {
			return;
		}
		
		TreeMultimap<Integer,Integer> tempScoreThmMMap = TreeMultimap.create();
		
		Set<Integer> scoreThmMMapKeySet = scoreThmMMap.asMap().descendingKeySet();
		Iterator<Integer> scoreThmMMapKeySetIter = scoreThmMMapKeySet.iterator();
		boolean topScorer = true;
		int counter = numHighest;
		whileLoop: while(scoreThmMMapKeySetIter.hasNext()){
			int curScore = scoreThmMMapKeySetIter.next();
			Set<Integer> thmIndexSet = scoreThmMMap.get(curScore);
			for(int thmIndex : thmIndexSet){
				//add everything in top tier, for contextual search to kick in.
				//Needed for short search terms, e.g. "finitely generated cover".
				if(counter-- < 1 && !topScorer){
					break whileLoop;
				}
				int thmSpanScore = thmSpanMap.get(thmIndex);
				//int prevScore = thmScoreMap.get(thmIndex);
				
				//int bonusScore = (int) (avgWordScore / ((double) spanCounter * 2));
				int bonusScore = thmSpanScore*2;
				//bonusScore = bonusScore == 0 ? 1 : bonusScore;
				int newThmScore = curScore + bonusScore;
				
				tempScoreThmMMap.put(newThmScore, thmIndex);
				thmScoreMap.put(thmIndex, newThmScore);
				if (DEBUG) {
					ThmHypPair thmHypPair = ThmHypPairGet.retrieveThmHypPairWithThm(thmIndex);
					String thm = thmHypPair.thmStr();
					String hyp = thmHypPair.hypStr();
					System.out.println("Adding bonus " + bonusScore + ". num words hit: " + thmSpanScore
							+ ". newThmScore: " + newThmScore + ". thm: " + thm + " HYP "+ hyp);
					// System.out.println("PREV SCORE " + prevScore + " NEW
					// SCORE " + newThmScore + thm);
				}
			}
			topScorer = false;
		}		
		scoreThmMMap.putAll(tempScoreThmMMap);		
	}

	/**
	 * Gather the sizes of the value maps for thmWordSpanMMap, and keep
	 * track of order based on scores using a TreeMultimap
	 * @param searchState
	 * @param thmWordSpanMMap
	 * @param thmSpanMap
	 * @return
	 */
	private static int computeLargestSpan(SearchState searchState, SetMultimap<Integer, Integer> thmWordSpanMMap,
			Map<Integer, Integer> thmSpanMap) {
		int largestWordSpan = 0;
		
		for (int thmIndex : thmWordSpanMMap.keySet()) {
			
			int thmWordsSetSize = thmWordSpanMMap.get(thmIndex).size();
			thmSpanMap.put(thmIndex, thmWordsSetSize);
			if(thmWordsSetSize > largestWordSpan){
				largestWordSpan = thmWordsSetSize;
			}
		}
		searchState.setLargestWordSpan(largestWordSpan);
		return largestWordSpan;
	}

	/**
	 * Auxiliary method for getHighestVecs. Retrieves thms that contain
	 * wordLong, add these thms to map. Annotated 2 grams only have annotation
	 * at start of first word.
	 * 
	 * @param thmScoreMap
	 * @param scoreThmMMap
	 * @param wordIndexPartPairMap Map of words and Collections of IndexPartPair's, where words must be 
	 * in their original forms, i.e. non-normalized, or even singularized (as of Dec 20.) Actually should 
	 * singularize! Used for literal search.
	 * @param word current word to add thms for
	 * @param wordIndices  array of indices of words in query
	 * @param singletonScoresAr  Array of scores for singleton words
	 * @param set of words, separated into singletons, used during search
	 * @param thmWordSpanMMap Multimap of thmIndex, and the (index of) set of words in query 
	 * that appear in the thm.
	 * @param thmWordsScoreMMap Multimap of thm indices, and the set of the words in them.
	 * @param wordThmIndexAddedMMap Thms that have already been added for the input.
	 * @param wordThmIndexMMap MMap created from tars used to look up thms containing words.
	 * @return scoreAdded
	 */
	private static int addWordThms(Map<Integer, Integer> thmScoreMap, TreeMultimap<Integer, Integer> scoreThmMMap,
			Multimap<Integer, Integer> thmWordSpanMMap, 
			Set<ThmScoreSpanPair> thmScoreSpanSet, Map<Integer, ThmPart> thmPartMap,
			List<WordThmsList> wordThmsListList, Map<String, Collection<IndexPartPair>> wordIndexPartPairMap,
			String word, int wordIndexInThm, WordForms.TokenType tokenType,
			int[] singletonScoresAr, Set<String> searchWordsSet, Set<Integer> dbThmSet, SearchState searchState
			) {
		// update scores map
		int curScoreToAdd = 0;
		String wordOriginalForm = word;
		List<String> relatedWordsList = null;
		// for every word, get list of thms containing this word
		Collection<IndexPartPair> wordThms;
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
			curScoreToAdd = wordScore;
		} else {
			wordSingForm = WordForms.getSingularForm(word);
			Integer wordSingFormScore = wordsScoreMap.get(wordSingForm);
			
			if (null != wordSingFormScore) {
				//long timeMap = System.nanoTime();
				wordThms = wordThmsIndexMMap1.get(wordSingForm);	
				//SimilarThmSearch.printElapsedTime(timeMap, "MAP RETRIEVAL TIME");
				wordScore = wordSingFormScore;
				curScoreToAdd = wordScore;
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
				curScoreToAdd = wordScore;
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
		if(searchState.tokenAlreadySearched(word)) {
			return 0;
		}
		searchState.addNormalizedSearchToken(word);
		
		// removes endings such as -ing, and uses synonym rep.		
		// adjust curScoreToAdd, boost 2, 3-gram scores when applicable
		curScoreToAdd = tokenType.adjustNGramScore(curScoreToAdd, singletonScoresAr, wordIndexInThm);
				
		if (!wordThms.isEmpty() && curScoreToAdd != 0) {
			
			//add thms to list for now
			wordThmsListList.add(new WordThmsList(word, wordThms, curScoreToAdd, tokenType, wordIndexInThm));
			//used for literal search.
			wordIndexPartPairMap.put(wordSingForm, wordThms);
			
			if (DEBUG) {
				System.out.println("SearchIntersection-Word added: " + word + ". Score: " + curScoreToAdd);
			}
			
			/****scoreAdded = gatherWordThmsAPosteriori(thmScoreMap, thmWordSpanMMap, thmScoreSpanSet, thmPartMap, word,
					wordIndexInThm, tokenType, searchWordsSet, dbThmSet, curScoreToAdd, wordOriginalForm, wordThms);*/
			
			// add singletons to searchWordsSet, so
			// searchWordsSet could be null if not interested in searchWordsSet.
			if (curScoreToAdd > 0 && searchWordsSet != null) {
				String[] wordAr = WordForms.getWhiteNonEmptySpaceNotAllPattern().split(word);
				for (String w : wordAr) {
					searchWordsSet.add(w);
				}
				if(!word.equals(wordOriginalForm)){
					searchWordsSet.add(wordOriginalForm);
				}
			}
		}
		if(searchState.allowLiteralSearch()) {
			addRelatedWordsThms(thmScoreMap, scoreThmMMap, thmScoreSpanSet, thmWordSpanMMap, wordIndexInThm, tokenType, curScoreToAdd,
					relatedWordsList, dbThmSet);
		}
		return curScoreToAdd;
	}
	
	/**
	 * Add thms corresponding to words to various score-keeping maps.
	 * @param thmScoreMap
	 * @param thmWordSpanMMap
	 * @param thmScoreSpanSet
	 * @param thmPartMap
	 * @param word
	 * @param wordIndexInThm
	 * @param tokenType
	 * @param searchWordsSet
	 * @param dbThmSet
	 * @param curScoreToAdd
	 * @param wordOriginalForm
	 * @param wordThms
	 * @return score added.
	 */
	private static int gatherWordThmsAPosteriori(Map<Integer, Integer> thmScoreMap,
			Multimap<Integer, Integer> thmWordSpanMMap, Set<ThmScoreSpanPair> thmScoreSpanSet,
			Map<Integer, ThmPart> thmPartMap, int wordIndexInThm, WordForms.TokenType tokenType,
			Set<String> searchWordsSet, Set<Integer> dbThmSet, int curScoreToAdd, 
			Collection<IndexPartPair> wordThms) {
		
		int scoreAdded;
		long beforeLoop = 0;
		if(profileTiming) {
			beforeLoop = System.nanoTime();
		}
		
		for (IndexPartPair thmIndex : wordThms) {
			//note this list could be long, i.e. in hundreds of thousands
			int index = thmIndex.thmIndex();
			
			if(null != dbThmSet && !dbThmSet.contains(index)) {
				continue;
			}
			
			//wordThmIndexAddedMMap.put(word, index);
			
			// skip thm if current word already been covered by previous
			// 2/3-gram
			/*if (tokenType.ifAddedToMap(thmWordSpanMMap, index, wordIndexInThm)){
				continue;
			}*/
			Integer prevScore = thmScoreMap.get(index);
			prevScore = prevScore == null ? 0 : prevScore;
			//lower score if only occurring in context
			/*****if(thmIndex.isContextPart()) {
				int penalty = curScoreToAdd > 5 ? curScoreToAdd / 3 : 1;
				curScoreToAdd -= penalty;
			}*/
			thmIndex.addToMap(thmPartMap);
			
			Integer newScore = prevScore + curScoreToAdd;
			
			/***Dec 6 scoreThmMMap.remove(prevScore, index);
			scoreThmMMap.put(newScore, index);*/
			
			//thmScorePQ.add(new ThmScoreSpanPair(index, newScore, 0));
			// put in thmIndex, and the index of word in the query, to
			// thmWordSpanMMap.
			tokenType.addToMap(thmWordSpanMMap, index, wordIndexInThm, thmScoreSpanSet, 
					newScore, prevScore, thmScoreMap);
			
			//thmWordsMMap.put(index, word);
			
			//thmScoreSet.add(new ThmScoreSpanPair(index, newScore, 0));
		}
		if(profileTiming) SimilarThmSearch.printElapsedTime(beforeLoop, "LOOPING over "+wordThms.size()+" Thms");
		scoreAdded = curScoreToAdd;
		
		return scoreAdded;
	}

	/**
	 * Add thms for related words to to current the thm's actual words.
	 * @param thmScoreMap
	 * @param scoreThmMMap
	 * @param thmWordSpanMMap
	 * @param wordIndexInThm
	 * @param tokenType
	 * @param scoreAdded
	 * @param relatedWordsList
	 */
	private static void addRelatedWordsThms(Map<Integer, Integer> thmScoreMap,
			TreeMultimap<Integer, Integer> scoreThmMMap, Set<ThmScoreSpanPair> thmScoreSet,
			Multimap<Integer, Integer> thmWordSpanMMap, int wordIndexInThm,
			WordForms.TokenType tokenType, int scoreAdded, List<String> relatedWordsList, Set<Integer> dbThmSet) {
		// add thms for related words found, with some reduction factor;
		// make global after experimentation.
		double RELATED_WORD_MULTIPLICATION_FACTOR = 4 / 5.0;
		//int dbThmSetArLen = dbThmSet.length;
		
		if (null != relatedWordsList) {
			// wordScore = wordsScoreMap.get(word);
			int relatedWordScore = (int) Math.ceil(scoreAdded * RELATED_WORD_MULTIPLICATION_FACTOR);
			for (String relatedWord : relatedWordsList) {
				// Multimap, so return empty collection rather than null, if no
				// hit.
				// relatedWordThmIndices.addAll();
				Collection<IndexPartPair> relatedWordThms = wordThmsIndexMMap1.get(relatedWord);
				
				if (!relatedWordThms.isEmpty() && relatedWordScore == 0) {
					Integer score = wordsScoreMap.get(relatedWord);
					if (null == score){
						continue;
					}					
					relatedWordScore = (int) Math.ceil(score * RELATED_WORD_MULTIPLICATION_FACTOR);
				}
				
				for (IndexPartPair thmIndex : relatedWordThms) {
					
					int index = thmIndex.thmIndex();
					if(null != dbThmSet && !dbThmSet.contains(index)) {
						continue;
					}
					
					Integer prevScore = thmScoreMap.get(index);
					prevScore = prevScore == null ? 0 : prevScore;
					
					if(thmIndex.isContextPart()) {
						int penalty = relatedWordScore > 5 ? relatedWordScore/3 : 1;
						relatedWordScore -= penalty;
					}
					
					Integer newScore = prevScore + relatedWordScore;
					
					// related words count towards span, only if the original
					// word not added.
					if (!tokenType.ifAddedToMap(thmWordSpanMMap, index, wordIndexInThm)) {
						// put in thmIndex, and the index of word in the query,
						// to thmWordSpanMMap.
						tokenType.addToMap(thmWordSpanMMap, index, wordIndexInThm, 
								thmScoreSet, newScore, prevScore, thmScoreMap);
					}
					
					// this mapping is not being used in the end right now,
					// since the top N are picked, regardless of their scores.
					thmScoreMap.put(index, newScore);
					//***Dec 6 scoreThmMMap.put(newScore, index);
					//thmScoreSet.add(new ThmScoreSpanPair(index, newScore, 0));
				}
			}
		}
	}

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
			}

			SearchState searchState = new SearchState();		
			// user's input overrides default num
			StringBuilder inputSB = new StringBuilder();
			int numHighest = SearchCombined.getNumCommonVecs(inputSB, thm);
			thm = inputSB.toString();
			
			// searchWordsSet is null.
			Set<String> searchWordsSet = new HashSet<String>();
			List<Integer> highestThms = getHighestThmList(thm, searchWordsSet, searchState, contextSearchBool, 
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
			System.out.println("~~~~~~~~~~~~~~~~~~~~~~ SEARCH RESULTS ~~~~~~~~~~~~~~~~~~~~~~");
			for (Integer thmIndex : highestThms) {
				System.out.println(counter++ + " ++ " + thmIndex + " " + ThmHypPairGet.retrieveThmHypPairWithThm(thmIndex));
			}
		}
		sc.close();
	}

}
