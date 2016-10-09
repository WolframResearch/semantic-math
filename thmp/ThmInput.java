package thmp;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads in text from file and extracts definitions, lemmas
 * prepositions, and theorems
 * 
 * @author yihed
 *
 */
public class ThmInput {

	private static Pattern THM_START_PATTERN = Pattern.
			compile("\\\\begin\\{def(?:.*)|\\\\begin\\{lem(?:.*)|\\\\begin\\{th(?:.*)|\\\\begin\\{prop(?:.*)|\\\\begin\\{proclaim(?:.*)");
	private static Pattern THM_END_PATTERN = Pattern.
			compile("\\\\end\\{def(?:.*)|\\\\end\\{lem(?:.*)|\\\\end\\{th(?:.*)|\\\\end\\{prop(?:.*)|\\\\endproclaim(?:.*)");
	private static Pattern LABEL_PATTERN = Pattern.compile("(?:^.*)\\\\label\\{((?:[a-zA-Z]|-)*)\\} (.*)");
	//boldface typesetting. \cat{} refers to category.
	//private static Pattern DF_EMPH_PATTERN = Pattern.compile("\\\\df\\{([^\\}]*)\\}|\\\\emph\\{([^\\}]*)\\}"
		//	+ "|\\\\cat\\{([^}]*)\\}|\\{\\\\it([^}]*)\\}");
	private static Pattern[] GROUP1_PATTERN_ARRAY = new Pattern[]{Pattern.compile("\\\\df\\{([^\\}]*)\\}"),
			Pattern.compile("\\\\emph\\{([^}]*)\\}"),
			Pattern.compile("\\\\cat\\{([^}]*)\\}"),
			Pattern.compile("\\{\\\\it\\s*([^}]*)\\}") //\\{\\\\it([^}]*)\\}
			};
	private static Pattern INDEX_PATTERN = Pattern.compile("\\\\index\\{(^\\})\\}");
	//pattern for eliminating the command completely for web display. E.g. \fml
	private static Pattern ELIMINATE_PATTERN = Pattern.compile("\\\\fml|\\\\ofml|\\\\begin\\{enumerate\\}|\\\\end\\{enumerate\\}");
	private static Pattern ITEM_PATTERN = Pattern.compile("\\\\item");
	
	public static void main(String[] args) throws IOException{
		boolean writeToFile = true;
		if(writeToFile){
			//File file = new File("src/thmp/data/commAlg5.txt");
			//String srcFileStr = "src/thmp/data/commAlg5.txt";
			//String srcFileStr = "src/thmp/data/multilinearAlgebra.txt";
			//String srcFileStr = "src/thmp/data/functionalAnalysis.txt";
			String srcFileStr = "src/thmp/data/fieldsRawTex.txt";			
			
			//Path fileTo = Paths.get("src/thmp/data/thmFile5.txt");
			//Path fileTo = Paths.get("src/thmp/data/multilinearAlgebraThms.txt");
			Path fileTo = Paths.get("src/thmp/data/fieldsThms2.txt");
			
			FileReader srcFileReader = new FileReader(srcFileStr);
			BufferedReader srcFileBReader = new BufferedReader(srcFileReader);
			
			List<String> thmWebDisplayList = new ArrayList<String>();
			List<String> thmList = readThm(srcFileBReader, thmWebDisplayList);
			
			System.out.println(thmWebDisplayList);
			
			//write list of theorems to file
			Files.write(fileTo, thmList, Charset.forName("UTF-8"));
		}
	}

