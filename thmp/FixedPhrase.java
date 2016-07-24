package thmp;

import java.util.regex.Pattern;

/**
 * A FixedPhrase instance represents a compound phrase
 * that definitively states something and serves as one 
 * part of speech.
 * Could incorporate scores in for later use during mx parsing. 
 * e.g. "for all but finitely many" should be grouped into nearby ent's
 * pos as adj.
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
	
	public FixedPhrase(String regex, String pos){
		//compile the regex into a Pattern.
		this.phrasePattern = Pattern.compile(regex);
		
		this.pos = pos;
		
		//determine the number of words to look down
		this.numWordsDown = regex.split(" ").length;		
		
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
	
}
