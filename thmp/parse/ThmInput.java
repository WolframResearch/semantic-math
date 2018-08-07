package thmp.parse;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import thmp.utils.MacrosTrie;
import thmp.utils.MacrosTrie.MacrosTrieBuilder;
import thmp.utils.WordForms;

/**
 * Reads in text from file and extracts definitions, lemmas prepositions, and
 * theorems.
 * Use this to extract just theorems/propositions/etc to file. 
 * 
 * @author yihed
 *
 */
public class ThmInput {

	//Intentionally *not* final, as must append custom author-defined macros and compile the pattern
	//factor out the \begin! shouldn't need the [^\\\\]* if using matcher.find()
	/*static String THM_START_STR = ".*\\\\begin\\s*\\{(def)(?:.*)|.*\\\\begin\\s*\\{(lem)(?:.*)"
			//<--{prop.. instead of {pro.., since need to skip parsing proofs.
			+ "|.*\\\\begin\\s*\\{(theo)(?:.*)|.*\\\\begin\\s*\\{(thm)(?:.*)|.*\\\\begin\\s*\\{(prop)(?:.*)" 
			+ "|.*\\{(proclaim)(?:.*)|.*\\\\begin\\s*\\{(cor)(?:.*)|.*\\\\begin\\s*\\{(conj)(?:.*)";*/
	
	private static String THM_START_STR = ".*\\\\begin\\s*\\{(def|lem|theo|thm|prop|cor|conj).*"
			//<--{prop.. instead of {pro.., since need to skip parsing proofs.
			//not capturing the proclaim.
			+ "|.*\\{proclaim(?:.*)";
	static String THM_START_STR0 = ".*\\\\begin\\s*\\{(";
	static String THM_START_STR1 = "def|lem|theo|thm|prop|cor|conj).*"
			//<--{prop.. instead of {pro.., since need to skip parsing proofs.
			//not capturing the proclaim.
			+ "|.*\\{proclaim(?:.*)";	
	//default map of thm types and their full type names, e.g. thm->Theorem.
	static final Map<String, String> defaultThmTypeMap;
	
	static final Pattern THM_START_PATTERN = Pattern.compile(THM_START_STR);	
	// start of theorem, to remove words such as \begin[prop]
	 /*
	 * private static Pattern SENTENCE_START_PATTERN = Pattern. compile(
	 * "^\\\\begin\\{def(?:[^}]*)\\}\\s*|^\\\\begin\\{lem(?:[^}]*)\\}\\s*|^\\\\begin\\{th(?:[^}]*)\\}\\s*"
	 * +
	 * "|^\\\\begin\\{prop(?:[^}]*)\\}\\s*|^\\\\begin\\{proclaim(?:[^}]*)\\}\\s*"
	 * );
	 */	
	/*static String THM_END_STR = ".*(?:\\\\end\\s*\\{def(?:.*)|\\\\end\\s*\\{lem(?:.*)"
			+ "|\\\\end\\s*\\{theo(?:.*)|\\\\end\\s*\\{thm(?:.*)|\\\\end\\s*\\{prop.*|end\\s*\\{proclaim(?:.*)"
			+ "|\\\\endproclaim(?:.*)|\\\\end\\s*\\{cor(?:.*)|\\\\end\\s*\\{conj(?:.*))";*/

	static String THM_END_STR0 = ".*\\\\end\\s*\\{(";
	static String THM_END_STR1 = "def|lem|theo|thm|prop|proclaim|cor|conj).*"
			+ "|\\\\endproclaim.*";
	
	static String THM_END_STR = ".*\\\\end\\s*\\{(def|lem|theo|thm|prop|proclaim|cor|conj).*"
			+ "|\\\\endproclaim.*";
	//static String THM_END_STR = "\\\\end\\{def(?:.*)|\\\\end\\{lem(?:.*)|\\\\end\\{th(?:.*)|\\\\end\\{pro(?:.*)|\\\\endproclaim(?:.*)"
			//+ "|\\\\end\\{cor(?:.*)";
	static final Pattern THM_END_PATTERN = Pattern.compile(THM_END_STR);
	
