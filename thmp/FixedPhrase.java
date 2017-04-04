package thmp;

import java.util.regex.Pattern;

/**
 * A FixedPhrase instance represents a compound phrase
 * that definitively states something and serves as one 
 * part of speech.
 * Could incorporate scores in for later use during mx parsing. 
 * e.g. "all but finitely many" should be grouped into nearby ent's
 * pos as adj.
 * Should remain immutable, e.g. no setters.
 * @author yihed
 *
 */
public class FixedPhrase {

	/** number of words down the sentence to apply the regex match.
	 * Equal to the length of the pattern.
	 */
	private int numWordsDown;
	//regex to match, potentially different spellings,
	//like phrases with or without "the".
	private Pattern phrasePattern;
	//part of speech
	private String pos;
	
	public FixedPhrase(String regexStr, String pos){
		//compile the regex into a Pattern.
		this.phrasePattern = Pattern.compile(regexStr);		
		this.pos = pos;		
		//determine the number of words to look down
		this.numWordsDown = regexStr.split(" ").length;		
	}
	
	public String pos(){
		return this.pos;
	}
	
	public int numWordsDown(){
		return this.numWordsDown;
	}
	
	public Pattern phrasePattern(){
		return this.phrasePattern;
	}
	
	public String toString(){
		return this.phrasePattern.toString();
	}
}
