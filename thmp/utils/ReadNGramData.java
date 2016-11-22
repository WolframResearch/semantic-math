package thmp.utils;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Read in scraped n-grams from file, sort words into 
 * various files according to whether they are singleton,
 * or n-grams.
 * 
 * @author yihed
 */
public class ReadNGramData {

	private static final String TWO_GRAM_DATA_FILESTR = "src/thmp/data/twoGramData.txt";
	private static final String THREE_GRAM_DATA_FILESTR = "src/thmp/data/threeGramData.txt";
	//name of file containing additional n-grams and words scraped from the web.
	private static final String NGRAM_DATA_FILESTR = "src/thmp/data/NGramData2.txt";
	private static final Pattern SEPARATOR_PATTERN = Pattern.compile("\\s+|-|–");
	private static final Pattern COMMA_PATTERN = Pattern.compile("(.+), (.+)");
	private static final Pattern END_SLASH_PATTERN = Pattern.compile("(.+)\\s+([^-\\s]+)-$");
	
	public static void main(String[] args){
		
		List<String> twoGramsList = new ArrayList<String>();
		List<String> threeGramsList = new ArrayList<String>();
		
		BufferedReader fileBufferedReader;
		try{
			fileBufferedReader = new BufferedReader(new FileReader(NGRAM_DATA_FILESTR));
		}catch(FileNotFoundException e){
			e.printStackTrace();
			return;
		}
		String word;
		try{
			while((word = fileBufferedReader.readLine()) != null){
				//pre-process, e.g. turn "Lie algebra, algebraic" into
				//"algebraic Lie algebra", and "adic l-" into "l-adic"
				word = preprocessWord(word);
				
				Matcher matcher = SEPARATOR_PATTERN.matcher(word);
				word = matcher.replaceAll(" ").toLowerCase();
				//determine if 2 or 3 gram
				int wordLen = word.split(" ").length;
				if(wordLen == 2){
					twoGramsList.add(word);					
				}else if(wordLen == 3){
					threeGramsList.add(word);
				}//single word, need to be careful about initialization sequence with Maps!
					/*else if(wordLen == 1){
						Maps.putWordToPosMap(word, "ent");
					}*/
			}
		}catch(IOException e){
			e.printStackTrace();
		}
		FileUtils.writeToFile(twoGramsList, TWO_GRAM_DATA_FILESTR);
		FileUtils.writeToFile(threeGramsList, THREE_GRAM_DATA_FILESTR);
	}

	/**
	 * Pre-process, e.g. turn "Lie algebra, algebraic" into
	 * "algebraic Lie algebra", and "adic l-" into "l-adic".
	 * @param word
	 * @return
	 */
	private static String preprocessWord(String word) {
		String updatedWord = word;
		//if word ends in slash, e.g. "adic l-"
		Matcher slashMatcher = END_SLASH_PATTERN.matcher(word);
		if(slashMatcher.find()){
			updatedWord = slashMatcher.group(2) + " " + slashMatcher.group(1);
		}
		
		//if word is separated by comma, e.g.
		//"Lie algebra, algebraic"
		Matcher commaMatcher = COMMA_PATTERN.matcher(word);
		if(commaMatcher.find()){
			updatedWord = commaMatcher.group(2) + " " + commaMatcher.group(1);
		}
		
		return updatedWord;
	}
	
}