	//begin of latex expression
	//static final Pattern BEGIN_PATTERN = Pattern.compile("[^\\\\]*\\\\begin(?!\\{document)");
	static final Pattern BEGIN_PATTERN = Pattern.compile("[^\\\\]*\\\\begin");
	static final Pattern BEGIN_DOC_PATTERN = Pattern.compile("\\\\begin\\s*\\{\\s*document");
	
	//new theorem pattern. E.g. \newtheorem{corollary}[theorem]{Corollary}
	static final Pattern NEW_THM_PATTERN = Pattern.compile("\\\\newtheorem\\**\\s*\\{([^}]+)\\}(?:[^{]*)\\{([^}]+).*");
	/*another custom definition specification, \newcommand{\xra}  {\xrightarrow}
	  Need to be smart about e.g. \newcommand{\\un}[1]{\\underline{#1}} !*/
	//Should also support \newcommand{cmd}[args][opt]{def} with default val for optional terms
	static final Pattern NEW_CMD_PATTERN = Pattern.compile("\\s*\\\\(?:renew|new|provide)command\\{*([^}{\\[]+)\\}*\\s*(?:\\[(\\d)\\])*(?:\\[([^]]+)\\])*\\s*\\{(.+?)\\}\\s*");
	/*e.g. \def\X{{\cal X}};  \def \author {William {\sc Smith}}; 
	 * Need to support e.g. \def\G{\hbox{\boldmath{}$G$\ unboldmath}}
	 *Currently not covering: \def <command> <parameter-text>{<replacement-text>} e.g. \def\testonearg[#1]{\typeout{Testing one arg: '#1'}} */
	public static final Pattern NEW_DEF_PATTERN = Pattern.compile("\\s*\\\\def\\s*([^{]+?)\\s*\\{(.+?)\\}\\s*");
	//define math operator a la \DeclareMathOperator{\sin}{sin}, \DeclareMathOperator*{\spec}{Spec}
	public static final Pattern MATH_OP_PATTERN = Pattern.compile("\\s*\\\\DeclareMathOperator\\**\\s*\\{([^}]+)\\}\\s*\\{(.+?)\\}\\s*");
	
	static final Pattern THM_TERMS_PATTERN = Pattern.compile("(Theorem|Proposition|Lemma|Corollary|Conjecture|Definition|Claim)");
	
	private static final Pattern LABEL_PATTERN = Pattern.compile("(.*?)\\\\(?:label)\\{([^}$]*)\\}\\s*(.*?)");
	//deliberately not including eqref
	private static final Pattern REF_PATTERN = Pattern.compile("\\\\(?:ref)\\{([^}$]*)\\}");
	//private static final Pattern DIGIT_PATTERN = Pattern.compile(".*\\d+.*");
	static final Pattern endAnyPattern = Pattern.compile("(.*?)\\\\end.*");
	static final Pattern beginAnyPattern = Pattern.compile("(.*?)\\\\begin.*");
	
	/* Boldface typesetting. \cat{} refers to category. *MUST Update* DF_EMPH_PATTERN_REPLACEMENT when updating this!
	   e.g. {\em lll\/}. These are deprecated as of May 2018. But keep for reference.*/
	/*private static final Pattern DF_EMPH_PATTERN = Pattern
			.compile("\\\\df\\{([^\\}]*)\\}|\\\\emph\\{([^\\}]*)\\}|\\{\\\\em\\s+([^\\}]+)[\\\\/]*\\}|\\\\cat\\{([^}]*)\\}|\\{\\\\it\\s*([^}]*)\\}"
					+ "|\\\\(?:eq)*ref\\{([^}]*)\\}|\\\\subsection\\{([^}]*)\\}|\\\\section\\{([^}]*)\\}|\\\\bf\\{([^}]*)\\}"
					+ "|\\\\ensuremath\\{([^}]+)\\}|\\\\(?:textbf|textsl|textsc)\\{([^}]+)\\}");*/
	/* Replacement for DF_EMPH_PATTERN, should have same number of groups as
	   number of patterns in DF_EMPH_PATTERN. */
	/*private static final String DF_EMPH_PATTERN_REPLACEMENT = "$1$2$3$4$5$6$7$8$9$10$11";*/
	
