package thmp.utils;

import com.google.common.collect.ImmutableSet;

import thmp.ThmP1;
import thmp.search.CollectFreqWords;

public class WordForms {

	//delimiters to split on when making words out of input
	private static final String SPLIT_DELIM = "\\s+|\'|\\(|\\)|\\{|\\}|\\[|\\]|\\.|\\;|\\,|:|-|_|~";
	private static final ImmutableSet<String> freqWordsSet; 
	
	static{
		freqWordsSet = CollectFreqWords.GetFreqWords.get_nonMathFluffWordsSet2();
	}
	
	/**
	 * Returns the most likely singular form of the word, or
	 * original word if it doesn't end in s, es, or ies
	 * @param word
	 * @return
	 */
	public static String getSingularForm(String word){
		//if word in dictionary, should not be processed. Eg "continuous"
		if(freqWordsSet.contains(word)) return word;
		
		String[] singFormsAr = ThmP1.getSingularForms(word);
		//singFormsAr successively replaces words ending in "s", "es", "ies"
		int k = 2;
		while(k > -1 && singFormsAr[k] == null){
			k--;
		}
		if(k != -1){
			word = singFormsAr[k];
			//System.out.println("singular form: "+ word);
		}
		//if letter before "es" is not "s", eg "kisses", or "ch", eg "catches",
		//add e back, to preserve terms that end in "e", eg "agrees"

		String irregPluralEndings = "s|h|x";
		int wordLen = word.length();
		if(k==1 && wordLen > 1 && !String.valueOf(word.charAt(wordLen-1)).matches(irregPluralEndings)){
			word = word+"e";
		}
		return word;
	}
	
	/**
	 * Returns split delimiters.
	 * @return
	 */
	public static String splitDelim(){
		return SPLIT_DELIM;
	}
}
