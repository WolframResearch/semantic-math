package thmp.search;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multiset;
import com.wolfram.puremath.dbapp.ConceptSearchUtils;
import com.wolfram.puremath.dbapp.SimilarThmUtils;

import thmp.search.CollectThm.ThmWordsMaps.IndexPartPair;
import thmp.utils.DBUtils;
import thmp.utils.WordForms;

/**
 * Search based on literally matching concepts words. Arising
 * from user clicking on concepts from web FE.
 * @author yihed
 */
public class ConceptSearch {

	private static final Logger logger = LogManager.getLogger(ConceptSearch.class);
	private static final Map<String, Integer> keywordsIndexMap 
		= CollectThm.ThmWordsMaps.get_CONTEXT_VEC_WORDS_INDEX_MAP();
	private static final List<String> keywordsList = CollectThm.ThmWordsMaps.get_CONTEXT_VEC_WORDS_LIST();
	private static final ImmutableMultimap<String, IndexPartPair> wordThmsIndexMMap1
		= CollectThm.ThmWordsMaps.get_wordThmsMMapNoAnno();;
	private static final int NUM_NEAREST_VECS = SearchCombined.NUM_NEAREST;
	
	private static final int NUM_BITS_PER_WORD_INDEX;
	private static final int maxWordsIndexListLen = Searcher.SearchMetaData.maxWordsIndexListLen;
	
	private static final int NO_OP = -1;
	
	static {
		//just shift!
		NUM_BITS_PER_WORD_INDEX = (int)Math.ceil(Math.log(keywordsIndexMap.size())/Math.log(2));
	}
	
	public static List<Integer> getStrictNormalizedWordsThms(String[] keyWordsAr) {
		List<String> wordsList = new ArrayList<String>();
		return getStrictNormalizedWordsThms(wordsList);
	}
	
	/**
	 * Get thms that strictly contain *all* words in keyWordsList.
	 * @param keyWordsList List of keywords, *must* already be normalized, i.e. in form 
	 * that is used in maps.
	 * @return List of thms that contain all words
	 */
	public static List<Integer> getStrictNormalizedWordsThms(List<String> keyWordsList) {
		
		Multiset<IndexPartPair> allWordsThms = HashMultiset.create();
		if(keyWordsList.isEmpty()) {
			return Collections.<Integer>emptyList();
		}
		
		int keyWordsListSz = keyWordsList.size();
		for(String word : keyWordsList) {
			Collection<IndexPartPair> wordThms = wordThmsIndexMMap1.get(word);
			allWordsThms.addAll(wordThms);
		}
		//multiset to favor results that show up in thm statements
		Multiset<Integer> indexPartMSet = HashMultiset.create();
		List<Integer> selectedThmsList = new ArrayList<Integer>();
		int thmCount = 0;
		
		for(Multiset.Entry<IndexPartPair> entry : allWordsThms.entrySet()) {
			
			int count = entry.getCount();
			if(count < keyWordsListSz) {
				continue;
			}
			IndexPartPair pair = entry.getElement();
			int thmIndex = pair.thmIndex();
			if(!pair.isContextPart()) {
				indexPartMSet.add(thmIndex);
			}
			selectedThmsList.add(thmIndex);
			if(++thmCount > NUM_NEAREST_VECS) {
				break;
			}
		}
		
		//sort to have 
		selectedThmsList.sort(new thmp.utils.DataUtility.CountComparator<Integer>(indexPartMSet));
		
		return selectedThmsList;
	}
	
	
	/**
	 * Finds index list of concepts in a thm by retrieving precomputed
	 * indices from database. Used at app runtime.
	 * @param thmIndex
	 * @return List of concepts in given thm.
	 */
	public static List<String> getThmConceptsList(int thmIndex){
		
		Connection conn = DBUtils.getPooledConnection();
		if(null == conn) {
			return Collections.emptyList();
		}
		
		List<Integer> indexList;
		
		try {
			indexList = ConceptSearchUtils.getThmConceptsFromDB(thmIndex, conn);
		}catch(SQLException e) {
			logger.error("SQLException while getting thm concepts! " + e);
			return Collections.emptyList();
		}finally {
			DBUtils.closePooledConnection(conn);
		}
		
		List<String> wordsList = new ArrayList<String>();
		for(int i : indexList) {
			wordsList.add(keywordsList.get(i));
		}
		return wordsList;
	}
	
	/**
	 * Finds all normalized key words in a given thm String, encode indices of these words into byte array,
	 * return map of thm indices and byte array. Used at data creation and processing time, *not* app run time.
	 * @param thmStr
	 * @return
	 */
	public static byte[] getThmWordsIndexByteArray(String thmStr) {
		
		List<String> wordsList = WordForms.splitThmIntoSearchWordsList(thmStr);
		int wordsListSz = wordsList.size();
		List<Integer> wordIndexList = new ArrayList<Integer>();
		
		for (int i = 0; i < wordsListSz; i++) {
			
			//long time0 = System.nanoTime();
			String word = wordsList.get(i);
			
			if (i < wordsListSz - 1) {
				String nextWord = wordsList.get(i+1);
				String twoGram = word + " " + nextWord;
				twoGram = WordForms.normalizeTwoGram(twoGram);
				// check for 3 grams.
				//long time1 = SimilarThmSearch.printElapsedTime(time0, "time1");
				
				if (i < wordsListSz - 2) {
					String thirdWord = wordsList.get(i+2);
					String threeGram = twoGram + " " + thirdWord;
					int wordIndex = getNormalizedWordIndex(threeGram);
					if(wordIndex != NO_OP ) {
						wordIndexList.add(wordIndex);
					}
				}
				int wordIndex = getNormalizedWordIndex(twoGram);
				if(wordIndex != NO_OP ) {
					wordIndexList.add(wordIndex);
				}
			}
			int wordIndex = getNormalizedWordIndex(word);
			if(wordIndex != NO_OP) {
				wordIndexList.add(wordIndex);
			}
			if(wordIndexList.size() >= maxWordsIndexListLen) {
				break;
			}
		}
		
		return SimilarThmUtils.wordsListToByteArray(wordIndexList, NUM_BITS_PER_WORD_INDEX,
				maxWordsIndexListLen) ;
	}
	

	
	/**
	 * Normalizes (singularize, trim endings, etc) word, and get 
	 * the word's index. 
	 * @param word
	 * @return normalized word index. NO_OP if none present.
	 */
	private static int getNormalizedWordIndex(String word) {
		
		Integer wordIndex = keywordsIndexMap.get(word);
		if(null != wordIndex) {
			return wordIndex;
		} 
		String wordSingForm = WordForms.getSingularForm(word);
			
		wordIndex = keywordsIndexMap.get(wordSingForm);			
		if (null != wordIndex) {
				return wordIndex;		
		}
		
		String normalizedWord = WordForms.normalizeWordForm(word);
		wordIndex = keywordsIndexMap.get(normalizedWord);		
		if (null != wordIndex) {
			return wordIndex;		
		}
		
		normalizedWord = WordForms.normalizeWordForm(wordSingForm);
		wordIndex = keywordsIndexMap.get(normalizedWord);		
		if (null != wordIndex) {
			return wordIndex;		
		}
		
		return NO_OP;
	}
	
}