	//*MUST Update* groupReplacePattStr when updating this! Include eqref but not ref
	private static Pattern groupReplacePatt = Pattern.compile("\\\\eqref\\s*\\{([^}]+)\\}");
	private static String groupReplacePattStr = "$1";
	
	//replace \begin or \end {equation} with \[ or \], since MathJax does not seem to render them correctly on web
	private static final Pattern beginEqnPatt = Pattern.compile("\\\\begin\\s*\\{equation\\**\\}");
	private static final Pattern endEqnPatt = Pattern.compile("\\\\end\\s*\\{equation\\**\\}");
	
	/*private static Pattern[] GROUP1_PATTERN_ARRAY = new Pattern[] { Pattern.compile("\\\\df\\{([^\\}]*)\\}"),
			Pattern.compile("\\\\emph\\{([^}]*)\\}"), Pattern.compile("\\\\cat\\{([^}]*)\\}"),
			Pattern.compile("\\{\\\\it\\s*([^}]*)\\}") // \\{\\\\it([^}]*)\\}
	};*/
	//
	private static Pattern lessThanGreaterThanPatt = Pattern.compile("(<(?!\\s)|(?<!\\s)>)");
	
	private static final Pattern INDEX_PATTERN = Pattern.compile(".*\\\\index\\{([^\\}]*)\\}%*.*");
	/* pattern for eliminating the command completely for web display. E.g. \fml. How about \begin or \end everything?
	//patterns such as \\rm are deprecated in TeX, but (new) authors still use them, and MathJax doesn't render them.
	Space after \rm intentional. Don't include \ref.
	*/
	private static final Pattern ELIMINATE_PATTERN = Pattern
			.compile("\\\\df(?!rac)|\\\\emph|\\\\em|\\\\rm |\\\\cat|\\\\it(?!e)|\\\\eqref|\\\\subsection|\\\\section|\\\\bf|\\\\vspace"//\\\\(?:eq)*ref
					+ "|\\\\ensuremath|\\\\(?:textbf|textsl|textsc|textnormal)|\\\\linebreak|(?<!\\\\)%"
					+ "|\\\\fml|\\\\ofml|\\\\(?:begin|end)\\{enumerate\\}|\\\\(?:begin|end)\\{(?:sub)*section\\**\\}"					
					+ "|\\\\begin\\{slogan\\}|\\\\end\\{slogan\\}|\\\\sbsb|\\\\cat|\\\\bs|\\\\maketitle|\\\\textup"
					+ "|\\\\section\\**\\{(?:[^}]*)\\}\\s*|\\\\noindent|\\\\(?:small|big)skip" 
					//eliminate \begin{aligned} later. |\\\\begin\\{a(?:[^}]*)\\}|\\\\end\\{a(?:[^}]*)\\}\\s*"
					+ "|\\\\cite\\{[^}]+\\}|\\\\cite\\[[^\\]]+\\]|\\\\par\\s|\\\\(?:begin|end)\\{displaymath\\}",
					Pattern.CASE_INSENSITIVE);
	//Eliminate begin/end{align*} as well, for better display on web frontend. <--don't eliminate begin/end{align}!
	//Note this string is used to dynamically compile Patterns in DetectHypothesis.java, so must edit with caution.
	static final String ELIMINATE_BEGIN_END_THM_STR = "\\\\begin\\{def(?:[^}]*)\\}\\s*|\\\\begin\\{lem(?:[^}]*)\\}\\s*|\\\\begin\\{th(?:[^}]*)\\}\\s*"
					+ "|\\\\begin\\{pr(?:[^}]*)\\}\\s*|\\\\begin\\{proclaim(?:[^}]*)\\}\\s*|\\\\begin\\{co(?:[^}]*)\\}\\s*"
					+ "|\\\\end\\{def(?:[^}]*)\\}\\s*|\\\\end\\{lem(?:[^}]*)\\}\\s*|\\\\end\\{th(?:[^}]*)\\}\\s*"
					+ "|\\\\end\\{pr(?:[^}]*)\\}\\s*|\\\\end\\{proclaim(?:[^}]*)\\}\\s*|\\\\end\\{co(?:[^}]*)\\}\\s*"
					+ "|\\\\(?:begin|end)\\{re(?:[^}]*)\\}\\s*";
	
