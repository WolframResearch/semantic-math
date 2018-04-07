package thmp.search;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;
import com.google.common.collect.TreeMultimap;
import com.wolfram.puremath.dbapp.DBUtils;
import com.wolfram.puremath.dbapp.LiteralSearchUtils;

import thmp.search.CollectThm.ThmWordsMaps.IndexPartPair;
import thmp.search.LiteralSearch.LiteralSearchIndex;
import thmp.search.SearchIntersection.WordDistScoreTMap;
import thmp.utils.FileUtils;
import thmp.utils.WordForms;

/**
 * Search the literal words as last resort, if the other search
 * algorithms don't return good results. This alleviates the lack
 * of particular words that are not in the lexicon.
 * 
 * @author yihed
 */
public class LiteralSearch {

	private static final Set<String> trueFluffWordsSet = WordFrequency.ComputeFrequencyData.trueFluffWordsSet();
	private static final Map<String, Integer> wordsScoreMap = CollectThm.ThmWordsMaps.get_wordsScoreMap();
	private static final ListMultimap<String, LiteralSearchIndex> literalSearchIndexMap;
	private static final int WORD_BASE_POINT = 1;
	//point when word is right next to another word
	private static final int WORD_DIST_1_POINT = 2;
	private static final int WORD_DIST_2_3_POINT = 1;
	/**threshold after which to perform literal word search*/
	private static final double literalSearchTriggerThreshold = 0.5;
	private static final double literalSearchTriggerThreshold2 = 0.4;
	private static final Set<String> INVALID_SEARCH_WORD_SET;
	//max word length allowed to be a literal word.
	public static final int LITERAL_WORD_LEN_MAX = 15;
	private static final Pattern DIGIT_PATTERN = Pattern.compile("\\d+");
	private static final Logger logger = LogManager.getLogger(LiteralSearch.class);
	/*deliberately includes slash \\. Allows umlaut chars that are preceded by slash.*/
	private static final Pattern LITERAL_SPECIAL_CHARS_PATTERN 
		= Pattern.compile("([-\\{\\[\\)\\(\\}\\]$%/|?@*.;,:_~!+^&+<>=#]|\\\\(?![\"\'`])|(?<!\\\\)\"|(?<!\\\\)\'|(?<!\\\\)`)");
	private static final int MIN_SCORE = 0;

	static {		
		//logger.info("LiteralSearchIndex.class.getClassLoader(): "+LiteralSearchIndex.class.getClassLoader());
		//move to db!
		literalSearchIndexMap = deserializeIndexMap();
		
		INVALID_SEARCH_WORD_SET = new HashSet<String>();
		//fluff words that not in trueFluffWordsSet
		String[] invalidSearchWordAr = new String[] {"with","then","those","there","their",
				"let","for"};
		for(String fluffWord : invalidSearchWordAr) {
			INVALID_SEARCH_WORD_SET.add(fluffWord);
		}
		Set<String> stopWordsSet = WordForms.stopWordsSet();
		for(String fluffWord : stopWordsSet) {
			INVALID_SEARCH_WORD_SET.add(fluffWord);
		}
	}
	
	/**
	 * Class for recording the thm index containing a particular word, 
	 * and the index the word shows up in the theorem.
	 */
	public static class LiteralSearchIndex implements Serializable{
		
		private static final long serialVersionUID = -5527085309265213755L;
		
		/**thm index in total corpus*/
		private int thmIndex;
		/**Array of word indices in thm, for the word that this LiteralSearchIndex
		 * is for. E.g. "a b a", wordIndexAr for "a" is [0, 2]. Length of this array
		 * should usually be ~1 or 2*/
		private byte[] wordIndexAr;
		
		/**Maximum length of the index array of words in a theorem. 
		 * Stop counting after this len. (This also happens to be Maximum length,
		 * but wordIndexAr length should usually be ~1 or 2)*/
		public static final byte MAX_WORD_INDEX_AR_VAL = Byte.MAX_VALUE;
		
		/**number of bytes necessary to store a word index */
		public static final byte NUM_BITS_PER_WORD_INDEX = DBUtils.NUM_BITS_PER_BYTE;
		/**Max length allowed in wordIndexAr, i.e. number of indices to record per word, since
		 * a word can occur multiple times in thm*/
		public static final byte MAX_INDEX_COUNT_PER_WORD = 2;
		
