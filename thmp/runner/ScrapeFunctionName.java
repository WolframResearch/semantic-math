package thmp.runner;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import thmp.utils.WordForms;


/**
 * Class containing methods for scraping function names from papers.
 * E.g. "the ... polynomial/function".
 * @author yihed
 *
 */
public class ScrapeFunctionName {

	//punctuation pattern to eliminate
		//almost all special patterns but no -, ', ^, *, which can occur in thm names
		private static final Pattern THM_SCRAPE_ELIM_PUNCTUATION_PATTERN = Pattern
				.compile("[\\{\\[\\)\\(\\}\\]$\\%/|#@.;,:_~!&\"`+<>=#]");
		//used for thm scraping.
		private static final Pattern THM_SCRAPE_PATTERN = Pattern.compile("(?i)(?:theorem|lemma|conjecture)");
		
		//"the " <> (one or two words, potentially with hyphen) <> " polynomials"
		private static final Pattern FUNCTION_SCRAPE_PATTERN = 
				Pattern.compile("((?:the)[^\\[\\{\\(\\]\\)\\}\\%/|@.;,:_~!&\"`+<>=#]{4,28}(?:function|polynomial|number))");
		
		//revise this!!
		private static final Pattern THM_SCRAPE_PUNCTUATION_PATTERN = Pattern.compile("(?:(?=[,!:;.\\s])|(?<=[,!:;.\\s]))");
		//stop words that come after the stirng "theorem", to stop scraping before, the word immediately before.
		private static final Set<String> SCRAPE_STOP_WORDS_BEFORE_SET = new HashSet<String>();
		private static final Pattern THM_SCRAPE_ELIM_PATTERN = Pattern.compile("(?:from|proof|prove[sd]*|next)");
		private static final Pattern WHITE_SPACE_PATTERN = Pattern.compile("\\s*");
		
		/*
		 * "the " <> (one or two words, potentially with hyphen) <> " function"
			lets also search for
			"the " <> (one or two words, potentially with hyphen) <> " polynomials"
			"the " <> (one or two words, potentially with hyphen) <> " numbers"
		 */
		
		/**
		 * Add function names scraped from line to thmNameList.
		 * *Only* used for function name scraping.
		 * @param line line to process from.
		 * @param thmNameMSet
		 */
		public static void scrapeThmNames(String str, StringBuilder sb) {
			
			Matcher m = FUNCTION_SCRAPE_PATTERN.matcher(str);
			while(m.find()) {
				sb.append(m.group(1)).append("\n");				
			}			
		}
		
		/**
		 * 
		 * @param thmList
		 * @param index
		 * @return
		 */
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
	
	
}