	static final Pattern ELIMINATE_BEGIN_END_THM_PATTERN = Pattern
			.compile(ELIMINATE_BEGIN_END_THM_STR, Pattern.CASE_INSENSITIVE);
	
	private static final Pattern ITEM_PATTERN = Pattern.compile("\\\\item");

	static {
		Map<String, String> defaultThmTypeMap0 = new HashMap<String, String>();
		defaultThmTypeMap0.put("thm", "Theorem");
		defaultThmTypeMap0.put("theo", "Theorem");
		defaultThmTypeMap0.put("prop", "Proposition");
		defaultThmTypeMap0.put("lem", "Lemma");
		defaultThmTypeMap0.put("proclaim", "Claim");
		defaultThmTypeMap0.put("cor", "Corollary");
		defaultThmTypeMap0.put("conj", "Conjecture");
		defaultThmTypeMap0.put("def", "Definition");
		
		defaultThmTypeMap = new HashMap<String, String>(defaultThmTypeMap0);
	}
	
	public static void main1(String[] args) throws IOException {
		boolean writeToFile = false;

		// File file = new File("src/thmp/data/commAlg5.txt");
		// String srcFileStr = "src/thmp/data/commAlg5.txt";
		// String srcFileStr = "src/thmp/data/multilinearAlgebra.txt";
		//String srcFileStr = "src/thmp/data/functionalAnalysis.txt";
		//String srcFileStr = "src/thmp/data/fieldsRawTex.txt";
		String srcFileStr = "src/thmp/data/thmsFeb27.txt";
		// String srcFileStr = "src/thmp/data/test1.txt";
		FileReader srcFileReader = new FileReader(srcFileStr);
		BufferedReader srcFileBReader = new BufferedReader(srcFileReader);

		List<String> thmWebDisplayList = new ArrayList<String>();
		List<String> bareThmList = new ArrayList<String>();
		List<String> thmList = readThm(srcFileBReader, thmWebDisplayList, bareThmList);
		System.out.println("ThmInput - thmList after processing: " + thmList);
		if (writeToFile) {
			// Path fileTo = Paths.get("src/thmp/data/test1Thms.txt");
			Path fileTo = Paths.get("src/thmp/data/fieldsThms2.txt");
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
		List<String> customBeginThmList = new ArrayList<String>();
		//Map<Pattern, String> macrosMap = new HashMap<Pattern, String>();
		MacrosTrieBuilder macrosTrieBuilder = new MacrosTrieBuilder();
		
		List<String> thms = new ArrayList<String>();

		String line;		
		//read in custom macros, break as soon as \begin{document} encountered
		while ((line = srcFileReader.readLine()) != null) {			
			Matcher newThmMatcher;			
			if(BEGIN_PATTERN.matcher(line).find()){
				break;
			}else if((newThmMatcher = NEW_THM_PATTERN.matcher(line)).find()){
				//should be a proposition, hypothesis, etc
				if(THM_TERMS_PATTERN.matcher(newThmMatcher.group(2)).find()){
					customBeginThmList.add(newThmMatcher.group(1));		
				}
			}else if((newThmMatcher = NEW_CMD_PATTERN.matcher(line)).matches()){
				//macrosList.add(newThmMatcher)
				String commandStr = newThmMatcher.group(1);
				String replacementStr = newThmMatcher.group(3);
				String slotCountStr = newThmMatcher.group(2);
				int slotCount = null == slotCountStr ? 0 : Integer.valueOf(slotCountStr);
				macrosTrieBuilder.addTrieNode(commandStr, replacementStr, slotCount);
				
				/*if(newThmMatcher.group(1).equals("\\xra")){
					System.out.println("*****replacement " + newThmMatcher.group(2));
					System.out.println(Pattern.compile(Matcher.quoteReplacement(newThmMatcher.group(1))).matcher("\\xra").matches());
				}*/
			}
		}
		/*Ensure immutability of Trie*/
		MacrosTrie macrosTrie = macrosTrieBuilder.build();
		//System.out.println("macrosTrie " + macrosTrie);
		//append list of macros to THM_START_STR and THM_END_STR
		if(!customBeginThmList.isEmpty()){
			StringBuilder startBuilder = new StringBuilder();
			StringBuilder endBuilder = new StringBuilder();
			for(String macro : customBeginThmList){
				//create start and end macros
				startBuilder.append("\\\\begin\\{").append(macro).append(".*");				
				endBuilder.append("\\\\end\\{").append(macro).append(".*");
			}
			thmStartPattern = Pattern.compile(THM_START_STR + startBuilder);
			thmEndPattern = Pattern.compile(THM_END_STR + endBuilder);	
		}
		//Pattern p = Pattern.compile("\\\\xra");
		//System.out.println("------macrosMap: "+ macrosMap);
		
		StringBuilder newThmSB = new StringBuilder();
		boolean inThm = false;
		Matcher matcher = thmStartPattern.matcher(line);
		if (matcher.find()) {
			inThm = true;
		}
		while ((line = srcFileReader.readLine()) != null) {			
			if (WordForms.getWhiteEmptySpacePattern().matcher(line).find()){
				continue;
			}			
			// if(line.matches("(?:\\\\begin\\{def[^}]*\\}|\\\\begin\\{lem[^}]*\\}|\\\\begin\\{th[^}]*\\}|\\\\begin\\{prop[^}]*\\})(?:.)*")){
			matcher = thmStartPattern.matcher(line);
			if (matcher.find()) {
				// if(line.matches("\\\\begin\\{definition\\}|\\\\begin\\{lemma\\}")){
				// if(line.matches("\\\\begin\\{definition\\}|\\\\begin\\{lemma\\}|\\\\begin\\{thm\\}|\\\\begin\\{theorem\\}")){				
				inThm = true;
			}			
			else if (thmEndPattern.matcher(line).find()) {
				// else
				// if(line.matches("\\\\end\\{definition\\}|\\\\end\\{lemma\\}|\\\\end\\{thm\\}|\\\\end\\{theorem\\}")){
				inThm = false;
				//append the e.g. "\end{theorem}"
				newThmSB.append(" ").append(line);
				// process here, return two versions, one for bag of words, one
				// for display
				// strip \df, \empf. Index followed by % strip, not percent
				// don't strip.
				// replace enumerate and \item with *
				//System.out.println("newThmSB! " + newThmSB);				
				String thm = removeTexMarkup(newThmSB.toString(), thmWebDisplayList, bareThmList, macrosTrie,
						ELIMINATE_BEGIN_END_THM_PATTERN) + "\n";
				//System.out.println("after removeTexMarkup! " + thm);
				/*
				 * String[] meat = thm.split("\\\\label\\{([a-zA-Z]|-)*\\} ");
				 * String noTexString = ""; //get the second part, meat[1], if
				 * separated by "\label{...}" if(meat.length > 1){ thm =
				 * meat[1]; //System.out.println(thm); }
				 */
				// String thm = newThmSB.toString();
				if (!WordForms.getWhiteEmptySpacePattern().matcher(thm).find()) {
					thms.add(thm);
				}				
				newThmSB.setLength(0);
				continue;
			}

			if (inThm) {
				newThmSB.append(" ").append(line);				
			}
		}

		// srcFileReader.close();
		return thms;
	}

	public static String removeTexMarkup(String thmStr, List<String> thmWebDisplayList,
			List<String> bareThmList) {
		return removeTexMarkup(thmStr, thmWebDisplayList, bareThmList, null, ELIMINATE_BEGIN_END_THM_PATTERN);
	}
	
	public static String removeTexMarkup(String thmStr, List<String> thmWebDisplayList,
			List<String> bareThmList, MacrosTrie macrosTrie, Pattern eliminateBeginEndThmPattern) {
		boolean isContextStr = false;
		return removeTexMarkup(thmStr, thmWebDisplayList, bareThmList, macrosTrie, eliminateBeginEndThmPattern, isContextStr);
	}
	
	public static String removeTexMarkup(String thmStr, List<String> thmWebDisplayList,
			List<String> bareThmList, MacrosTrie macrosTrie, Pattern eliminateBeginEndThmPattern, 
			boolean isContextStr) {		
		return removeTexMarkup(thmStr,thmWebDisplayList, bareThmList, macrosTrie, eliminateBeginEndThmPattern, 
				isContextStr, null, null);
	}
	
	/**
	 * Processes Latex input, e.g. by removing syntax used purely for display and not
	 * useful for parsing, such as \textit{ }.
	 * Also remove markups such as "\begin{theorem}"
	 * But enumerate should not always be turned off.
	 * Remove footnotes.
	 * Pass in collection to gather hypotheses, to collect hyp if \ref for precious \label shows up.
	 * And to gather labels.
	 * @param newThmSB 
	 * @param thmWebDisplayList Can be null. 
	 * @param bareThmList Can be null. 
	 * @param refThms List of referenced theorems. To be included in the context string of this thmStr.
	 * @boolean whether current input str is context string.
	 * @return Thm without the "\begin{lemma}", "\label{}", etc parts.
	 */	
	public static String removeTexMarkup(String thmStr, List<String> thmWebDisplayList,
			List<String> bareThmList, MacrosTrie macrosTrie, Pattern eliminateBeginEndThmPattern, 
			boolean isContextStr, List<String> refThms, ParseState parseState) {
		
		boolean getWebDisplayList = thmWebDisplayList == null ? false : true;
		boolean getBareThmList = bareThmList == null ? false : true;		
		
		/* replace \df{} and \emph{} with their content.<-- Can't just replace, since can result in mismatching
		 * braces. Better to remove the \emph without updating the enclosing content. Commented out May 2018*/
		/*Matcher matcher = DF_EMPH_PATTERN.matcher(thmStr);
		thmStr = matcher.replaceAll(DF_EMPH_PATTERN_REPLACEMENT);*/
		
		/*Replace macros first, since they can contain symbols such as \\ensuremath, which are to be removed.*/
		if(null != macrosTrie){
			//System.out.println("thmStr BEFORE "+thmStr);
			thmStr = macrosTrie.replaceMacrosInThmStr(thmStr);
			//System.out.println("thmStr AFTER "+thmStr);
		}
		
		Matcher matcher;
		
		if(isContextStr) {
			//only replace within context, MathJax should take begin equation.
			matcher = beginEqnPatt.matcher(thmStr);
			//if(matcher.find()) {	
				if(isContextStr) {
					//make inline math, to be smaller.
					thmStr = matcher.replaceAll("\\$");
					matcher = endEqnPatt.matcher(thmStr);
					thmStr = matcher.replaceAll("\\$");
				}/*else {
					thmStr = matcher.replaceAll("\\\\[");
					//thmStr = matcher.replaceAll("\\$");
					matcher = endEqnPatt.matcher(thmStr);
					thmStr = matcher.replaceAll("\\\\]");
					//thmStr = matcher.replaceAll("\\$");
				}*/
			//}
		}
		
		//parse to remove \footnote{...} from theorems, to not have those interjections.
		thmStr = cleanTex(thmStr);
		
		thmStr = groupReplacePatt.matcher(thmStr).replaceAll(groupReplacePattStr);
		
		// eliminate symbols such as \fm
		matcher = ELIMINATE_PATTERN.matcher(thmStr);
		thmStr = matcher.replaceAll("");
		
		//place space around < and >, so browser doesn't interpret these as opening and closing HTML tags.
		//Note browser processes text before MathJax.
		matcher = lessThanGreaterThanPatt.matcher(thmStr);
		thmStr = matcher.replaceAll(" $1 ");
		
		//System.out.println("ThmInput - eliminateBeginEndThmPattern "+eliminateBeginEndThmPattern );
		/*comment out this line if want to retain "\begin{theorem}", etc*/
		thmStr = eliminateBeginEndThmPattern.matcher(thmStr).replaceAll("");
		
		//System.out.println("Think inf loop is matching right before this, shouldn't get to this point");
		matcher = ITEM_PATTERN.matcher(thmStr);
		//replace \item with bullet points (*), but only if next place is not e.g. (ii)
		if(thmStr.contains("enumerate")) {
			//i.e. \\begin{enumerate}
			thmStr = matcher.replaceAll("");
		}else {
			thmStr = matcher.replaceAll(" -");
		}
		
		/*custom macros*/
		/*if(null != macrosMap){
			for(Entry<Pattern, String> macroEntry : macrosMap.entrySet()){
				thmStr = macroEntry.getKey().matcher(thmStr).replaceAll(macroEntry.getValue());				
			}
		}*/
		
		// containing the words inside \label and \index etc, but not the words
		// "\label", "\index",
		// for bag-of-words searching.
		//String wordsThmStr = thmStr;
		
		// replace \index{...} with its content for wordsThmStr and nothing for
		// web display version
		matcher = INDEX_PATTERN.matcher(thmStr);
		if (matcher.matches()) {
			thmStr = matcher.replaceAll("$1");
			// replace a!b!c inside \index with spaced-out versions
			thmStr = thmStr.replaceAll("!", " ");
		}
		if(getWebDisplayList || getBareThmList){
			matcher = INDEX_PATTERN.matcher(thmStr);
			thmStr = matcher.replaceAll("");
		}

		//bare thm string with no label content at the beginning.
		String bareThmStr = thmStr;
		
		//look for \ref{...} inside thmStr, find referenced thms, to be added to refThms list,
				//to be prepended to context string.				
		matcher = REF_PATTERN.matcher(thmStr);
		
		if(null != parseState && null != refThms) {
					//counter for displaying references
			int refCounter = 1;
			String tempStr = thmStr;
			while(matcher.find()) {
				//e.g. ab is name for \ref{ab}
				String labelName = matcher.group(1);
				int matchStartPos = matcher.start();
				int matchEndPos = matcher.end();
				String labelStr = "[" + refCounter++ + "]";
				tempStr = thmStr.substring(0, matchStartPos ) + labelStr + thmStr.substring(matchEndPos, thmStr.length());
						
				//check parseState for the theorem referenced.
				String thm = parseState.labelThmMap().get(labelName);
				System.out.println("!! refCounter thm" + labelName + " parseState.labelThmMap() "  + parseState.labelThmMap());
				if(null != thm) {
					refThms.add(thm);
				} 
			}
			thmStr = tempStr;
		}
		
		// Record label to be included in hypotheses later, if a thm includes \ref for this label.
		// remove label. Need to be careful, since label can occur in middle of sentence,
		// shouldn't strip away everything before label.		
		matcher = LABEL_PATTERN.matcher(thmStr);
		if (matcher.matches()) {
			//replaceAll changes the state of the matcher, so need to get the group here.
			String labelName = matcher.group(2);
			
			thmStr = matcher.replaceAll("$1$3");	
			//this is only executed when getting theorems for context parsing.
			if(getBareThmList){	
				bareThmStr = thmStr;
			}
			if(null != parseState) {
				//thmStr must be added *after* all processing has been done.
				parseState.addRefThm(labelName, thmStr);
			}
		}
		
		if(getBareThmList){
			bareThmList.add(bareThmStr);
		}
		if(getWebDisplayList){
			//thmStr same as wordsThmStr because of the thmStr = wordsThmStr; assignment
			thmWebDisplayList.add(thmStr);
		}
		//wordsThmStr is good for web display
		return thmStr;
	}
	
	/**
	 * Parse the given thm to clean up TeX, e.g. remove \footnote{...}
	 * @param thm
	 * @return
	 */
	private static String cleanTex(String thm) {
		StringBuilder sb = new StringBuilder(200);
		String footnoteStr = "\\footnote";
		int footnoteStrLen = footnoteStr.length();
		int thmLen = thm.length();
		int endIndex = thmLen - footnoteStrLen - 3;
		int i = 0;
		
		for(; i < endIndex; i++) {
			
			if(footnoteStr.equals(thm.substring(i, i+footnoteStrLen))) {
				//don't check for escapes.
				//There exist variations such as \footnote[10]{...}
				i += footnoteStrLen;
				while(i < thmLen && thm.charAt(i) != '{') {
					i++;
				}
				i++;
				int braceCounter = 1;
				char c;
				int prevChar = ' ';
				while(i < thmLen && ((c=thm.charAt(i)) != '}' || braceCounter > 0)) {
					if(prevChar != '\\') {
						if(c == '{') {
							braceCounter++;
						}else if(c == '}') {
							braceCounter--;
							if(braceCounter == 0) {
								break;
							}
						}
					}
					prevChar = c;
					i++;
				}
				
			}else {
				sb.append(thm.charAt(i));
			}			
		}
		sb.append(thm.substring(i, thmLen));
		return sb.toString();
		
	}
	
}
