package thmp.runner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.TreeMultiset;

import thmp.search.ThmHypPairGet;
import thmp.search.WordFrequency;
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
	private static final Set<String> SCRAPE_STOP_WORDS_BEFORE_SET = new HashSet<String>();
	
	static {
		String[] beforeStopWordsAR = new String[]{"by", "of","to","above","in", "By", "with", "is", "from",
				"following", "then", "thus", "this", "if", "and", "", "an", "are", "exists", "there", "that",
				"all", "for", "we", "have", "may", "over", "other", "on", "only", "be", "its", "the", "denote",
				"under"};
		//use list of most frequent English words, Modulo the false positives, e.g. map, group, 
		Map<String, Integer> freqWordsMap = WordFrequency.ComputeFrequencyData.englishStockFreqMap();
		SCRAPE_STOP_WORDS_BEFORE_SET.addAll(freqWordsMap.keySet());
		
		for(String w : beforeStopWordsAR) {
			SCRAPE_STOP_WORDS_BEFORE_SET.add(w);
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
				select4Grams(thmStr, nGramMSet, addNewTerms);				
			}
			
			Multiset<String> sortedMSet = TreeMultiset.create(new 
					thmp.utils.DataUtility.CountComparator<String>(nGramMSet));
			sortedMSet.addAll(nGramMSet);
			
			List<String> list = new ArrayList<String>(sortedMSet.elementSet());
			
			//list.sort(new Comp(nGramMSet));			
			//System.out.println("list: " + list);
			
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
	private static void select4Grams(String thm, Multiset<String> nGramMSet,
			boolean allowNewWords) {
		
		String thmLower = thm.toLowerCase();
		List<String> wordsList = Arrays.asList(THM_SCRAPE_PUNCTUATION_PATTERN.split(thmLower));
		
		//gather words, stop at punctuations, stop words, and latex.		
		int wordsListSz = wordsList.size();
		
		int index = 0;
		
		outerWhile: for(int startingIndex = 0; startingIndex < wordsListSz; startingIndex++) {
			
			StringBuilder sb = new StringBuilder(35);
			int wordCount = 0;
			
			index = startingIndex;
			while(wordCount < 4 ) {
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
				sb.append(word).append(" ");
				wordCount++;
				index++;
			}
			String word = sb.toString();
			if(!allowNewWords) {
				if(nGramMSet.contains(word)) {
					nGramMSet.add(word);
				}				
			}else {
				nGramMSet.add(word);
			}
		}
	}
	
}
