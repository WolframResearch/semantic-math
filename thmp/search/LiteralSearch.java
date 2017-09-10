package thmp.search;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.TreeMultimap;

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
	private static final ListMultimap<String, LiteralSearchIndex> literalSearchIndexMap;
	private static final int WORD_BASE_POINT = 1;
	//point when word is right next to another word
	private static final int WORD_DIST_1_POINT = 2;
	private static final int WORD_DIST_2_3_POINT = 1;
	/**threshold after which to perform literal word search*/
	private static final double literalSearchTriggerThreshold = 0.5;
	
	static {		
		//literalSearchIndexMap = deserializeIndexMap();
		literalSearchIndexMap = null;
	}
	
	/**
	 * Class for recording the thm index, and the  used in the theorem.
	 */
	public static class LiteralSearchIndex implements Serializable{
		
		private static final long serialVersionUID = -5527085309265213755L;
		
		/**thm index in total corpus*/
		private int thmIndex;
		/**list of word indices in thm*/
		private List<Number> wordIndexList;
		
		/**
		 * Input list must be sorted in increasing order.
		 * @param thmIndex_
		 * @param wordIndexList_ IllegalArgumentException is not sorted.
		 */
		public LiteralSearchIndex(int thmIndex_, List<Number> wordIndexList_) {

			//System.out.println("wordIndexList_.get(0).getClass() "+ (wordIndexList_.get(0).getClass()));
			
			int wordIndexListSz = wordIndexList_.size();
			if(wordIndexListSz > 1) {
				//System.out.println("wordIndexList_.get(0).getClass() "+ (wordIndexList_.get(0).getClass()));
				short prior = wordIndexList_.get(0).shortValue();
				for(int i = 1; i < wordIndexListSz; i++) {
					short current = wordIndexList_.get(i).shortValue();
					if(current < prior) {
						throw new IllegalArgumentException("Input word index list must be sorted in "
								+ "ascending order.");
					}
					prior = current;
				}
			}
			
			this.thmIndex = thmIndex_;			
			this.wordIndexList = wordIndexList_;
		}
		
		public int thmIndex() {
			return this.thmIndex;
		}
		
		public List<Number> wordIndexList() {
			return this.wordIndexList;
		}
	}
	
	
	/********Tools for building*******/

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
		//Set<String> thmWordsSet = new HashSet<String>();
		ListMultimap<String, Number> wordsIndexMMap = ArrayListMultimap.create();
		
		List<String> thmWordsList = WordForms.splitThmIntoSearchWords(thm.toLowerCase());
		int thmWordsListSz = thmWordsList.size();
		//space optimization
		int num = 0;
		Number counter;
		if(thmWordsListSz > 127) {
			short i = 0;
			counter = i;
		}else {
			byte i = 0;
			counter = i;
		}
		final byte increment = 1;
		
		while(num < thmWordsListSz) {
			String word = thmWordsList.get(num);
			if(trueFluffWordsSet.contains(word)) {
				num++;
				continue;
			}
			word = processLiteralSearchWord(word);
			wordsIndexMMap.put(word, counter);
			num++;
			//System.out.println("counter.getClass() "+counter.getClass());			
			
			if(thmWordsListSz > 127) {
				short base = counter.shortValue();
				counter = (short)(base + increment);
			}else {
				byte base = counter.byteValue();
				counter = (byte)(base + increment);
			}			
		}
		//System.out.println("LiteralSearch - done with while loop over thmWordsList!");

		for(String word : wordsIndexMMap.keys()) {
			//must wrap in ArrayList, since the List from .get() is a RandomAccessWrappedList, 
			//which is not serializable
			searchIndexMap.put(word, new LiteralSearchIndex(thmIndex, new ArrayList<Number>(wordsIndexMMap.get(word))));
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
	 * @param query
	 * @param maxThmCount the max number of thms that should be returned, optional param.
	 * @return
	 */
	public static List<Integer> literalSearch(String query, int...maxThmCountAr){
		
		List<String> queryWordList = WordForms.splitThmIntoSearchWords(query);
		//System.out.println("in literalSearch query words: "+ queryWordList);
		//list of thm indices, and the indices of words in the thm, along with the words.
		Map<Integer, TreeMap<Number, String>> thmIndexWordMap = new HashMap<Integer, TreeMap<Number, String>>();
		
		for(String word : queryWordList) {
			if(trueFluffWordsSet.contains(word)) {
				continue;
			}
			word = processLiteralSearchWord(word);
			
			List<LiteralSearchIndex> thmIndexList = literalSearchIndexMap.get(word);
			//System.out.println("LiteralSearch - thmIndexList "+thmIndexList);
			
			for(LiteralSearchIndex searchIndex : thmIndexList) {
				int thmIndex = searchIndex.thmIndex;
				TreeMap<Number, String> indexWordMap = new TreeMap<Number, String>();
				for(Number wordIndex : searchIndex.wordIndexList) {
					indexWordMap.put(wordIndex, word);
				}
				
				thmIndexWordMap.put(thmIndex, indexWordMap);
			}			
		}
		
		Map<Integer, Integer> thmScoreMap = createThmScoreMap(thmIndexWordMap);
		TreeMultimap<Integer, Integer> scoreThmMMap = TreeMultimap.create();
		for(Map.Entry<Integer, Integer> thmScoreEntry : thmScoreMap.entrySet()) {
			scoreThmMMap.put(thmScoreEntry.getValue(), thmScoreEntry.getKey());
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
	 * Creates thm score map based on distances between words in a thm.
	 * A word next to another earns 2 points, dist 1 or 2 apart 1 pt, else no point.
	 * Base point total is number of distinct words. 
	 * @param thmIndexWordMap Map where keys are thm index, and value are map of word index and word.
	 * @return
	 */
	private static Map<Integer, Integer> createThmScoreMap(Map<Integer, TreeMap<Number, String>> thmIndexWordMap) {
		
		Map<Integer, Integer> thmScoreMap = new HashMap<Integer, Integer>();		
		
		for(Map.Entry<Integer, TreeMap<Number,String>> thmIndexWordEntry : thmIndexWordMap.entrySet()) {
			int thmIndex = thmIndexWordEntry.getKey();
			thmScoreMap.put(thmIndex, computeThmScore(thmIndexWordEntry.getValue()));
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
			return WORD_BASE_POINT;
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
		
		int totalScore = WORD_BASE_POINT * wordScoreMap.size();
		//add points for the total number of words triggered
		for(int score : wordScoreMap.values()) {
			totalScore += score;
		}
		return totalScore;
	}
	
	public static boolean spanBelowThreshold(int curSpan, int maxPossibleSpan) {
		if(2 == maxPossibleSpan) {
			return curSpan < 2;
		}
		return curSpan <= maxPossibleSpan * literalSearchTriggerThreshold;
	}
	
	public static void main(String[] args) {
		//check out the "real fluff words"
		ListMultimap<String, LiteralSearchIndex> literalSearchIndexMap 
			= ArrayListMultimap.create();
		
		addThmLiteralSearchIndexToMap ("quadratic field extension", 1, 
				literalSearchIndexMap);
		
		//System.out.println(WordFrequency.ComputeFrequencyData.trueFluffWordsSet());
		//System.out.println(Arrays.toString(WordForms.splitThmIntoSearchWords("this r(8e3 se . 4")));
	}
	
}
