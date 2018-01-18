package thmp.runner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multiset;
import com.google.common.collect.TreeMultiset;

import thmp.search.ThmHypPairGet;
import thmp.search.WordFrequency;
import thmp.parse.Maps;
import thmp.search.SearchCombined.ThmHypPair;
import thmp.utils.WordForms;

/**
 * Selects n-grams from theorems. For e.g. building an entity store.
 * @author yihed
 *
 */
public class SelectThmNGrams {

	//number of thms to serialize at a time
	private static final int numThmPerBundle = 50000;
		
	private static final Pattern WHITE_SPACE_PATT = WordForms.getWhiteEmptySpacePattern();
	private static final Pattern THM_SCRAPE_PUNCTUATION_PATTERN = Pattern.compile("(?:(?=[,!:;.])|(?<=[,!:;.])|\\s+)");
	private static final Pattern THM_SCRAPE_ELIM_PUNCTUATION_PATTERN = Pattern
			.compile("[\\{\\[\\)\\(\\}\\]$\\%/|#@.;,:_~!&\"`+<>=#]");
	//private static final Pattern SLASH_PATTERN = Pattern.compile("\\\\");
	private static final Set<String> SCRAPE_STOP_WORDS_BEFORE_SET = new HashSet<String>();
	private static final Set<String> mostCommonWordSet;
	private static final Map<String, String> freqWordsPosMap = WordFrequency.ComputeFrequencyData.freqWordsPosMap();
	private static final ListMultimap<String, String> wordPosMMap = Maps.posMMap();
	
	static {
		String[] beforeStopWordsAr = new String[]{"by", "of", "to","above","in", "By", "with", "is", "from",
				"following", "then", "thus", "this", "if", "and", "any", "a", "an", "are", "exists", "there", "that",
				"all", "for", "we", "have", "has", "may", "over", "other", "on", "only", "be", "its", "the", "denote",
				"under", "et"};
		//use list of most frequent English words, Modulo the false positives, e.g. map, group, 
		Map<String, Integer> freqWordsMap = WordFrequency.ComputeFrequencyData.englishStockFreqMap();
		
		SCRAPE_STOP_WORDS_BEFORE_SET.addAll(freqWordsMap.keySet());
		
		mostCommonWordSet = new HashSet<String>();
		
		for(String w : beforeStopWordsAr) {
			SCRAPE_STOP_WORDS_BEFORE_SET.add(w);
			mostCommonWordSet.add(w);
		}	
	}
	
	public static void main(String[] args) {		
		selectNGrams();
	}
	
	/**
	 * Combines different serialized files, and serializes them.
	 */
	private static void selectNGrams() {
		
		int totalThmCount = ThmHypPairGet.totalThmsCount();
		int totalBundles = (int)Math.ceil(((double)totalThmCount) / numThmPerBundle);
		//int totalThmCount = 1;
		totalBundles = 1;
		
		String rootPath = "src/thmp/data/nGramScrape";
		//1/3 way through, stop adding new ones, for memory purposes, and only add count if already contained
		int collectCap = totalBundles/3;
		collectCap = collectCap == 0 ? 1 : collectCap;
		
		for(int j = 0; j < totalBundles; j++) {
			
			boolean addNewTerms = j < collectCap;
			addNewTerms = true;
			Multiset<String> nGramMSet = HashMultiset.create();
			
			int endingIndex = Math.min(totalThmCount, (j+1)*numThmPerBundle);
			int startingIndex = j * numThmPerBundle;
			
			for(int i = startingIndex; i < endingIndex; i++) {
				
				ThmHypPair thmHypPair = ThmHypPairGet.retrieveThmHypPairWithThm(i);
				String thmStr = thmHypPair.getEntireThmStr();
				//String thmStr = "hi there hdsh wt bunny sf gsg sgrgd dhg sf gsg sgrgd dhg";
				//String thmStr = "hi there hds khl, jkk$ agsg hi there hds khl. hjkh kh hk gh";
				selectNGrams(thmStr, nGramMSet, addNewTerms, 4);				
			}
			
			Multiset<String> sortedMSet = TreeMultiset.create(new 
					thmp.utils.DataUtility.CountComparator<String>(nGramMSet));
			sortedMSet.addAll(nGramMSet);
			
			List<String> list = new ArrayList<String>(sortedMSet.elementSet());
			
			//make map instead of write to file.
			List<List<String>> similarThmsMapList = new ArrayList<List<String>>();
			similarThmsMapList.add(list);
			String path = rootPath + j + ".dat";
			thmp.utils.FileUtils.serializeObjToFile(similarThmsMapList, path);
			String txtPath = rootPath + j + ".txt";
			
			thmp.utils.FileUtils.writeToFile(sortedMSet.entrySet(), txtPath);
			
		}		
	}
	