		public static final int PLACEHOLDER_INDEX = -1;
		
		/**
		 * Input list must be sorted in increasing order.
		 * @param thmIndex_
		 * @param wordIndexAr_ IllegalArgumentException is not sorted.
		 */
		public LiteralSearchIndex(int thmIndex_, byte[] wordIndexAr_) {
			
			int wordIndexListSz = wordIndexAr_.length;
			if(wordIndexListSz > 1) {
				//System.out.println("wordIndexList_.get(0).getClass() "+ (wordIndexList_.get(0).getClass()));
				byte prior = wordIndexAr_[0];
				for(int i = 1; i < wordIndexListSz; i++) {
					byte current = wordIndexAr_[i];
					if(current < prior) {
						System.out.println("current, prior: "+ current+ " " +prior);
						throw new IllegalArgumentException("Input word index list must be sorted in "
								+ "ascending order.");
					}
					prior = current;
				}
			}			
			this.thmIndex = thmIndex_;			
			this.wordIndexAr = wordIndexAr_;
		}
		
		public int thmIndex() {
			return this.thmIndex;
		}
		
		public byte[] wordIndexAr() {
			return this.wordIndexAr;
		}
		
		@Override
		public String toString() {
			return "{" + thmIndex + " " + Arrays.toString(wordIndexAr) + "}";
		}
	}	
	
	/**
	 * Used for returning pairs of literal search scores.
	 */
	public static class LiteralSearchIndexPair{
		int wordDistScore;
		int firstStartScore;
		
		public LiteralSearchIndexPair(int wordDistScore_, int firstIndexScore_) {
			this.wordDistScore = wordDistScore_;
			this.firstStartScore = firstIndexScore_;
		}
		
		public int wordDistScore() {
			return this.wordDistScore;
		}
		
		public int firstStartScore() {
			return this.firstStartScore;
		}
	}
	
	/********Tools for building literal search index map, at data processing time*******/

	/**
	 * Tokenizes thm into words, create LiteralSearchIndex's, and add to supplied
	 * index map.
	 * @param thm
	 * @param thmIndex
	 * @param searchIndexMap Multimap of word and LiteralSearchIndex objects.
	 */
	public static void addThmLiteralSearchIndexToMap (String thm, int thmIndex, 
			ListMultimap<String, LiteralSearchIndex> searchIndexMap) {
		
		ListMultimap<String, Byte> wordsIndexMMap = extractWordIndexMMapFromThm(thm);
		
		for(String word : wordsIndexMMap.keys()) {
			//must wrap in ArrayList, since the List from .get() is a RandomAccessWrappedList, 
			//which is not serializable
			List<Byte> wordIndexList = wordsIndexMMap.get(word);
			int wordIndexListSz = wordIndexList.size();
			if(LiteralSearchIndex.MAX_INDEX_COUNT_PER_WORD < wordIndexListSz) {
				wordIndexListSz = LiteralSearchIndex.MAX_INDEX_COUNT_PER_WORD;
			}
			byte[] wordIndexAr = new byte[wordIndexListSz];
			
			for(int i = 0; i < wordIndexListSz; i++) {
				wordIndexAr[i] = wordIndexList.get(i).byteValue();
			}
			searchIndexMap.put(word, new LiteralSearchIndex(thmIndex, wordIndexAr));
		}		
	}

