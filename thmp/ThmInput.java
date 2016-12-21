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

import thmp.utils.WordForms;

/**
 * Reads in text from file and extracts definitions, lemmas prepositions, and
 * theorems
 * 
 * @author yihed
 *
 */
public class ThmInput {

	//Intentionally *not* final, as must append custom author-defined macros and compile the pattern
	static String THM_START_STR = "\\\\begin\\{def(?:.*)|\\\\begin\\{lem(?:.*)|\\\\begin\\{thm(?:.*)|\\\\begin\\{theo(?:.*)|\\\\begin\\{prop(?:.*)"
			+ "|\\\\begin\\{proclaim(?:.*)|\\\\begin\\{cor(?:.*)";
	
	static final Pattern THM_START_PATTERN = Pattern.compile(THM_START_STR);
	
	// start of theorem, to remove words such as \begin[prop]
	 /*
	 * private static Pattern SENTENCE_START_PATTERN = Pattern. compile(
	 * "^\\\\begin\\{def(?:[^}]*)\\}\\s*|^\\\\begin\\{lem(?:[^}]*)\\}\\s*|^\\\\begin\\{th(?:[^}]*)\\}\\s*"
	 * +
	 * "|^\\\\begin\\{prop(?:[^}]*)\\}\\s*|^\\\\begin\\{proclaim(?:[^}]*)\\}\\s*"
	 * );
	 */
	
	static String THM_END_STR = "\\\\end\\{def(?:.*)|\\\\end\\{lem(?:.*)|\\\\end\\{thm(?:.*)|\\\\end\\{theo(?:.*)|\\\\end\\{prop(?:.*)|\\\\endproclaim(?:.*)"
			+ "|\\\\end\\{cor(?:.*)";
	static final Pattern THM_END_PATTERN = Pattern.compile(THM_END_STR);
	
	//begin of latex expression
	static final Pattern BEGIN_PATTERN = Pattern.compile("\\\\begin.*");
	
	//new theorem pattern
	static final Pattern NEW_THM_PATTERN = Pattern.compile("\\\\newtheorem\\{([^}])\\}(?:[^{]*)\\{([^}]).*");
		
	static final Pattern THM_TERMS_PATTERN = Pattern.compile("Theorem|Proposition|Lemma|Corollary");
	private static final Pattern LABEL_PATTERN = Pattern.compile("(?:^.*)\\\\label\\{([^}]*)\\}\\s*(.*)");
	private static final Pattern DIGIT_PATTERN = Pattern.compile(".*\\d+.*");

	// boldface typesetting. \cat{} refers to category. MUST *Update* DF_EMPH_PATTERN_REPLACEMENT when updating this!
	private static final Pattern DF_EMPH_PATTERN = Pattern
			.compile("\\\\df\\{([^\\}]*)\\}|\\\\emph\\{([^\\}]*)\\}|\\\\em\\{([^\\}]*)\\}|\\\\cat\\{([^}]*)\\}|\\{\\\\it\\s*([^}]*)\\}"
					+ "|\\\\ref\\{([^}]*)\\}");
	// replacement for DF_EMPH_PATTERN, should have same number of groups as
	// number of patterns in DF_EMPH_PATTERN.
	private static final String DF_EMPH_PATTERN_REPLACEMENT = "$1$2$3$4$5$6";

	/*private static Pattern[] GROUP1_PATTERN_ARRAY = new Pattern[] { Pattern.compile("\\\\df\\{([^\\}]*)\\}"),
			Pattern.compile("\\\\emph\\{([^}]*)\\}"), Pattern.compile("\\\\cat\\{([^}]*)\\}"),
			Pattern.compile("\\{\\\\it\\s*([^}]*)\\}") // \\{\\\\it([^}]*)\\}
	};*/
	private static final Pattern INDEX_PATTERN = Pattern.compile("\\\\index\\{([^\\}]*)\\}%*");
	// pattern for eliminating the command completely for web display. E.g. \fml
	private static Pattern ELIMINATE_PATTERN = Pattern
			.compile("\\\\fml|\\\\ofml|\\\\begin\\{enumerate\\}|\\\\end\\{enumerate\\}"
					+ "|\\\\begin\\{def(?:[^}]*)\\}\\s*|\\\\begin\\{lem(?:[^}]*)\\}\\s*|\\\\begin\\{th(?:[^}]*)\\}\\s*"
					+ "|\\\\begin\\{prop(?:[^}]*)\\}\\s*|\\\\begin\\{proclaim(?:[^}]*)\\}\\s*|\\\\begin\\{cor(?:[^}]*)\\}\\s*"
					+ "|\\\\begin\\{slogan\\}|\\\\end\\{slogan\\}|\\\\sbsb|\\\\cat|\\\\bs");
	
	private static final Pattern ITEM_PATTERN = Pattern.compile("\\\\item");

