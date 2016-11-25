package thmp;

import java.util.regex.Pattern;

/**
 * Used to detect hypotheses in a sentence.
 * @author yihed
 *
 */
public class DetectHypothesis {
	
	//pattern matching is faster than calling str.contains() repeatedly 
	//which is O(mn) time.
	private static final Pattern HYP_PATTERN = Pattern.compile(".*assume.*|.*denote.*|.*define.*") ;
	
	/**
	 * Whether the inputStr is a hypothesis. By checking whether the input 
	 * contains any assumption-indicating words.
	 * @param inputStr
	 * @return
	 */
	public static boolean isHypothesis(String inputStr){
		if(HYP_PATTERN.matcher(inputStr).find()){
			return true;
		}
		return false;
	}
	
	public static void main(String[] args){
		//only parse if sentence is hypothesis, when outside theorems
		//to build up variableNamesMMap. Should also collect the sentence that 
		//defines a variable, to include inside the theorem for search.
		
		
		//how to best detect substring?! for latex symbol matching
		
		
	}
	
}