	/**
	 * Used for extracting word indices in thm, used for scoring in literal search, 
	 * and intersection search.
	 * @param thm
	 * @return
	 */
	private static ListMultimap<String, Byte> extractWordIndexMMapFromThm(String thm) {
		//don't count English fluff words, e.g. "how", "the"
		ListMultimap<String, Byte> wordsIndexMMap = ArrayListMultimap.create();
		
		List<String> thmWordsSet = WordForms.splitThmIntoSearchWordsList(thm.toLowerCase());
		//space optimization
		//int num = 0;
		/*byte counter = Byte.MIN_VALUE;*/
		//to limit size of db table to 128 rather than 256.
		//byte counter = 0; <--actually use 256, 
		/*so word index can be recorded within a range of 256 values */
		byte counter = Byte.MIN_VALUE;
		
		for(String word : thmWordsSet) {
			
			if(isInValidSearchWord(word)) {
				continue;
			}
			//need to undergo exact same processing as when indexing thms or searching for queries.
			word = processLiteralSearchWord(word);
			//don't count those already in lexicon map, since they are already counted by the lexicon-thm map.
			if(wordsScoreMap.containsKey(word)) {
				continue;
			}
			
			//System.out.print("word, counter " + word+ "  "+counter + ".\t");
			wordsIndexMMap.put(word, counter);
			//num++;
			//System.out.println("counter.getClass() "+counter.getClass());			
			if(counter == LiteralSearchIndex.MAX_WORD_INDEX_AR_VAL) {
				//don't exceed max byte value, so every index can be stored in a byte.
				break;
			}
			counter++;
			
			/*if(thmWordsListSz > 127) {
				short base = counter.shortValue();
				counter = (short)(base + increment);
			}else {
				byte base = counter.byteValue();
				counter = (byte)(base + increment);
			}*/			
		}
		return wordsIndexMMap;
	}
	
	private static ListMultimap<String, LiteralSearchIndex> deserializeIndexMap() {
		String path = FileUtils.getPathIfOnServlet(Searcher.SearchMetaData.literalSearchIndexMapPath());
		
		@SuppressWarnings("unchecked")
		ListMultimap<String, LiteralSearchIndex> indexMap = ((List<ListMultimap<String, LiteralSearchIndex>>)FileUtils
				.deserializeListFromFile(path)).get(0);
		
		return indexMap;
	}

	/********Tools for searching*******/
	