	public static void main(String[] args) throws IOException {
		boolean writeToFile = false;

		// File file = new File("src/thmp/data/commAlg5.txt");
		// String srcFileStr = "src/thmp/data/commAlg5.txt";
		// String srcFileStr = "src/thmp/data/multilinearAlgebra.txt";
		String srcFileStr = "src/thmp/data/functionalAnalysis.txt";
		// String srcFileStr = "src/thmp/data/fieldsRawTex.txt";
		// String srcFileStr = "src/thmp/data/test1.txt";
		FileReader srcFileReader = new FileReader(srcFileStr);
		BufferedReader srcFileBReader = new BufferedReader(srcFileReader);

		List<String> thmWebDisplayList = new ArrayList<String>();
		List<String> bareThmList = new ArrayList<String>();
		List<String> thmList = readThm(srcFileBReader, thmWebDisplayList, bareThmList);
		if (writeToFile) {
			// Path fileTo = Paths.get("src/thmp/data/thmFile5.txt");
			// Path fileTo =
			// Paths.get("src/thmp/data/multilinearAlgebraThms2.txt");
			Path fileTo = Paths.get("src/thmp/data/functionalAnalysisThms2.txt");
			// Path fileTo = Paths.get("src/thmp/data/test1Thms.txt");
			// Path fileTo = Paths.get("src/thmp/data/fieldsThms2.txt");

			System.out.println(thmWebDisplayList);

			// write list of theorems to file
			Files.write(fileTo, thmList, Charset.forName("UTF-8"));
		}
	}

	/**
	 * Read in theorems, with list of custom \newtheorem commands
	 * @param srcFileReader
	 * @return
	 */
	/*public static List<String> readThm(BufferedReader srcFileReader, List<String> thmWebDisplayList,
			List<String> bareThmList)
			throws FileNotFoundException, IOException {
		return ThmInput.readThm(srcFileReader, thmWebDisplayList, bareThmList, null);
	}*/
	
