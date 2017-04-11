package thmp;

import java.util.Arrays;

import thmp.exceptions.ParseRuntimeException.IllegalSyntaxException;
import thmp.utils.WordForms;

/**
 * Utility class for parsing tex, such as 
 * \begin{enumerate} ...
 * 
 *
 */
public class TexParseUtils {

	//special command, \\Equiv[A, B, ...]
	/**
	 * Calls tokenize and parse to parse each indidivual 
	 * \item of \enumerate, then group them together as one 
	 * command.
	 * @param inputAr String[] input, space-delimited, should begin with \begin{enumerate}
	 * and \item's. Should also end in \end{enumerate}, as built in preprocess().
	 */
	public static void parseEnumerate(String[] inputAr, ParseState parseState){
		System.out.println(Arrays.toString(inputAr));
		
		StringBuilder itemSb = new StringBuilder();
		//coarse splitting, since only need to sieve out \item, etc.
		//and need to put string back together.
		//String[] inputAr = input.split("\\s+");
		String enumerateStr = "\\begin{enumerate}";
		
		int inputArLen = inputAr.length;
		assert inputArLen > 0;
		int index = 0;
		
		//look for \begin{enumerate}
		while(index < inputArLen && !inputAr[index].equals(enumerateStr) ){
			index++;			
		}		
		
		if(index == inputArLen) return;
		
		String curWord;
		index++;
		while(index < inputArLen){
			
			curWord = inputAr[index];
			if(curWord.equals("\\item")){
				if(itemSb.length() > 0){
					parseItem(itemSb.toString(), parseState);
					itemSb.setLength(0);
				}
			}else if(curWord.equals("\\end{enumerate}")){				
				return;
			}
			else{
				itemSb.append(" " + curWord);
			}			
			index++;
		}		
	}
	
	/**
	 * Parses one \item. 
	 * @param itemStr
	 */
	private static void parseItem(String itemStr, ParseState parseState){
		
		String[] itemStrAr = ThmP1.preprocess(itemStr);
		
		for(String sentence : itemStrAr){
			try {
				parseState = ThmP1.tokenize(sentence.trim(), parseState);
			} catch (IllegalSyntaxException e) {
				e.printStackTrace();
			}
			parseState = ThmP1.parse(parseState);
		}
	}
}