	/**
	 * @param srcFileReader BufferedReader to get tex from.
	 * @param thmWebDisplayList List to contain theorems to display for the web.
	 * without \labels, \index, etc.
	 * @return List of unprocessed theorems read in from srcFileReader, for bag of words search.
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public static List<String> readThm(BufferedReader srcFileReader, List<String> thmWebDisplayList) throws FileNotFoundException, IOException {
		//BufferedReader is faster than scanner
		
		//Scanner sc = new Scanner(file);
		List<String> thms = new ArrayList<String>();
		
		//String newThm = "";
		StringBuilder newThmSB = new StringBuilder();		
		boolean inThm = false;
		String line;
		while((line=srcFileReader.readLine()) != null){
			//while(sc.hasNextLine()){
			if(line.matches("\\s*")) continue;
			
			//if(line.matches("(?:\\\\begin\\{def[^}]*\\}|\\\\begin\\{lem[^}]*\\}|\\\\begin\\{th[^}]*\\}|\\\\begin\\{prop[^}]*\\})(?:.)*")){	
			Matcher matcher = THM_START_PATTERN.matcher(line);
			if(matcher.find()){	
				//if(line.matches("\\\\begin\\{definition\\}|\\\\begin\\{lemma\\}")){
				//if(line.matches("\\\\begin\\{definition\\}|\\\\begin\\{lemma\\}|\\\\begin\\{thm\\}|\\\\begin\\{theorem\\}")){	
				//newThm = line;	
				newThmSB.append(line);
				line = srcFileReader.readLine();
				inThm = true;
			}			
			//else if(line.matches("\\\\end\\{definition\\}|\\\\end\\{lemma\\}")){
			else if(THM_END_PATTERN.matcher(line).find()){
			//else if(line.matches("\\\\end\\{definition\\}|\\\\end\\{lemma\\}|\\\\end\\{thm\\}|\\\\end\\{theorem\\}")){
				inThm = false;
				//newThm += "\n";	
				
				//process here, return two versions, one for bag of words, one for display
				//strip \df, \empf. Index followed by % strip, not percent don't strip.
				//replace enumerate and \item with *
				String thm = processTex(newThmSB, thmWebDisplayList) + "\n";
				
				//newThmSB.append("\n");
				/*String[] meat = thm.split("\\\\label\\{([a-zA-Z]|-)*\\} ");
				String noTexString = "";
				//get the second part, meat[1], if separated by "\label{...}"
				if(meat.length > 1){
					thm = meat[1];
					//System.out.println(thm);
				}*/
				//String thm = newThmSB.toString();
				if(!thm.matches("\\s*")){ 
					thms.add(thm);				
				}
				//newThm = "";
				newThmSB.setLength(0);
				continue;
			}
			
			if(inThm){
				//newThm = newThm + " " + line;
				newThmSB.append(" " + line);
			}
		}
		
		//srcFileReader.close();
		//System.out.println("Inside ThmInput, thmsList " + thms);
		return thms;
	}
	
	/**
	 * 
	 * @param newThmSB
	 * @param thmWebDisplayList
	 * @return Thm without the "\begin{lemma}", "\label{}", etc parts.
	 */
	private static String processTex(StringBuilder newThmSB, List<String> thmWebDisplayList){
		
		String noLabelThmStr = newThmSB.toString();
		
		//replace \df{} and \emph{} with their content
		Matcher matcher;
		for(Pattern pattern : GROUP1_PATTERN_ARRAY){
			matcher = pattern.matcher(noLabelThmStr);
			
			if(matcher.find()){
				//System.out.println(matcher.group(1));
				noLabelThmStr = matcher.replaceAll("$1");		
			}
		}
		
		//eliminate symbols such as \fml
		matcher = ELIMINATE_PATTERN.matcher(noLabelThmStr);
		noLabelThmStr = matcher.replaceAll("");
		
		matcher = ITEM_PATTERN.matcher(noLabelThmStr);
		noLabelThmStr = matcher.replaceAll("*");
		
		//containing the words inside \label and \index etc, but not the words "\label", "\index", 
		// for bag-of-words searching.
		String wordsThmStr = noLabelThmStr;
		
		//remove label 
		//StringBuilder a;
		matcher = LABEL_PATTERN.matcher(noLabelThmStr);
		if(matcher.find()){			
			wordsThmStr = matcher.group(1) + ":: " + matcher.group(2);
			//get thm content
			noLabelThmStr = matcher.group(2);
		}
		
		//replace \index{...} with its content for wordsThmStr and nothing for web display version
		matcher = INDEX_PATTERN.matcher(wordsThmStr);
		wordsThmStr = matcher.replaceAll("$1");
		
		matcher = INDEX_PATTERN.matcher(noLabelThmStr);
		noLabelThmStr = matcher.replaceAll("");
		
		thmWebDisplayList.add(noLabelThmStr);
		return wordsThmStr;
	}
	
}
