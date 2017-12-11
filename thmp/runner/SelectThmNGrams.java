package thmp.runner;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.wolfram.puremath.dbapp.DBUtils;
import com.wolfram.puremath.dbapp.SimilarThmUtils;

import thmp.search.ThmHypPairGet;
import thmp.search.SearchCombined.ThmHypPair;
import thmp.utils.FileUtils;
import thmp.utils.WordForms;

/**
 * Selects n-grams from theorems.
 * @author yihed
 *
 */
public class SelectThmNGrams {

	//number of thms to serialize at a time
	private static final int numThmPerBundle = 5000;
		
	private static final Pattern THM_SCRAPE_PUNCTUATION_PATTERN = Pattern.compile("(?:(?=[,!:;.\\s])|(?<=[,!:;.\\s]))");
	private static final Pattern THM_SCRAPE_ELIM_PUNCTUATION_PATTERN = Pattern
			.compile("[\\{\\[\\)\\(\\}\\]$\\%/|#@.;,:_~!&\"`+<>=#]");
	private static final Set<String> SCRAPE_STOP_WORDS_BEFORE_SET = new HashSet<String>();
	
	static {
		String[] beforeStopWordsAR = new String[]{"by", "of","to","above","in", "By", "with", "is", "from",
				"following", "then", "thus", "this"};
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
		
		Multiset<String> nGramMSet = HashMultiset.create();
		
		//1/3 way through, stop adding new ones, for memory purposes, and only add count if already contained
		
		for(int j = 0; j < totalBundles; j++) {
			
			boolean addNewTerms = j < totalBundles/3;
			
			Map<Integer, byte[]> similarThmsMap = new HashMap<Integer, byte[]>();
			int endingIndex = Math.min(totalThmCount, (j+1)*numThmPerBundle);
			int startingIndex = j * numThmPerBundle;
			for(int i = startingIndex; i < endingIndex; i++) {	
				
				ThmHypPair thmHypPair = ThmHypPairGet.retrieveThmHypPairWithThm(i);
				String thmStr = thmHypPair.getEntireThmStr();
				select4Grams(thmStr, nGramMSet, addNewTerms);
				
				List<Integer> similarThmList = thmp.search.SimilarThmSearch.preComputeSimilarThm( i );
				similarThmsMap.put(i, SimilarThmUtils.indexListToByteArray(similarThmList));
				
				
			}
			
			
			String path = DBUtils.SimilarThmsTb.similarThmIndexByteArrayPathNoDat + j + ".dat" ;
			//make map instead of write to file.
			List<Map<Integer, byte[]>> similarThmsMapList = new ArrayList<Map<Integer, byte[]>>();
			similarThmsMapList.add(similarThmsMap);
			thmp.utils.FileUtils.serializeObjToFile(similarThmsMapList, path);
			System.out.println("Done serializing for bundle " + j);
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
		List<String> lineList = Arrays.asList(THM_SCRAPE_PUNCTUATION_PATTERN.split(thmLower));
		
		//gather words, stop at punctuations, stop words, and latex.
		Iterator<String> iter = lineList.iterator();
		
		outerWhile: while(iter.hasNext()) {
			
			StringBuilder sb = new StringBuilder(30);
			for(int j = 0; j < 4; j++) {
				if(!iter.hasNext()) {
					break outerWhile;
				}
				String word = iter.next();
				//note could have tex!
				if(word.length() < 2) {
					continue outerWhile;
				}
				if(WordForms.getWhiteEmptySpacePattern().matcher(word).matches()) {				
					continue outerWhile;
				}
				if(THM_SCRAPE_ELIM_PUNCTUATION_PATTERN.matcher(word).find()) {
					continue outerWhile;
				}				
				if(SCRAPE_STOP_WORDS_BEFORE_SET.contains(word)) {
					continue outerWhile;
				}
				sb.append(word).append(" ");
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
	/*
	private static void scrapeThmNames(String line, List<String> thmNameList) {
			
			List<String> lineList = Arrays.asList(THM_SCRAPE_PUNCTUATION_PATTERN.split(line));
			int lineListSz = lineList.size();
			for(int i = 0; i < lineListSz; i++) {
				String word = lineList.get(i);
				
				
				if(THM_SCRAPE_PATTERN.matcher(word).matches()) {
					String thmWords = collectThmWordsBeforeAfter(lineList, i);
					if(!WHITE_SPACE_PATTERN.matcher(thmWords).matches()) {
						thmNameList.add(thmWords);							
					}
				}
			}			
		}
	
	
	private static String collectThmWordsBeforeAfter(List<String> thmList, int index) {
		int indexBoundToCollect = 6;
		StringBuilder sb = new StringBuilder(40);
		int thmListSz = thmList.size();
		int i = 1;
		int count = 0;
		boolean gathered = false;
		while(count < indexBoundToCollect && index - i > -1) {
			String curWord = thmList.get(index-i);
			if(WordForms.getWhiteEmptySpacePattern().matcher(curWord).matches()) {
				i++;
				continue;
			}
			if(THM_SCRAPE_ELIM_PUNCTUATION_PATTERN.matcher(curWord).find()) {
				break;
			}
			if(THM_SCRAPE_ELIM_PATTERN.matcher(curWord).matches()) {
				return "";
			}
			if(count==0 && SCRAPE_STOP_WORDS_BEFORE_SET.contains(curWord)) {
				break;
			}
			sb.insert(0, curWord + " ");
			i++;
			count++;
			gathered = true;
		}
		sb.append(thmList.get(index)).append(" ");
		indexBoundToCollect = 7;
		i = 1;
		count = 0;
		while(count < indexBoundToCollect && index + i < thmListSz) {
			String curWord = thmList.get(index+i);
			if(WordForms.getWhiteEmptySpacePattern().matcher(curWord).matches()) {
				i++;
				continue;
			}	
			if(THM_SCRAPE_ELIM_PUNCTUATION_PATTERN.matcher(curWord).find()) {
				break;
			}
			if(count==0 && WordForms.DIGIT_PATTERN.matcher(curWord).find()) {
				break;
			}
			if(THM_SCRAPE_ELIM_PATTERN.matcher(curWord).matches()) {
				return "";
			}
			sb.append(curWord).append(" ");
			i++;
			count++;
			gathered = true;
		}
		if(sb.length() > 1) {
			sb.deleteCharAt(sb.length()-1);
		}
		if(gathered) {
			return sb.toString();
		}else {
			return "";
		}		
	}
	*/
	
}
