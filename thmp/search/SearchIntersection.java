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
import java.util.Random;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.TreeMultimap;

import thmp.search.CollectThm.ThmWordsMaps.IndexPartPair;
import thmp.search.LiteralSearch.LiteralSearchIndexPair;
import thmp.search.SearchCombined.ThmHypPair;
import thmp.utils.DBUtils.AuthorName;
import thmp.utils.DBUtils.ConjDisjType;
import thmp.utils.FileUtils;
import thmp.utils.GatherRelatedWords.RelatedWords;
import thmp.utils.WordForms;
import thmp.utils.WordForms.ThmPart;
import thmp.utils.WordForms.TokenType;

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
	
	private static final Set<String> stopWordSet;
	
	//default word score for words having thms, but not in precomputed list of scores .
	private static final int defaultWordScore;
	/* Keys to relatedWordsMap are not necessarily normalized, only normalized if key not 
	 * already contained in docWordsFreqMapNoAnno. */
	private static final Map<String, RelatedWords> relatedWordsMap;

	// these maps are not immutable.
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
		stopWordSet = WordForms.stopWordsSet();
		
		int sum = 0;
		//in case the word scores are sorted
		List<Integer> scoresList = new ArrayList<Integer>(wordsScoreMap.values());
		int scoresListSz = scoresList.size();
		
		final int maxCount = 200;
		int counter = maxCount;
		
		Random rand = new Random();
		while(counter-- > 0) {
			int nextRand = rand.nextInt(scoresListSz);
			sum += scoresList.get(nextRand);
		}
		
		int avgScore = (int)(sum / maxCount); 
		
		//at least 2, so related words can have an integral score value that's 1 less
		//but non-zero.
		final int minScore = WordForms.MIN_WORD_SCORE;
		avgScore = avgScore > 0 ? avgScore : minScore;
		defaultWordScore = avgScore;
	}

	/**
	 * Pair of theorem index and its span score.
	 */
	public static class ThmScoreSpanPair implements Comparable<ThmScoreSpanPair> {
		private int thmIndex;
		/*score based on words*/
		private int score;
		//the number of word hits from query for this thm.
		private int spanScore;
		//score based on how closely clustered the words are. The closer the better, the 
		//higher the closer.
		private int wordDistScore;
		//starting index of first matched word from query.
		private int firstStartScore;// = LiteralSearch.LiteralSearchIndex.MAX_WORD_INDEX_AR_VAL;
		
		public ThmScoreSpanPair(int index_, int score_, int spanScore_, int wordDistScore_,
				int firstStartScore_) {
			this.thmIndex = index_;
			this.score = score_;
			this.spanScore = spanScore_;
			this.wordDistScore = wordDistScore_;
			this.firstStartScore = firstStartScore_;
		}

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
					//Already checked whether this.equals(other). But need to put back in in case this tie breaker
					//is called by anything other than the previous tiebreaker!
					: (this.spanScore < other.spanScore ? 1 : tieBreakWithWordDist(other));
		}
		
		private int tieBreakWithWordDist(ThmScoreSpanPair other) {
			// reverse because treemap naturally has ascending order
			return this.wordDistScore > other.wordDistScore ? -1
					//same comment as above applies here.
					: (this.wordDistScore < other.wordDistScore ? 1 : tieBreakWithFirstWordScore(other));
		}
		
		//Tie break with the starting point, favors earlier starting point, per discussion with Michael.
		//since earlier correlates with word being more important.
		private int tieBreakWithFirstWordScore(ThmScoreSpanPair other) {
			// favor lower starting index 
			return this.firstStartScore < other.firstStartScore ? -1
					//same comment as above applies here.
					: (this.firstStartScore > other.firstStartScore ? 1 : -1);
		}
		
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + wordDistScore;
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
			
			if (thmIndex != other.thmIndex)
				return false;
			if (wordDistScore != other.wordDistScore)
				return false;
			if (spanScore != other.spanScore)
				return false;
			if (score != other.score)
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
		//score for *orginal* word, that this word might be related to, is same as score
		//if this word is the original word. Don't use this in compare or equals.
		int originalScore;
		//index of word in the thm. To differentiate amongst two WordThmsLists for a query
		//that are accidentally the equal by previous measures.
		int wordIndexInThm;
		
		WordForms.TokenType tokenType;
		
		WordThmsList(String word_, Collection<IndexPartPair> thmsList_, int score_,
				WordForms.TokenType tokenType_, int wordIndexInThm_){
			this.word = word_;
			this.thmsList = thmsList_;
			this.score = score_;
			this.originalScore = score_;
			this.tokenType = tokenType_;
			this.wordIndexInThm = wordIndexInThm_;
		}
		
		WordThmsList(String word_, Collection<IndexPartPair> thmsList_, int score_,
				int originalScore_,
				WordForms.TokenType tokenType_, int wordIndexInThm_){			
			this(word_, thmsList_, score_, tokenType_, wordIndexInThm_);
			this.originalScore = originalScore_;
		}
		
		/**
		 * Higher scores, and then shorter theorem lists are prioritized.
		 * 
		 */
		@Override
		public int compareTo(WordThmsList other) {
			//want thmsListSize to be first, for efficient looping after sorting.
			int thisSz = thmsList.size();
			int otherSz = other.thmsList.size();
			return this.score < other.score ? 1 : this.score > other.score ? -1 : 
				//shorter thm lists rank higher
				( thisSz > otherSz ? 1 : thisSz < otherSz ? -1 :
					(this.wordIndexInThm > other.wordIndexInThm ? 1 : 
							this.wordIndexInThm < other.wordIndexInThm ? -1 : 
								(this.word.compareTo(other.word)))
					);
		}
		
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + this.thmsList.size();
			result = result * prime + this.score;
			result = result * prime + this.wordIndexInThm;
			result = result + this.word.hashCode();
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
			return this.word.equals(other.word);
		}
		
		@Override
		public String toString() {
			return this.word;
		}
	}
	
	/**
	 * Container for pair of thmPart and TreeMap of word indices in 
	 * that thm, along with the word.
	 * Each instance corresponds to a theorem.
	 */
	public static class WordDistScoreTMap{
		//private ThmPart thmPart;
		/*indices of words in the thm, along with the words.*/
		private TreeMap<Number, String> hypIndexWordTMap;
		private TreeMap<Number, String> thmIndexWordTMap;
		
		public WordDistScoreTMap() {
			hypIndexWordTMap = new TreeMap<Number, String>();
			thmIndexWordTMap = new TreeMap<Number, String>();		
		}
		
		public void addToTreeMap(ThmPart thmPart_, Number index, String word){
			if(thmPart_ == ThmPart.HYP) {
				hypIndexWordTMap.put(index, word);
			}else {
				thmIndexWordTMap.put(index, word);
			}
		}
		
		public TreeMap<Number, String> thmIndexWordTMap(){
			return thmIndexWordTMap;
		}
		
		public TreeMap<Number, String> hypIndexWordTMap(){
			return hypIndexWordTMap;
		}
		
		@Override
		public String toString() {
			return hypIndexWordTMap + "; " + thmIndexWordTMap;
		}
	}
	
	/**
	 * Outward facing function, e.g. to web servlets.
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
				authorThmList = DBSearch.searchByAuthor(authorRelation, searchState, conjDisjType);
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
		//int numWordsAdded = 0;

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
		//map of word in normalized form, with list of thms for that word. Used for literal search.
		Map<String, Collection<IndexPartPair>> wordIndexPartPairMap = new HashMap<String, Collection<IndexPartPair>>();
		
		Set<ThmScoreSpanPair> thmScoreSpanSet = new HashSet<ThmScoreSpanPair>();
		Map<Integer, ThmPart> thmPartMap = new HashMap<Integer, ThmPart>();
		
		ListMultimap<String, WordThmsList> wordThmsListList = ArrayListMultimap.create();
		//map of words and the counts of theorems for that word *without* related words.
		Map<String, Integer> wordThmCountMap = new HashMap<String, Integer>();
		
		// array of words to indicate frequencies that this word was included in
		// either a singleton or n-gram
		int[] wordCountArray = new int[inputWordsArSz];
		
		for (int i = 0; i < inputWordsArSz; i++) {
			//long time0 = System.nanoTime();
			String word = inputWordsList.get(i);
			
			//skip stop words, for stop words in middle of n-grams, e.g. "A of B"
			//would have already recorded the n-gram in previous iterations.
			if(stopWordSet.contains(word)) {
				continue;
			}
			/*This processing pipeline needs to *exactly* match the one when scraping data.
			 * In CollectThm.ThmWordsMaps.addToWordThmIndexMap()*/
			//strip away umlauts, which were stripped when gathering theorems.
			word = WordForms.stripUmlautFromWord(word);
			
			// elicit higher score if wordLong fits
			// also turn into singular form if applicable			
			int scoreAdded = 0;
			
			// check for 2 grams
			if (i < inputWordsArSz - 1) {
				String nextWord = inputWordsList.get(i+1);
				nextWord = WordForms.stripUmlautFromWord(nextWord);
				
				String twoGram = word + " " + nextWord;
				
				//addWordThms() will normalize.
				//twoGram = WordForms.normalizeTwoGram(twoGram);
				//long time1 = SimilarThmSearch.printElapsedTime(time0, "time1");
				
				if (i < inputWordsArSz - 2) {
					String thirdWord = inputWordsList.get(i+2);
					
					thirdWord = WordForms.stripUmlautFromWord(thirdWord);
					String threeGram = twoGram + " " + thirdWord;
					//threeGram = WordForms.stripUmlautFromWord(threeGram);
					
					//if (threeGramsMap.containsKey(threeGram)) {
					//addWordThms will add if proper n-gram.
						scoreAdded = addWordThms(thmScoreMap, scoreThmMMap, thmWordSpanMMap,// thmWordsMMap, thmScoreSpanSet, 
								thmPartMap, wordThmsListList, wordIndexPartPairMap,
								threeGram, i, WordForms.TokenType.THREEGRAM, wordThmCountMap,
								singletonScoresAr, searchWordsSet, dbThmSet, searchState);
						if (scoreAdded > 0) {
							wordCountArray[i] += 1;
							wordCountArray[i + 1] = wordCountArray[i + 1] + 1;
							wordCountArray[i + 2] = wordCountArray[i + 2] + 1;
							totalWordsScore += scoreAdded;
							searchState.addTokenScore(threeGram, scoreAdded);
							//numWordsAdded++;
							indexStartingWordsMMap.put(i, threeGram);
						}
					//}
				}
				//long time2 = SimilarThmSearch.printElapsedTime(time1, "time2");
				if (twoGramsMap.containsKey(twoGram)) {
					scoreAdded = addWordThms(thmScoreMap, scoreThmMMap, thmWordSpanMMap, //thmScoreSpanSet, 
							thmPartMap, wordThmsListList, wordIndexPartPairMap, twoGram,
							i, WordForms.TokenType.TWOGRAM, wordThmCountMap, singletonScoresAr,
							searchWordsSet, dbThmSet, searchState);
					if (scoreAdded > 0) {
						wordCountArray[i] += 1;
						wordCountArray[i + 1] = wordCountArray[i + 1] + 1;
						totalWordsScore += scoreAdded;
						searchState.addTokenScore(twoGram, scoreAdded);
						//numWordsAdded++;
						indexStartingWordsMMap.put(i, twoGram);
					}
				}
			}		
			
			//long time3 = System.nanoTime();
			//This score includes words scores for words related to this word.
			scoreAdded = addWordThms(thmScoreMap, scoreThmMMap, thmWordSpanMMap, //thmScoreSpanSet,
					thmPartMap, wordThmsListList, wordIndexPartPairMap,
					word, i, WordForms.TokenType.SINGLETON, wordThmCountMap, singletonScoresAr, searchWordsSet, dbThmSet, searchState);
			if (scoreAdded > 0) {
				wordCountArray[i] += 1;
				totalWordsScore += scoreAdded;
				//how is this used later?!
				searchState.addTokenScore(word, scoreAdded);
				//numWordsAdded++;
				indexStartingWordsMMap.put(i, word);
			}			
			//SimilarThmSearch.printElapsedTime(time3, "time3");
		}
		
		List<String> originalWordsList = new ArrayList<String>(wordThmCountMap.keySet());
		/*sort here to avoid looping over unnecessary thms, since thms lists can be large, e.g. O(10^4) 
		  or sometimes O(10^5)*/
		//Collections.sort(wordThmsListList);
		//this is reversed, words with higher number of thms come first.
		Collections.sort(originalWordsList, new WordForms.WordFreqComparator(wordThmCountMap));
				
		//Iterator<WordThmsList> wordThmsListIter = wordThmsListList.iterator();
		//Iterator<String> wordThmsListKeyIter = wordThmsListList.keySet().iterator();
		Set<IndexPartPair> selectedThmsSet = new HashSet<IndexPartPair>();
		int curScore = 0;
		int originalWordsListSz = originalWordsList.size();
		
		//list of thm indices, and the indices of words in the thm, along with the words.
		Map<Integer, WordDistScoreTMap> thmWordIndexMap = new HashMap<Integer, WordDistScoreTMap>();
		
		int halfScore = totalWordsScore / 2;
		//avoid duplicating words, or parts, e.g. many times subwords
		//of previous two grams or three grams are check again.  <--maybe don't add them to start with?!
		//but requested thms might be very large, exceeding that of the two gram.
		StringBuilder searchedWordsSb = new StringBuilder(100);
		/* to be used to prune generic-word thms.  e.g. ones whose sole word is "equation", "module".
		   One word per thm index, since only care in case the thm index corresponds to one word. */
		Map<Integer, String> thmPruneWordsMap = new HashMap<Integer, String>();
		
		/*Only count existing thms as soon as current score exceeds half of total score. */
		//iterate over objects consisting of a word and its theorem list.
		for(int i = originalWordsListSz-1; i > -1; i--) {
			
			//these are the original words, not related words that might have fetched the current wordThmsList.
			String word = originalWordsList.get(i);
			//keep track of scores added for this word
			
			//total score added for cur word and all words related to it for the theorems (keys).
			//Keys are thm indices, values are scores that have been added for this word and related words.
			Map<Integer, Integer> thmRelWordsScoreMap = new HashMap<Integer, Integer>();
			
			List<WordThmsList> wordThmsListCol = wordThmsListList.get(word);			
			int originalWordScore = 0;
			
			if(wordThmsListCol.size() > 0) {
				originalWordScore = wordThmsListCol.get(0).originalScore;
			}
			
			//if(curScore < halfScore || selectedThmsSet.size() <= numHighest) {	
			//}
			//don't count related words score.
			curScore += originalWordScore;
			for(WordThmsList wordThmsList : wordThmsListCol) {
				
				//WordThmsList wordThmsList = wordThmsListIter.next();
				String relWord = wordThmsList.word;
				
				//////////////////String word = wordThmsList.word;
				//If n-gram 
				//already checked, don't check individual words again. E.g. "simplicial object"
				//followed by "simplicia" and "object". "relatively prime" then "relativ", "prime".
				if(searchedWordsSb.toString().contains(relWord)) {
					continue;
				}			
				searchedWordsSb.append(relWord).append(" ");
				
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
					//add to selected thms for record keeping to decide if future thms should be added.
					selectedThmsSet.addAll(wordThms);					
				}
				//this function actually loops over the thms.
				gatherWordThmsAPosteriori(thmScoreMap, thmWordSpanMMap, thmScoreSpanSet,
						thmPartMap, thmWordIndexMap, wordThmsList, thmRelWordsScoreMap,//wordThmsList.wordIndexInThm, wordThmsList.tokenType,
						searchWordsSet, dbThmSet, originalWordScore, wordThms, thmPruneWordsMap, word);
			}
		}
		//System.out.println("++++++++++++++++++++++++++++++");
		//System.out.println("thmWordIndexMap.get(717757) " + thmWordIndexMap.get(717757));
		
		//System.out.println("SearchIntersection - thmWordIndexMap "+thmWordIndexMap);
		// add bonus points to thms with most number of query words, judging
		// from size of value set in thmWordSpanMMap
		
		computeLargestSpan(searchState, thmWordSpanMMap, thmSpanMap);
		
		searchState.addThmSpan(thmSpanMap);	
		searchState.setThmScoreMap(thmScoreMap);
		int resultWordSpan = searchState.largestWordSpan();
		/**short circuit if number of token below threshold*/
		//This is not longer used as of March 2018.
		if(false && !FileUtils.isByblis67 && //********temporary Feb 15. Since can't set up db on byblis
				searchState.allowLiteralSearch() && LiteralSearch.spanBelowThreshold(resultWordSpan, inputWordsArSz)) {
			System.out.println("Initializing literal search...");
			
			List<Integer> highestThmList = LiteralSearch.literalSearch(input, searchState, resultWordSpan, searchWordsSet, 
					wordIndexPartPairMap, numHighest);
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
		int maxThmCount = 400;
		int counter = 0;
		//number of original query words, *without* related words.
		//int queryWordsCount = wordThmCountMap.keySet().size();
		
		ThmScoreSpanPair pair = thmScorePQ.poll();
		//if pair is null, then score won't be used.
		int score = null == pair ? -1 : pair.score;
		int firstScore = score;
		//Check score == firstScore, to gather everything with the same score, to give them equal treatment.
		while((counter < numHighest || score == firstScore) && counter < maxThmCount && null != pair) {
			
			int index = pair.thmIndex;
			score = pair.score;
			
			//score based on compare word distances here
			WordDistScoreTMap indexWordTMap = thmWordIndexMap.get(index);
			LiteralSearchIndexPair literalSearchScorePair = LiteralSearch.computeThmWordDistScore(indexWordTMap);
			
			int wordDistScore = literalSearchScorePair.wordDistScore();
			//favor results where query words come first.
			int firstStartScore = literalSearchScorePair.firstStartScore;
			
			//don't penalize score, penalize wordDistScore instead, since want to rank
			//all results containing all keys words above those that contain fewer, and 
			//result ranks are determined by score foremost.
			if(ThmPart.HYP == thmPartMap.get(index)) {
				int penalty = wordDistScore > 7 ? wordDistScore / 4 : 1;
				wordDistScore -= penalty;
			}
			
			int thmWordSpan = pair.spanScore;
			
			thmScorePQ2.add(new ThmScoreSpanPair(index, score, thmWordSpan, wordDistScore, firstStartScore));
			pair=thmScorePQ.poll();
			counter++;
		}
		
		pair = thmScorePQ2.poll();
		firstScore = null == pair ? -1 : pair.score;
		int halfMaxScore = firstScore/2;
		int firstSpan = null == pair ? -1 : pair.spanScore;
		counter = numHighest;
		Set<String> genericSearchTermsSet = WordForms.genericSearchTermsSet();
		//System.out.println("%%%%%%%%%%%%%%%%%%%%%%%%%%%%");
		
		while(counter-- > 0 && null != pair) {
			
			int thmIndex = pair.thmIndex;
			score = pair.score;
		 	boolean isWordGeneric = genericSearchTermsSet.contains(thmPruneWordsMap.get(thmIndex));
			//prune irrelevant results.
			if(pair.spanScore < firstSpan) {
				if(score < halfMaxScore) {
					break;
				}else if(pair.spanScore == 1 && isWordGeneric) {
					break;
				}
			}else if(firstSpan == 1 && inputWordsArSz > 1 && isWordGeneric) { //check search set size
				//e.g. "Banachoid space", where "Banachoid" doesn't yield any hits, so didn't contribute to span.
				break;
			}
			//very useful debug print. Don't delete - April 2018. Need to debug on byblis.
			if(DEBUG) {
				System.out.println(counter+ ": +++ " + pair.thmIndex+". score: "+ pair.score+ " SpanScore " + pair.spanScore + " DistScore " + pair.wordDistScore + " "
						+ " firstStartScore " + pair.firstStartScore + " " + ThmHypPairGet.retrieveThmHypPairWithThmFromCache(pair.thmIndex));
			}
			thmScoreSpanList.add(pair);
			highestThmList.add(thmIndex);
			pair = thmScorePQ2.poll();
		}
		if(DEBUG) {
			System.out.println("%%%%%%%%%%%%%%%%%%%%%%%%%%%%");
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
			//temporary false - Feb 14//now rank the thms here based on word index distances
			boolean b = false;
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
		//logger.info("Highest thm list obtained, intersection search done!");
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
				
				//make sure this score in this map is used! not just score from wordThmsList. 
				
				tempScoreThmMMap.put(newThmScore, thmIndex);
				thmScoreMap.put(thmIndex, newThmScore);
				if (DEBUG) {
					ThmHypPair thmHypPair = ThmHypPairGet.retrieveThmHypPairWithThmFromCache(thmIndex);
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
	 * in normalized form. Used for literal search.
	 * @param word current word to add thms for
	 * @param wordIndices  array of indices of words in query
	 * @param singletonScoresAr  Array of scores for singleton words
	 * @param set of words, separated into singletons, used during search
	 * @param thmWordSpanMMap Multimap of thmIndex, and the (index of) set of words in query 
	 * that appear in the thm.
	 * @param thmScoreSpanSet Set of words hit in thm. *Note* span scores must be the exact number of words hit,
	 * *not* double counting singleton and n-gram overlaps. 
	 * @param thmWordsScoreMMap Multimap of thm indices, and the set of the words in them.
	 * @param wordThmIndexAddedMMap Thms that have already been added for the input.
	 * @param wordThmCountMap map of word and the count of thms without related words thms.
	 * @param searchWordsSet set of words searched, used for highlighting terms in web FE.
	 * @param wordThmIndexMMap MMap created from tars used to look up thms containing words.
	 * @return scoreAdded
	 */
	private static int addWordThms(Map<Integer, Integer> thmScoreMap, TreeMultimap<Integer, Integer> scoreThmMMap,
			Multimap<Integer, Integer> thmWordSpanMMap, //Set<ThmScoreSpanPair> thmScoreSpanSet, 
			Map<Integer, ThmPart> thmPartMap, ListMultimap<String, WordThmsList> wordThmsListList, Map<String, Collection<IndexPartPair>> wordIndexPartPairMap,
			String word, int wordIndexInThm, WordForms.TokenType tokenType, Map<String, Integer> wordThmCountMap,
			int[] singletonScoresAr, Set<String> searchWordsSet, Set<Integer> dbThmSet, SearchState searchState
			) {
		
		int curScoreToAdd = 0;
		String wordOriginalForm = word;
		List<String> relatedWordsList = null;
		// for every word, get list of thms containing this word. 
		Collection<IndexPartPair> wordThms;
		
		//Recall words have all been systematically singularized normalized when gathering and processing data,
		//i.e. when adding to wordThmsMMap
		if(tokenType.equals(TokenType.SINGLETON)) {
			word = WordForms.getSingularForm(word);
		}else {
			word = WordForms.normalizeNGram(word);
		}
		
		Collection<IndexPartPair> singWordThms = wordThmsIndexMMap1.get(word);
		wordThms = singWordThms;		
		
		RelatedWords relatedWords;
		//Normalized forms of stop words are usually the same as the original.
		//these are already skipped when gathering words.
		//boolean isStopWord = stopWordSet.contains(word);
		
		relatedWords = relatedWordsMap.get(word);
		if (null != relatedWords) {
			relatedWordsList = relatedWords.getCombinedList();
		}
		
		Integer wordScore = 0;
		if (!wordThms.isEmpty()) {
			wordScore = wordsScoreMap.get(word);
			//should add nonzero score!!!
			wordScore = wordScore == null ? defaultWordScore : wordScore;
			curScoreToAdd = wordScore;
		} /*else {
			wordSingForm = WordForms.getSingularForm(word);
			
			wordThms = wordThmsIndexMMap1.get(wordSingForm);	
			if (!wordThms.isEmpty()) {
				//long timeMap = System.nanoTime();
				
				Integer wordSingFormScore = wordsScoreMap.get(wordSingForm);
				wordSingFormScore = null == wordSingFormScore ? defaultWordScore : wordSingFormScore;
				
				//SimilarThmSearch.printElapsedTime(timeMap, "MAP RETRIEVAL TIME");
				wordScore = wordSingFormScore;
				curScoreToAdd = wordScore;
				word = wordSingForm;
				//need to repeat this rather than factor it out, since related words can exist for word
				//forms before they are completely normalized.
				if (null == relatedWordsList) {
					relatedWords = relatedWordsMap.get(word);
					if (null != relatedWords) {
						relatedWordsList = relatedWords.getCombinedList();
					}
				}				
			} 
		}*/
		
		if (wordThms.isEmpty()) {
			
			String normalizedWord = WordForms.normalizeWordForm(word);
			Integer tempWordScore = wordsScoreMap.get(normalizedWord);
			wordThms = wordThmsIndexMMap1.get(normalizedWord);
			
			if (!wordThms.isEmpty()) {
				
				tempWordScore = null == tempWordScore ? defaultWordScore : tempWordScore;
				// wordScore = wordsScoreMap.get(singFormLong);
				// wordScore = wordsScoreMap.get(word);
				// wordScore = wordScore == null ? 0 : wordScore;
				wordScore = tempWordScore;
				curScoreToAdd = wordScore;
				word = normalizedWord;
				
				if (null == relatedWordsList) {
					relatedWords = relatedWordsMap.get(word);
					if (null != relatedWords) {
						relatedWordsList = relatedWords.getCombinedList();
					}
				}
			}
		}
		//whether (the normalized form of) word has already been searched, to avoid duplicate work.
		if(searchState.tokenAlreadySearched(word)) {
			return 0;
		}
		searchState.addNormalizedSearchToken(word);
		
		// removes endings such as -ing, and uses synonym rep.		
		// adjust curScoreToAdd, boost 2, 3-gram scores when applicable
		curScoreToAdd = tokenType.adjustNGramScore(curScoreToAdd, singletonScoresAr, wordIndexInThm);
				
		if (!wordThms.isEmpty()) {
			
			if(curScoreToAdd == 0) {
				curScoreToAdd = defaultWordScore;
			}
			
			wordThmsListList.put(word, new WordThmsList(word, wordThms, curScoreToAdd, tokenType, wordIndexInThm));
			//use normalized word for literal search. Construct new map, as wordThms are immutable.
			wordIndexPartPairMap.put(word, new HashSet<IndexPartPair>(wordThms));
			
			wordThmCountMap.put(word, wordThms.size());
			
			if (DEBUG) {
				System.out.println("SearchIntersection-Word added: " + word + ". Score: " + curScoreToAdd);
			}
			
			/****scoreAdded = gatherWordThmsAPosteriori(thmScoreMap, thmWordSpanMMap, thmScoreSpanSet, thmPartMap, word,
					wordIndexInThm, tokenType, searchWordsSet, dbThmSet, curScoreToAdd, wordOriginalForm, wordThms);*/
			
			// add singletons to searchWordsSet. searchWordsSet could be null if not interested in searchWordsSet.
			if (searchWordsSet != null) {
				/*not add individual terms, After discussing with Michael, March 2018. 
				 * String[] wordAr = WordForms.getWhiteNonEmptySpaceNotAllPattern().split(word);
				for (String w : wordAr) {
					searchWordsSet.add(w);
				}*/
				searchWordsSet.add(wordOriginalForm);
				searchWordsSet.add(word);
				
				/*if(!word.equals(wordOriginalForm)){
					searchWordsSet.add(wordOriginalForm);
				}*/
			}
		}
		
		if(searchState.allowLiteralSearch()) {
			//don't add related words score to total word score, i.e. curScoreToAdd
			addRelatedWordsThms(thmScoreMap, scoreThmMMap, //thmScoreSpanSet, 
					thmWordSpanMMap, wordIndexInThm, word, tokenType, curScoreToAdd, relatedWordsList, dbThmSet, wordThmsListList, wordIndexPartPairMap);
		}
		return curScoreToAdd;
	}
	
	/**
	 * Add thms corresponding to a given word to various score-keeping maps.
	 * Called once per instance of a word and all its theorems.
	 * Note thms for this word could have been added already, either due to duplicates
	 * in query, or through earlier related words.
	 * @param thmScoreMap
	 * @param thmWordSpanMMap
	 * @param thmScoreSpanSet Will be sorted later to determine ranking.
	 * @param thmPartMap
	 * @param thmWordIndexMap map of thm index and map of word-index and word.
	 * @param word
	 * @param wordIndexInThm
	 * @param tokenType
	 * @param searchWordsSet
	 * @param dbThmSet
	 * @param curScoreToAdd
	 * @param thmRelWordsScoreMap map of thm index and related words scores. 
	 * @param wordOriginalForm
	 * @param wordThms
	 * @param thmPruneWordsMap used to prune generic-word thms.  e.g. ones whose sole word is "equation", "module".
	 * @return score added.
	 */
	private static void gatherWordThmsAPosteriori(Map<Integer, Integer> thmScoreMap,
			Multimap<Integer, Integer> thmWordSpanMMap, Set<ThmScoreSpanPair> thmScoreSpanSet,
			Map<Integer, ThmPart> thmPartMap, Map<Integer, WordDistScoreTMap> thmWordIndexMap,
			WordThmsList wordThmsList, Map<Integer, Integer> thmRelWordsScoreMap,
			//int wordIndexInThm, WordForms.TokenType tokenType,
			Set<String> searchWordsSet, Set<Integer> dbThmSet, int originalWordScore, 
			Collection<IndexPartPair> wordThms, Map<Integer, String> thmPruneWordsMap, String word) {
		/**
		 * thmScoreMap, thmWordSpanMMap, thmScoreSpanSet,
						thmPartMap, thmWordIndexMap, wordThmsList   , //wordThmsList.wordIndexInThm, wordThmsList.tokenType,
						searchWordsSet, dbThmSet, originalWordScore, wordThms, thmPruneWordsMap, word)
		 */
		//int scoreAdded;
		long beforeLoop = 0;
		if(profileTiming) {
			beforeLoop = System.nanoTime();
		}
		
		//for(WordThmsList wordThmsList : wordThmsListSet) {
		int wordIndexInThm = wordThmsList.wordIndexInThm; 
		WordForms.TokenType tokenType = wordThmsList.tokenType;
		//doesn't need to be original word, could be a word related to input word.
		String relWord = wordThmsList.word;
		int relWordScore0 = wordThmsList.score;
		
		boolean isOriginalWord = relWord.equals(word);
		
		for (IndexPartPair thmIndexPair : wordThms) {
			//note this list could be long, i.e. in hundreds of thousands
			int index = thmIndexPair.thmIndex();
			
			if(null != dbThmSet && !dbThmSet.contains(index)) {
				continue;
			}
			
			thmPruneWordsMap.put(index, word);
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
			thmIndexPair.addToMap(thmPartMap);
			
			WordDistScoreTMap wordIndexTMap = thmWordIndexMap.get(index);
			wordIndexTMap = null == wordIndexTMap ? new WordDistScoreTMap() : wordIndexTMap;			
			
			ThmPart thmPart = thmIndexPair.thmPart();
			//array of word indices in thm, used for word-distance based scoring.
			byte[] wordIndexAr = thmIndexPair.wordIndexAr();
			for(byte b : wordIndexAr) {
				//limited to 128 because bytes comparison needed in treemap.
				wordIndexTMap.addToTreeMap(thmPart, b, word);
			}
			// used to prune generic-word thms, e.g. ones whose sole word is "equation", "module", etc.
			/////////thmIndexPair.setWord(word);
						
			thmWordIndexMap.put(index, wordIndexTMap);
			
			Integer newScore;
			//score to add for this thm.
			int scoreToAdd;
			//these realWordsScores is capped at the true score of the original word by the implementation.
			Integer relWordsScore = thmRelWordsScoreMap.get(index);
			if(relWordsScore != null  /*need to differentiate between actual word and related words! */ ) {
				if(isOriginalWord) {
					int diff = originalWordScore - relWordsScore;
					scoreToAdd = diff < 0 ? 0 : diff;					
				}else {
					//is a related word, should have total at most originalWordScore - 1
					int diff = originalWordScore - 1 - relWordsScore;
					scoreToAdd = Math.min(relWordScore0, diff < 0 ? 0 : diff);					
				}
				thmRelWordsScoreMap.put(index, relWordsScore + scoreToAdd);
			}else {			
				if(isOriginalWord) {	
					scoreToAdd = originalWordScore;
				}else {
					//is a related word.
					scoreToAdd = relWordScore0;	
				}
				thmRelWordsScoreMap.put(index, scoreToAdd);
			}			
			newScore = prevScore + scoreToAdd;
			
			/***Dec 6 scoreThmMMap.remove(prevScore, index);
			scoreThmMMap.put(newScore, index);*/
			
			//thmScorePQ.add(new ThmScoreSpanPair(index, newScore, 0));
			// put in thmIndex, and the index of word in the query, to
			// thmWordSpanMMap.
			tokenType.addToMap(thmWordSpanMMap, index, wordIndexInThm, thmScoreSpanSet, 
					newScore, prevScore, thmScoreMap);			
			
		}
		//}
		if(profileTiming) SimilarThmSearch.printElapsedTime(beforeLoop, "LOOPING over "+wordThms.size()+" Thms");
		//This is an approximation, used to estimate score half-point.
		//scoreAdded = relWordScore;
		
		//return scoreAdded;
	}

	/**
	 * Add thms for related words to to current the thm's actual words.
	 * @param thmScoreMap
	 * @param scoreThmMMap
	 * @param thmWordSpanMMap
	 * @param wordIndexInQuery
	 * @param tokenType
	 * @param wordThmsListMMap Multimap of WordThmsList's.
	 * @param originalWordScore
	 * @param relatedWordsList
	 */
	private static int addRelatedWordsThms(Map<Integer, Integer> thmScoreMap,
			TreeMultimap<Integer, Integer> scoreThmMMap, //Set<ThmScoreSpanPair> thmScoreSet,
			Multimap<Integer, Integer> thmWordSpanMMap, int wordIndexInQuery, String originalWord,
			WordForms.TokenType tokenType, int originalWordScore, List<String> relatedWordsList, Set<Integer> dbThmSet,
			ListMultimap<String, WordThmsList> wordThmsListMMap, Map<String, Collection<IndexPartPair>> wordIndexPartPairMap) {
		// add thms for related words found, with some reduction factor;
		// make global after experimentation. But *must not* exceed original word score,
		//to make original hits rank higher. Want score for input word to be 3 or higher.
		final double RELATED_WORD_MULTIPLICATION_FACTOR = 3.1 / 5.0;
		//int dbThmSetArLen = dbThmSet.length;
		int totalRelatedScoreAdded = 0;
		final int maxTotalScoreAdded = originalWordScore;
		//prevent words e.g. "gaussian" having too many related words.
		int maxRelatedWords = 4;
		
		if (null != relatedWordsList && !relatedWordsList.isEmpty()) {
			//gradually decrease score?!
			
			// wordScore = wordsScoreMap.get(word);
			int relatedWordScore = (int) Math.ceil(originalWordScore * RELATED_WORD_MULTIPLICATION_FACTOR);
			relatedWordScore = relatedWordScore == originalWordScore && originalWordScore > 0 ? originalWordScore - 1 : relatedWordScore;			
			//if score 0, would give no advantage to thms having more terms related to search terms, if not those search
			//terms on the nose. These are controlled by maxTotalScoreAdded at the end.
			relatedWordScore = relatedWordScore == 0 ? 1 : relatedWordScore;
			
			for (String relatedWord : relatedWordsList) {
				// Multimap, so return empty collection rather than null, if no hit.
				if(maxRelatedWords-- < 1) {
					break;
				}
				//need to create new Set each time, due to deliberate immutability of wordThmsIndexMMap1 
				Collection<IndexPartPair> relatedWordThms = new HashSet<IndexPartPair>(wordThmsIndexMMap1.get(relatedWord));
				
				/*if (relatedWordScore == 0 && !relatedWordThms.isEmpty()) {
					//Integer score = wordsScoreMap.get(relatedWord);
					//use default score rather than actual score, so related words hits don't
					//precede actual word queries. 
					int score = defaultWordScore;
					relatedWordScore = (int) Math.ceil(score * RELATED_WORD_MULTIPLICATION_FACTOR);
				}*/
				
				if(!relatedWordThms.isEmpty()) {
					/*Need to prevent thms containing many related words from outranking direct hits.
					e.g. "Perron-Frobenius" query top results contained non-direct hits, due to related words. 
					But don't iterate over all relatedWordThms, since these are large and iterating has proven to drastically slow down search.*/
					wordThmsListMMap.put(originalWord, new WordThmsList(relatedWord, relatedWordThms, relatedWordScore, originalWordScore, tokenType, wordIndexInQuery));
					//use normalized word for literal search.
					Collection<IndexPartPair> pairCol = wordIndexPartPairMap.get(relatedWord);
					if(null != pairCol) {
						pairCol.addAll(relatedWordThms);
					}else {
						wordIndexPartPairMap.put(relatedWord, relatedWordThms);
					}
					
					if (DEBUG) {
						System.out.println("SearchIntersection-RELATED Word added: " + relatedWord + ". Score: " + relatedWordScore);
					}
					
					// add singletons to searchWordsSet, so
					// searchWordsSet could be null if not interested in searchWordsSet.
					/*Don't highlight related words, per Michael. March 2018.
					 * if (searchWordsSet != null) {
						String[] wordAr = WordForms.getWhiteNonEmptySpaceNotAllPattern().split(relatedWord);
						for (String w : wordAr) {
							searchWordsSet.add(w);
						}
					}*/
					totalRelatedScoreAdded += relatedWordScore;
				}				
				
				/** Feb 19, 2018 Done later when iterating over thms added for (IndexPartPair thmIndex : relatedWordThms) {
					
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
					
					thmScoreMap.put(index, newScore);
					//***Dec 6 scoreThmMMap.put(newScore, index);
					//thmScoreSet.add(new ThmScoreSpanPair(index, newScore, 0));
				}*/
			}
		}
		return totalRelatedScoreAdded > maxTotalScoreAdded ? maxTotalScoreAdded : totalRelatedScoreAdded;
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
				System.out.println(counter++ + " ++ " + thmIndex + " " + ThmHypPairGet.retrieveThmHypPairWithThmFromCache(thmIndex));
			}
		}
		sc.close();
	}

}