	/**
	 * Performs literal search on query, after the other algorithms
	 * produce sub-optimal results, e.g. < 50% words hit.
	 * This will start without regard to the search from the 
	 * other algorithms.
	 * Pass in original query string instead of set of words, 
	 * since could use word ordering in query later on.
	 * Note that literal search words *are normalized* during processing.
	 * Provide thm indices from previous algorithms! To not waste those results.
	 * @param query ALl lower-case
	 * @param priorWordSpan Word span of previous search algorithms wrt user's query words.
	 * @param searchWordsSet set of words to be used for highlighting on the web FE.
	 * @param wordIndexPartPairMap map of word and their collection of thms. To take prior found in 
	 * intersection search into account
	 * @param maxThmCount the max number of thms that should be returned, optional param.
	 * @return
	 * List from literal search. Empty list if literal search doesn't improve word span,
	 * for e.g. "klfjk module"
	 */
	public static List<Integer> literalSearch(String query, SearchState searchState, int priorWordSpan, 
			Set<String> searchWordsSet,	Map<String, Collection<IndexPartPair>> wordIndexPartPairMap,
			int...maxThmCountAr){
		
		List<String> queryWordList = WordForms.splitThmIntoSearchWordsList(query);
		//System.out.println("in literalSearch query words: "+ queryWordList);
		//list of thm indices, and the indices of words in the thm, along with the words.
		Map<Integer, TreeMap<Number, String>> thmIndexWordMap = new HashMap<Integer, TreeMap<Number, String>>();
		int wordSpan = 0;
		
		Connection conn = searchState.databaseConnection();
		//Connection conn = thmp.utils.DBUtils.getPooledConnection();
		
		//Max length allowed for wordIndexAr. E.g. 2.
		int wordIndexArLen = LiteralSearchIndex.MAX_INDEX_COUNT_PER_WORD;
		//multiset of thm indices and the count of words for each index, where words
		//are not found in literal search db, but in lexicon
		Multiset<Integer> thmWordCountMSet = HashMultiset.create();
		
		for(String word : queryWordList) {
			if(isInValidSearchWord(word)) {
				continue;
			}
			word = processLiteralSearchWord(word);
			searchWordsSet.add(word);
			
			//list of thm indices for given word. Request indices from db!!
			//List<LiteralSearchIndex> thmIndexList = literalSearchIndexMap.get(word);
			
			//getLiteralSearchThmsFromDB String word,  Connection conn,
			//List<Integer> thmIndexList, List<Integer> wordsIndexArList
			List<Integer> thmIndexList = new ArrayList<Integer>();
			//list of words indices in respective theorems.
			List<Integer> wordsIndexArList = new ArrayList<Integer>();
			try {
				LiteralSearchUtils.getLiteralSearchThmsFromDB(word, conn, thmIndexList, wordsIndexArList);
			}catch(SQLException e) {
				logger.error("SQLException when getting literal search data!" + e);
				continue;
			}
			int thmIndexListSz = thmIndexList.size();			
			int wordsIndexArListSz = wordsIndexArList.size();
			if((thmIndexListSz * wordIndexArLen) != wordsIndexArListSz) {
				throw new IllegalArgumentException("Literal search db table inconsistency: "
						+ "(thmIndexListSz * maxWordIndexArLen) != wordsIndexArListSz");
			}
			 
			//if empty, check list from intersection search
			if(0 == thmIndexListSz) {
				Collection<IndexPartPair> wordIndexPartPairCol = wordIndexPartPairMap.get(word);
				if(null != wordIndexPartPairCol) {
					for(IndexPartPair pair : wordIndexPartPairCol) {
						thmWordCountMSet.add(pair.thmIndex());
					}
					wordSpan++;
				}		
			}else {
				wordSpan++;
			}
			//gather maps used for scoring based on word distances.
			for(int i = 0; i < thmIndexListSz; i++) {
				
				int thmIndex = thmIndexList.get(i);
				//map of word index in thm, and that word.
				TreeMap<Number, String> indexWordMap = thmIndexWordMap.get(thmIndex);
				
				indexWordMap = null == indexWordMap ? new TreeMap<Number, String>() : indexWordMap;
				
				int endIndex = (i+1) * wordIndexArLen;
				for(int j = i * wordIndexArLen; j < endIndex; j++) {			
					int wordIndexInThm = wordsIndexArList.get(j);
					//PLACEHOLDER_INDEX is a filler. (consecutive)
					if(wordIndexInThm == LiteralSearchIndex.PLACEHOLDER_INDEX) {
						break;
					}
					indexWordMap.put(wordIndexInThm, word);
				}				
				thmIndexWordMap.put(thmIndex, indexWordMap);
			}
		}
		//Jan 7 thmp.utils.DBUtils.closePooledConnection(conn);
		
		//need upper bound, since some results for long queries are still meaningful despite low span.
		final int wordSpanUpperBound = 3;
		if(wordSpan <= priorWordSpan && wordSpan < wordSpanUpperBound) {
			return Collections.emptyList();
		}
		Map<Integer, Integer> thmScoreMap = createThmScoreMap(thmIndexWordMap, thmWordCountMSet);
		TreeMultimap<Integer, Integer> scoreThmMMap = TreeMultimap.create();
		for(Map.Entry<Integer, Integer> thmScoreEntry : thmScoreMap.entrySet()) {
			//negate, so higher scores come first.
			scoreThmMMap.put(-thmScoreEntry.getValue(), thmScoreEntry.getKey());
		}
		
		int maxThmCount = maxThmCountAr.length > 0 ? maxThmCountAr[0] : Integer.MAX_VALUE;
		int counter = 0;
		List<Integer> thmIndexList = new ArrayList<Integer>();
		
		for(int thmIndex : scoreThmMMap.values()) {			
			if(++counter > maxThmCount) {
				break;
			}
			thmIndexList.add(thmIndex);
		}
		return thmIndexList;
	}

	/**
	 * Singularize and normalize word, to be used for literal search.
	 * @param word
	 * @return
	 */
	private static String processLiteralSearchWord(String word) {
		word = WordForms.stripUmlautFromWord(word);
		word = WordForms.getSingularForm(word);
		word = WordForms.normalizeWordForm(word);
		return word;
	}
	
	/**
	 * Determines if word should be added as valid literal search word,
	 * non-valid words include those containing special chars, $, \, etc.
	 * Need to screen out placeholder char, '\uFFFD', e.g. in "term�"
	 * This is also used to generate words for intersection search!
	 * @assumption umlauts have been stripped.
	 * @param word Already singularized and normalized.
	 * @return True if *in*valid search word
	 */
	public static boolean isInValidSearchWord (String word) {
		if(LITERAL_SPECIAL_CHARS_PATTERN.matcher(word).find()
				|| DIGIT_PATTERN.matcher(word).find()){
			return true;
		}
		if(INVALID_SEARCH_WORD_SET.contains(word)
				|| word.contains("\uFFFD")) {
			return true;
		}
		int wordLen = word.length();
		if(wordLen < 3 || wordLen > LITERAL_WORD_LEN_MAX) {
			return true;
		}
		return false;		
	}
	