	/**
	 * Extracts list of theorems/propositions/etc from provided BufferedReader.
	 * @param srcFileReader
	 *            BufferedReader to get tex from.
	 * @param thmWebDisplayList
	 *            List to contain theorems to display for the web. without
	 *            \labels, \index, etc. Can be null, for callers who don't need it.
	 * @param bareThmList
	 * 				bareThmList for parsing, without label content. Can be null.
	 * @param macros author-defined macros using \newtheorem
	 * @return List of unprocessed theorems read in from srcFileReader, for bag
	 *         of words search.
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public static List<String> readThm(BufferedReader srcFileReader, List<String> thmWebDisplayList,
			List<String> bareThmList)	
			throws FileNotFoundException, IOException {
		
		// \\\\end\\{def(?:.*)
		Pattern thmStartPattern = THM_START_PATTERN;
		Pattern thmEndPattern = THM_END_PATTERN;
		List<String> macrosList = new ArrayList<String>();
		
		List<String> thms = new ArrayList<String>();

		String line;
		
		//read in custom macros, break as soon as \begin{document} encountered
		while ((line = srcFileReader.readLine()) != null) {
			Matcher newThmMatcher = NEW_THM_PATTERN.matcher(line);
			
			if(BEGIN_PATTERN.matcher(line).find()){
				break;
			}else if(newThmMatcher.find()){
				//should be a proposition, hypothesis, etc
				if(THM_TERMS_PATTERN.matcher(newThmMatcher.group(2)).find()){
					macrosList.add(newThmMatcher.group(2));		
				}
			}			
		}
		
		//append list of macros to THM_START_STR and THM_END_STR
		if(!macrosList.isEmpty()){
			StringBuilder startBuilder = new StringBuilder();
			StringBuilder endBuilder = new StringBuilder();
			for(String macro : macrosList){
				//create start and end macros
				startBuilder.append("\\\\begin\\{").append(macro).append(".*");
				
				endBuilder.append("\\\\end\\{").append(macro).append(".*");
			}
			thmStartPattern = Pattern.compile(THM_START_STR + startBuilder);
			thmEndPattern = Pattern.compile(THM_END_STR + endBuilder);	
		}
			
		StringBuilder newThmSB = new StringBuilder();
		boolean inThm = false;
		Matcher matcher = thmStartPattern.matcher(line);
		if (matcher.find()) {
			inThm = true;
		}
		while ((line = srcFileReader.readLine()) != null) {
			// while(sc.hasNextLine()){
			if (WordForms.getWhitespacePattern().matcher(line).find()){
				continue;
			}
			
			// if(line.matches("(?:\\\\begin\\{def[^}]*\\}|\\\\begin\\{lem[^}]*\\}|\\\\begin\\{th[^}]*\\}|\\\\begin\\{prop[^}]*\\})(?:.)*")){
			matcher = thmStartPattern.matcher(line);
			if (matcher.find()) {
				// if(line.matches("\\\\begin\\{definition\\}|\\\\begin\\{lemma\\}")){
				// if(line.matches("\\\\begin\\{definition\\}|\\\\begin\\{lemma\\}|\\\\begin\\{thm\\}|\\\\begin\\{theorem\\}")){
				// newThm = line;
				// newThmSB.append(line);
				// line = srcFileReader.readLine();
				inThm = true;
			}
			// else
			// if(line.matches("\\\\end\\{definition\\}|\\\\end\\{lemma\\}")){
			else if (thmEndPattern.matcher(line).find()) {
				// else
				// if(line.matches("\\\\end\\{definition\\}|\\\\end\\{lemma\\}|\\\\end\\{thm\\}|\\\\end\\{theorem\\}")){
				inThm = false;
				// newThm += "\n";

				// process here, return two versions, one for bag of words, one
				// for display
				// strip \df, \empf. Index followed by % strip, not percent
				// don't strip.
				// replace enumerate and \item with *
				String thm = processTex(newThmSB, thmWebDisplayList, bareThmList) + "\n";

				// newThmSB.append("\n");
				/*
				 * String[] meat = thm.split("\\\\label\\{([a-zA-Z]|-)*\\} ");
				 * String noTexString = ""; //get the second part, meat[1], if
				 * separated by "\label{...}" if(meat.length > 1){ thm =
				 * meat[1]; //System.out.println(thm); }
				 */
				// String thm = newThmSB.toString();
				if (!WordForms.getWhitespacePattern().matcher(thm).find()) {
					thms.add(thm);
				}
				// newThm = "";
				newThmSB.setLength(0);
				continue;
			}

			if (inThm) {
				// newThm = newThm + " " + line;
				newThmSB.append(" " + line);
			}
		}

		// srcFileReader.close();
		// System.out.println("Inside ThmInput, thmsList " + thms);
		// System.out.println("thmWebDisplayList " + thmWebDisplayList);
		return thms;
	}

	/**
	 * Processes Latex input, e.g. by removing syntax used purely for display and not
	 * useful for parsing, such as \textit{ }.
	 * But enumerate should not always be turned off.
	 * @param newThmSB 
	 * @param thmWebDisplayList Can be null.
	 * @param bareThmList Can be null.
	 * @return Thm without the "\begin{lemma}", "\label{}", etc parts.
	 */
	public static String processTex(StringBuilder newThmSB, List<String> thmWebDisplayList,
			List<String> bareThmList) {

		boolean getWebDisplayList = thmWebDisplayList == null ? false : true;
		boolean getBareThmList = bareThmList == null ? false : true;		

		String noLabelThmStr = newThmSB.toString();

		// replace \df{} and \emph{} with their content
		Matcher matcher = DF_EMPH_PATTERN.matcher(noLabelThmStr);
		noLabelThmStr = matcher.replaceAll(DF_EMPH_PATTERN_REPLACEMENT);

		// eliminate symbols such as \fml
		matcher = ELIMINATE_PATTERN.matcher(noLabelThmStr);
		noLabelThmStr = matcher.replaceAll("");
		
		matcher = ITEM_PATTERN.matcher(noLabelThmStr);
		//replace \item with bullet points (*)
		noLabelThmStr = matcher.replaceAll(" (*)");

		// containing the words inside \label and \index etc, but not the words
		// "\label", "\index",
		// for bag-of-words searching.
		String wordsThmStr = noLabelThmStr;

		// replace \index{...} with its content for wordsThmStr and nothing for
		// web display version
		matcher = INDEX_PATTERN.matcher(wordsThmStr);

		if (matcher.find()) {
			// String insideIndexStr = new String(matcher.group(1));
			// insideIndexStr = insideIndexStr.replaceAll("!", " ");
			// System.out.println(insideIndexStr);
			wordsThmStr = matcher.replaceAll("$1");
			// replace a!b!c inside \index with spaced-out versions
			wordsThmStr = wordsThmStr.replaceAll("!", " ");
		}

		if(getWebDisplayList || getBareThmList){
			matcher = INDEX_PATTERN.matcher(noLabelThmStr);
			noLabelThmStr = matcher.replaceAll("");
		}
		
		//bare thm string with no label content at the beginning.
		String bareThmStr = noLabelThmStr;
		// remove label
		matcher = LABEL_PATTERN.matcher(noLabelThmStr);
		if (matcher.find()) {
			String labelContent = matcher.group(1);
			Matcher matcher2 = DIGIT_PATTERN.matcher(labelContent);
			String withLabelContent = null;
			if (!matcher2.find()) {
				withLabelContent = matcher.group(1) + ":: " + matcher.group(2);
				wordsThmStr = withLabelContent;
				// get thm content
				noLabelThmStr = withLabelContent;
			} else {
				wordsThmStr = matcher.group(2);
			}
			//this is only executed when getting theorems for context parsing.
			if(getBareThmList){			
				StringBuilder sb = new StringBuilder();
				if(withLabelContent != null){					
					//label often contains dashes "-"
					String[] labelAr = (labelContent + ".").split(" |-");
					for(String word : labelAr){
						sb.append(word + " ");
					}					
				}
				bareThmStr = sb + matcher.group(2);
			}
		}

		if(getBareThmList){
			bareThmList.add(bareThmStr);
		}
		if(getWebDisplayList){
			thmWebDisplayList.add(noLabelThmStr);
		}
		return wordsThmStr;
	}

}
