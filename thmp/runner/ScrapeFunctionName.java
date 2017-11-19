package thmp.runner;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Class containing methods for scraping function names from papers.
 * E.g. "the ... polynomial/function".
 * Run cat * /* /funcNames.txt > funcNameCombined.txt to gather all files.
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
				Pattern.compile("(?:the) ([^\\[\\{\\(\\]\\)\\}\\%/|@.;,:_~!&\"`+<>=#]{4,23} (?:function|polynomial|number))");
		private static final Pattern NON_FUNCTION_PATTERN = Pattern.compile(".* (?:the|in|of|as|on|is) .*");
		
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
			
			String word;
			Matcher m = FUNCTION_SCRAPE_PATTERN.matcher(str);
			while(m.find()) {
				word = m.group(1);
				Matcher m2 = NON_FUNCTION_PATTERN.matcher(word);
				if(!m2.matches()) {
					sb.append(word).append("\n");
				}								
			}			
		}
		
	
}