	/**
	 * Creates thm score map based on distances between words in a thm.
	 * A word next to another keyword earns 2 points, dist 1 or 2 apart 1 pt, else no point.
	 * This is to encourage words to appear closely together as in query, emulating n-grams.
	 * Base point total is number of distinct words. 
	 * @param thmIndexWordMap Map where keys are thm index, and value are map of word index and word.
	 * Word indices are relative, i.e. an index can be negative! Add Byte.MAX_VALUE to get absolute index.
	 * @param thmWordCountMSet Multiset of thm indices, where the count reflects the number of words for that index.
	 * @return thm score map of thm index and thm score.
	 */
	private static Map<Integer, Integer> createThmScoreMap(Map<Integer, TreeMap<Number, String>> thmIndexWordMMap,
			Multiset<Integer> thmWordCountMSet) {
		
		Map<Integer, Integer> thmScoreMap = new HashMap<Integer, Integer>();		

		for(Map.Entry<Integer, TreeMap<Number,String>> thmIndexWordEntry : thmIndexWordMMap.entrySet()) {
			int thmIndex = thmIndexWordEntry.getKey();
			
			LiteralSearchIndexPair scorePair = computeThmWordDistScore(thmIndexWordEntry.getValue());
			
			int thmScore = scorePair.wordDistScore;
			//add word count as base score.
			//thmScore += thmWordCountMSet.count(thmIndex);
			thmScore += thmIndexWordEntry.getValue().size()*WORD_BASE_POINT;
			
			//MIN_SCORE if only keyword is generic.
			if(thmScore > MIN_SCORE) {
				thmScoreMap.put(thmIndex, thmScore);				
			}
		}		
		return thmScoreMap;
	}

	/**
	 * Computes the score for a thm based on the ordering of the words in thm.
	 * Base points are assumed to be added by caller.
	 * @param indexWordMap TreeMap of word index in thm and the word.
	 * @param map map for thms gathered during intersection search.
	 * @return MIN_SCORE if contains only generic word
	 */
	public static LiteralSearchIndexPair computeThmWordDistScore(WordDistScoreTMap wordDistMapContainer) {
		
		TreeMap<Number,String> hypIndexWordMap = wordDistMapContainer.hypIndexWordTMap();
		TreeMap<Number,String> thmIndexWordMap = wordDistMapContainer.thmIndexWordTMap();
		
		LiteralSearchIndexPair hypPair = computeThmWordDistScore(hypIndexWordMap);
		LiteralSearchIndexPair thmPair = computeThmWordDistScore(thmIndexWordMap);
		
		//int totalScore = computeThmWordDistScore(hypIndexWordMap) + computeThmWordDistScore(thmIndexWordMap);
		//could penalize hypPair wordDistScore to lower ranking, but they are already effectively punished by 
		//their firstStartScore defaulting to the max.
		LiteralSearchIndexPair wholePair = new LiteralSearchIndexPair(hypPair.wordDistScore + thmPair.wordDistScore, 
				thmPair.firstStartScore);
		
		//only count the firstWordScore for thmIndexMap
		return wholePair;
	}
	
