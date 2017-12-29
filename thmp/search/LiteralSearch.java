package thmp.search;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
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
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import com.wolfram.puremath.dbapp.DBUtils;
import com.wolfram.puremath.dbapp.LiteralSearchUtils;

import thmp.search.LiteralSearch.LiteralSearchIndex;
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
	private static final int LITERAL_WORD_LEN_MAX = 15;
	private static final Pattern DIGIT_PATTERN = Pattern.compile("\\d+");
	private static final Logger logger = LogManager.getLogger(LiteralSearch.class);
	/*deliberately includes slash \\*/
	private static final Pattern LITERAL_SPECIAL_CHARS_PATTERN 
		= Pattern.compile("[-\\{\\[\\)\\(\\}\\]\\\\$%/|?@*.;,:_~!+^&\"\'`+<>=#]");
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
		/*Max length allowed for wordIndexAr*/
		public static final byte MAX_WORD_INDEX_AR_LEN = 2;
		
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
	
	/********Tools for building literal search index map*******/

	/**
	 * Tokenizes thm into words, create LiteralSearchIndex's, and add to supplied
	 * index map.
	 * @param thm
	 * @param thmIndex
	 * @param searchIndexMap
	 */
	public static void addThmLiteralSearchIndexToMap (String thm, int thmIndex, 
			ListMultimap<String, LiteralSearchIndex> searchIndexMap) {
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
			
			if(isInValidLiteralWord(word)) {
				continue;
			}
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
		
		for(String word : wordsIndexMMap.keys()) {
			//must wrap in ArrayList, since the List from .get() is a RandomAccessWrappedList, 
			//which is not serializable
			List<Byte> wordIndexList = wordsIndexMMap.get(word);
			int wordIndexListSz = wordIndexList.size();
			if(LiteralSearchIndex.MAX_WORD_INDEX_AR_LEN < wordIndexListSz) {
				wordIndexListSz = LiteralSearchIndex.MAX_WORD_INDEX_AR_LEN;
			}
			byte[] wordIndexAr = new byte[wordIndexListSz];
			
			for(int i = 0; i < wordIndexListSz; i++) {
				wordIndexAr[i] = wordIndexList.get(i).byteValue();
			}
			searchIndexMap.put(word, new LiteralSearchIndex(thmIndex, wordIndexAr));
		}		
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
	 * @param maxThmCount the max number of thms that should be returned, optional param.
	 * @return
	 * List from literal search. Empty list if literal search doesn't improve word span,
	 * for e.g. "klfjk module"
	 */
	public static List<Integer> literalSearch(String query, int priorWordSpan, Set<String> searchWordsSet, 
			//need to take prior found word thms from intersection search into account!!
			
			int...maxThmCountAr){
		
		List<String> queryWordList = WordForms.splitThmIntoSearchWordsList(query);
		//System.out.println("in literalSearch query words: "+ queryWordList);
		//list of thm indices, and the indices of words in the thm, along with the words.
		Map<Integer, TreeMap<Number, String>> thmIndexWordMap = new HashMap<Integer, TreeMap<Number, String>>();
		int wordSpan = 0;
		Connection conn = thmp.utils.DBUtils.getPooledConnection();
		int wordIndexArLen = LiteralSearchIndex.MAX_WORD_INDEX_AR_LEN;
		
		for(String word : queryWordList) {
			if(isInValidLiteralWord(word)) {
				continue;
			}
			word = processLiteralSearchWord(word);
			searchWordsSet.add(word);
			
			//list of thm indices for given word. Request indices from db!!
			//List<LiteralSearchIndex> thmIndexList = literalSearchIndexMap.get(word);
			
			//getLiteralSearchThmsFromDB String word,  Connection conn,
			//List<Integer> thmIndexList, List<Integer> wordsIndexArList
			List<Integer> thmIndexList = new ArrayList<Integer>();
			List<Integer> wordsIndexArList = new ArrayList<Integer>();
			try {
				LiteralSearchUtils.getLiteralSearchThmsFromDB(word, conn, thmIndexList, wordsIndexArList);
			}catch(SQLException e) {
				logger.error("SQLException when getting literal search data!" + e);
				continue;
			}
			int thmIndexListSz = thmIndexList.size();
			//if empty, check list from intersection search!!
			
			int wordsIndexArListSz = wordsIndexArList.size();
			if((thmIndexListSz * wordIndexArLen) != wordsIndexArListSz) {
				throw new IllegalArgumentException("Literal search db table inconsistency: "
						+ "(thmIndexListSz * maxWordIndexArLen) != wordsIndexArListSz");
			}
			
			for(int i = 0; i < thmIndexListSz; i++) {
				
				int thmIndex = thmIndexList.get(i);
				//map of index in thm, and word at that index.
				TreeMap<Number, String> indexWordMap = thmIndexWordMap.get(thmIndex);
				
				indexWordMap = null == indexWordMap ? new TreeMap<Number, String>() : indexWordMap;
				
				int endIndex = (i+1) * wordIndexArLen;
				for(int j = i * wordIndexArLen; j < endIndex; j++) {			
					int wordIndexInThm = wordsIndexArList.get(j);
					if(wordIndexInThm == LiteralSearchIndex.PLACEHOLDER_INDEX) {
						break;
					}
					indexWordMap.put(wordIndexInThm, word);
				}				
				thmIndexWordMap.put(thmIndex, indexWordMap);
			}	
			if(!thmIndexList.isEmpty()) {
				wordSpan++;
			}
		}
		//need upper bound, since some results for long queries are still meaningful despite low span.
		final int wordSpanUpperBound = 3;
		if(wordSpan <= priorWordSpan && wordSpan < wordSpanUpperBound) {
			return Collections.emptyList();
		}
		Map<Integer, Integer> thmScoreMap = createThmScoreMap(thmIndexWordMap);
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

	private static String processLiteralSearchWord(String word) {
		word = WordForms.getSingularForm(word);
		word = WordForms.normalizeWordForm(word);
		return word;
	}
	
	/**
	 * Determines if word should be added as valid literal search word,
	 * non-valid words include those containing special chars, $, \, etc.
	 * Need to screen out placeholder char, '\uFFFD', e.g. in "term�"
	 * @param word Already singularized and normalized.
	 * @return
	 */
	private static boolean isInValidLiteralWord (String word) {
		if(LITERAL_SPECIAL_CHARS_PATTERN.matcher(word).find()
				|| DIGIT_PATTERN.matcher(word).find()){
			return true;
		}
		if(trueFluffWordsSet.contains(word) || INVALID_SEARCH_WORD_SET.contains(word)
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
	 * @return
	 */
	private static Map<Integer, Integer> createThmScoreMap(Map<Integer, TreeMap<Number, String>> thmIndexWordMMap) {
		
		Map<Integer, Integer> thmScoreMap = new HashMap<Integer, Integer>();		

		for(Map.Entry<Integer, TreeMap<Number,String>> thmIndexWordEntry : thmIndexWordMMap.entrySet()) {
			int thmIndex = thmIndexWordEntry.getKey();
			//Integer priorScore = thmScoreMap.get(thmIndex);
			//priorScore = null == priorScore ? 0 : priorScore;
			int thmScore = computeThmScore(thmIndexWordEntry.getValue());
			if(thmScore > MIN_SCORE) {
				thmScoreMap.put(thmIndex, thmScore);				
			}
		}
		
		return thmScoreMap;
	}

	/**
	 * Computes the score for a thm based on the ordering of the words in thm.
	 * @param indexWordMap
	 * @return
	 */
	private static int computeThmScore(TreeMap<Number,String> indexWordMap) {
		//map of words, and the number of points that word is earning
		Map<String, Integer> wordScoreMap = new HashMap<String, Integer>();
		
		List<Number> wordsList = new ArrayList<Number>(indexWordMap.keySet());
		int wordsListSz = wordsList.size();
		if(1 == wordsListSz) {
			if(WordForms.genericSearchTermsSet().contains(indexWordMap.get(wordsList.get(0))) ) {
				//if only contain *one* generic word, count result as 0.
				//In these cases query must be more than one word, since 
				//else wouldn't be resorting to index search.	
				return MIN_SCORE;
			}else {
				return WORD_BASE_POINT;				
			}
		}
			
		Number priorWordIndex = wordsList.get(0);
		String priorWord = indexWordMap.get(priorWordIndex);
		wordScoreMap.put(priorWord, 0);
		
		//duplicate words count once to their nearest word,
		//but can serve to be near multiple words.
		for(int i = 1; i < wordsListSz; i++) {
			Number wordIndex = wordsList.get(i);
			String word = indexWordMap.get(wordIndex);
			
			int distToPriorWord = wordIndex.intValue() - priorWordIndex.intValue();
			int wordScore;
			switch(distToPriorWord) {
			case 0:
				wordScore = WORD_DIST_1_POINT;
				break;
			case 1:
			case 2:
				wordScore = WORD_DIST_2_3_POINT;
				break;
			default:
				wordScore = 0;				
			}
			//wordScoreMap must already have priorWord
			if(wordScoreMap.get(priorWord) < wordScore) {
				wordScoreMap.put(priorWord, wordScore);
			}
			wordScoreMap.put(word, wordScore);
			
			priorWordIndex = wordIndex;
			priorWord = word;
		}

		int wordScoreMapSz = wordScoreMap.size();
		
		if(1 == wordScoreMapSz
				&& WordForms.genericSearchTermsSet().contains(wordScoreMap.keySet().iterator().next())) {
			//same as above. If only contain *one* generic word, count result as 0.
			//In these cases query must be more than one word, since 
			//else wouldn't be resorting to index search.
			return MIN_SCORE;
		}

		//fist count base score
		int totalScore = WORD_BASE_POINT * wordScoreMapSz;
		//add points for the total number of words triggered
		for(int score : wordScoreMap.values()) {
			totalScore += score;
		}
		return totalScore;
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