	/**
	 * account for latex. Select 4 grams given a thm.
	 * @param thm
	 * @param nGramMSet set of n grams already gathered
	 */
	private static void selectNGrams(String thm, Multiset<String> nGramMSet,
			boolean allowNewWords, int nGramN) {
		
		String thmLower = thm.toLowerCase();
		
		List<String> wordsList = Arrays.asList(THM_SCRAPE_PUNCTUATION_PATTERN.split(thmLower));
		
		//gather words, stop at punctuations, stop words, and latex.		
		int wordsListSz = wordsList.size();
		
		int index = 0;
		
		outerWhile: for(int startingIndex = 0; startingIndex < wordsListSz; startingIndex++) {
			
			StringBuilder sb = new StringBuilder(35);
			int wordCount = 0;
			List<String> nGramList = new ArrayList<String>();
			
			index = startingIndex;
			while(wordCount < nGramN ) {
				if(index >= wordsListSz) {
					break outerWhile;
				}
				
				String word = wordsList.get(index);
				if(WHITE_SPACE_PATT.matcher(word).matches()) {	
					index++;
					continue;
				}
				//one way to skip tex
				if(word.length() < 2) {
					continue outerWhile;
				}
				
				if(THM_SCRAPE_ELIM_PUNCTUATION_PATTERN.matcher(word).find()) {
					continue outerWhile;
				}				
				if(SCRAPE_STOP_WORDS_BEFORE_SET.contains(word)) {
					continue outerWhile;
				}
				
				word = word.trim();
				sb.append(word).append(" ");
				nGramList.add(word);
				wordCount++;
				index++;
			}
			
			//sb = sb.subSequence(0, sb.length()-1);
			String word = sb.substring(0, sb.length()-1);
			
			if(word.contains("\\")) {
				continue outerWhile;
			}
			
			//check first and last words, make sure they are not verbs, to eliminate
			//e.g. infinite dimensional simplex spanned, or von neumann algebra acting.
			String firstWord = nGramList.get(0);
			if(!ifValidNGramWord(firstWord)) {
				continue outerWhile;
			}
			
			if(nGramN > 0) {
				String lastWord = nGramList.get(nGramN-1);
				if(!ifValidNGramWord(lastWord)) {
					continue outerWhile;
				}
			}
			
			//e.g. "... tilings"
			word = WordForms.getSingularForm(word);
			
			if(!allowNewWords) {
				if(nGramMSet.contains(word)) {
					nGramMSet.add(word);
				}				
			}else {
				nGramMSet.add(word);
			}
		}
	}
	
	/**
	 * Determines if word is a valid n gram word. Eliminate
	 * e.g. infinite dimensional simplex spanned, or "von neumann algebra acting",
	 * but not "cyclically symmetric lozenge tilings", "preserves projectively trivial complexes"
	 * @param word Already checked to be not in frequent list.
	 * @return
	 */
	private static boolean ifValidNGramWord(String word) {
		
		if(mostCommonWordSet.contains(word)) {
			return false;
		}
		
		String gerundForm = WordForms.getGerundForm(word);
		if(SCRAPE_STOP_WORDS_BEFORE_SET.contains(gerundForm)) {
			String wordPos = freqWordsPosMap.get(gerundForm);
			//check with e added as well, e.g. commute vs commuting
			if("verb".equals(wordPos) || "verb".equals(freqWordsPosMap.get(gerundForm + "e"))) {
				return false;				
			}
			
			if(wordPosMMap.get(gerundForm).contains("verb") || wordPosMMap.get(gerundForm+"e").contains("verb")) {
				return false;				
			}			
		}
		
		int wordLen = word.length();
		if(wordLen > 4) {
			String wordEnding = word.substring(wordLen-2, wordLen);
			String wordStem = word.substring(0, wordLen-2);
					
			if(wordEnding.endsWith("ed") && ("verb".equals(freqWordsPosMap.get(wordStem)) || 
					"verb".equals(freqWordsPosMap.get(wordStem+"e")) ) ) {				
				return false;
			}	
			//e.g. described, etc
			if(wordPosMMap.get(wordStem).contains("verb") || wordPosMMap.get(wordStem+"e").contains("verb")) {
				return false;				
			}
		}
		
		String singularForm = WordForms.getSingularForm(word);
		if(SCRAPE_STOP_WORDS_BEFORE_SET.contains(singularForm)) {
			String wordPos = freqWordsPosMap.get(singularForm);
			if("verb".equals(wordPos)) {
				return false;				
			}
			if(wordPosMMap.get(singularForm).contains("verb") || wordPosMMap.get(singularForm+"e").contains("verb")) {
				return false;				
			}
		}
		
		return true;
	}
	
}