	/**
	 * Computes the score for a thm based on the ordering of the words in thm.
	 * Base points are assumed to be added by caller.
	 * @param indexWordMap TreeMap of word index in thm and the word.
	 * @param map map for thms gathered during intersection search.
	 * @return MIN_SCORE if contains only generic word
	 */
	private static LiteralSearchIndexPair computeThmWordDistScore(TreeMap<Number, String> indexWordMap) {
		
		//map of words, and the number of points that word is earning
		Map<String, Integer> wordScoreMap = new HashMap<String, Integer>();
		
		List<Number> wordsList = new ArrayList<Number>(indexWordMap.keySet());
		int wordsListSz = wordsList.size();
		//just giving lowest position suffices. Used to give preference to results
		//containing matched words at the beginning.
		int lowestIndex = LiteralSearchIndex.MAX_WORD_INDEX_AR_VAL;
		
		if(wordsListSz < 2) {
			//base points (BASE_SCORE*number of words) are assumed to be added by caller.
			//so don't need to add here.
			return new LiteralSearchIndexPair(MIN_SCORE, lowestIndex);
		}
		
		Number priorWordIndex = wordsList.get(0);
		//list is ordered from smallest to largest.
		lowestIndex = priorWordIndex.intValue();
		
		String priorWord = indexWordMap.get(priorWordIndex);
		wordScoreMap.put(priorWord, 0);
		
		//duplicate words count once to their nearest word,
		//but can serve to be near multiple words.
		for(int i = 1; i < wordsListSz; i++) {
			Number wordIndex = wordsList.get(i);
			String word = indexWordMap.get(wordIndex);
			
			int nGramBonus = 0;
			//add bonus for each space-separated word
			for(char c : word.toCharArray()) {
				if(c == ' ') {
					nGramBonus += WORD_DIST_1_POINT;
				}
			}
			
			int distToPriorWord = wordIndex.intValue() - priorWordIndex.intValue();
			int wordScore;
			switch(distToPriorWord) {
			case 1:
				wordScore = WORD_DIST_1_POINT;
				break;
			case 2:
			case 3:
				wordScore = WORD_DIST_2_3_POINT;
				break;
			default:
				wordScore = 0;				
			}
			
			wordScore += nGramBonus;
			
			Integer prevWordScore = wordScoreMap.get(word);
			wordScore = prevWordScore == null ? wordScore : (wordScore > prevWordScore ? wordScore : prevWordScore);
			
			wordScoreMap.put(word, wordScore);
			
			priorWordIndex = wordIndex;
			//priorWord = word;
		}

		int wordScoreMapSz = wordScoreMap.size();
		
		if(1 == wordScoreMapSz
				//Note genericSearchTermsSet includes 5000 frequent English words.
				&& WordForms.genericSearchTermsSet().contains(wordScoreMap.keySet().iterator().next())) {
			//same as above. If only contain *one* generic word, count result as 0.
			//In these cases query must be more than one word, since 
			//else wouldn't be resorting to index search.
			return new LiteralSearchIndexPair(MIN_SCORE, lowestIndex);
		}
		
		int totalScore = 0; //was: WORD_BASE_POINT * wordScoreMapSz, base score will be added 
		//add points for the total number of words triggered
		for(int score : wordScoreMap.values()) {
			totalScore += score;
		}
		return new LiteralSearchIndexPair(totalScore, lowestIndex);
	}
	
	public static boolean spanBelowThreshold(int curSpan, int maxPossibleSpan) {
		//System.out.println("LiteralSearch - curSpan maxPossibleSpan: " + curSpan + "  " + maxPossibleSpan);
		if(2 == maxPossibleSpan) {
			return curSpan < 2;
		}
		if(maxPossibleSpan > 7) {
			return curSpan <= maxPossibleSpan * literalSearchTriggerThreshold2;
		}
		return curSpan <= maxPossibleSpan * literalSearchTriggerThreshold;
	}
	
	public static void main(String[] args) {
		//check out the "real fluff words"
		/*ListMultimap<String, LiteralSearchIndex> literalSearchIndexMap 
			= ArrayListMultimap.create();
		
		addThmLiteralSearchIndexToMap ("quadratic field extension", 1, 
				literalSearchIndexMap);***********/
		
		//g();
		//printIndexMap();
		//System.out.println(WordFrequency.ComputeFrequencyData.trueFluffWordsSet());
		//System.out.println(Arrays.toString(WordForms.splitThmIntoSearchWords("this r(8e3 se . 4")));
	}
	
	private static void printIndexMap() {
		
		FileUtils.writeToFile(literalSearchIndexMap.keySet(), "src/thmp/data/literalSearchIndexMapKeys.txt");
	}
	
	private static void serializeIndexMap() {
		ListMultimap<String, LiteralSearchIndex> mmap = ArrayListMultimap.create();
		LiteralSearchIndex searchIndex = new LiteralSearchIndex(4, new byte[] {1});
		mmap.put("hi", searchIndex);
		List<ListMultimap<String, LiteralSearchIndex>> list = new ArrayList<ListMultimap<String, LiteralSearchIndex>>();
		list.add(mmap);
		FileUtils.serializeObjToFile(list, "src/thmp/data/testIndexMap.dat");
		
	}
	
}
