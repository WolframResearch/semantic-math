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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ListMultimap;

import thmp.utils.ExtractMacros;

/**
 * Processes theorems/lemmas/defs read in from file.
 * To be used *after* ThmInput.java.
 * 
 * @author yihed
 *
 */

public class ProcessInput {
	
	//regex to mark start of tex, such as $, \[, \begin{align}
	private static final String TEX_START = "\\$.*|\\\\\\[.*|\\\\begin\\{ali[^}]*\\}";
	private static final String TEX_END = "[^$]*\\$|[^]]*\\\\\\]|\\\\end\\{ali[^}]*\\}";
	//character that would mark the end of a latex command
	private static final String COMMAND_STOP_CHAR = "\\\\|\\s+|\\$|\\^|_|-|:|\\.|;";
	private static final ImmutableMultimap<String, String> texCommandWordsMMap;
	private static final String MACROS_SRC = "src/thmp/data/texMacros.txt";
	
	//private static final Map<String, String> macrosMap = ExtractMacros.extractDefs();
	//list of thms with macros replaced
	private static List<String> macroReplacedThmList;
	
	private static final Pattern TEX_CONTENT_PATTERN;
	//set this reader when called from servlet with FileInputStream info
	private static BufferedReader macrosBReader;
	
	static{
		
		ListMultimap<String, String> texCommandWordsPreMMap = ArrayListMultimap.create();
		//put tex-words pairs into the premap
		addCommandWords(texCommandWordsPreMMap, "oplus", new String[]{"direct sum"});
		addCommandWords(texCommandWordsPreMMap, "otimes", new String[]{"direct product"});
		texCommandWordsMMap = ImmutableMultimap.copyOf(texCommandWordsPreMMap);
		//pattern to match latex symbols between "$", also include \[ \] ****
		String TEX_CONTENT = "\\$[^$]+\\$|\\$\\$[^$]+\\$\\$";
		TEX_CONTENT_PATTERN = Pattern.compile(TEX_CONTENT);
		
	}	
	
	private static void addCommandWords(ListMultimap<String, String> texCommandWordsPreMMap, String texCommand, String[] words){
		texCommandWordsPreMMap.putAll(texCommand, Arrays.asList(words));
	}
	
	public static List<String> processInput(File inputFile, boolean replaceTex, boolean replaceMacros) throws FileNotFoundException{
		BufferedReader macrosReader = getMacrosBReader();
		return processInput(inputFile, macrosReader, replaceTex, false, replaceMacros);
	}
	
	/**
	 * Get list of theorems whose macros have been replaced with expanded
	 * definitions. Should be called after processInput(...).
	 * @return
	 */
	public static List<String> get_macroReplacedThmList(){
		//Should be initialized already
		//during processInput(...).
		if(macroReplacedThmList == null){
			return Collections.emptyList();			
		}
		return macroReplacedThmList;
	}
	
	/**
	 * Sets the BufferedReader of the macro file InputStream 
	 * @param macrosReader
	 */
	public static void setResources(BufferedReader macrosReader){
		macrosBReader = macrosReader;
	}
	
	/**
	 * Reads in from file, potentially replaces latex, puts thms into a list, 
	 * @param inputFile
	 * @param replaceTex replaces the tex inside $ $ with "tex"
	 * @return List of Strings with latex such as \\cite removed.
	 * @throws FileNotFoundException
	 */
	private static List<String> processInput(File inputFile, BufferedReader macrosReader, boolean replaceTex, 
			boolean texToWords, boolean replaceMacros) throws FileNotFoundException {
		Scanner sc = new Scanner(inputFile);		
		List<String> macroReplacedThmPreList = new ArrayList<String>();
		List<String> noTexStringList = new ArrayList<String>();
		Map<String, String> macrosMap = ExtractMacros.extractDefs(macrosReader);
		
		while(sc.hasNextLine()){
			String thm = sc.nextLine();
			if(thm.matches("")) continue;			
			String noTexString = inputReplace(thm, macrosMap, replaceTex, texToWords, 
					replaceMacros, macroReplacedThmPreList);
			noTexStringList.add(noTexString);
		}
		
		//macroReplacedThmList = new ArrayList<String>(macroReplacedThmPreList);
		macroReplacedThmList = macroReplacedThmPreList;
		sc.close();
		return noTexStringList;
	}
	
	/**
	 * Get the bufferedReader for the file containing macros definitions.
	 * @return
	 */
	private static BufferedReader getMacrosBReader(){
		FileReader texSrcFileReader = null;
		try{
			texSrcFileReader = new FileReader(MACROS_SRC);
		}catch(FileNotFoundException e){
			e.printStackTrace();
		}
		return new BufferedReader(texSrcFileReader);
	}
	
	/**
	 * Redirect method for processInput.
	 * @param thmInputList
	 * @param replaceTex
	 * @return
	 */
	public static List<String> processInput(List<String> thmInputList, boolean replaceTex){			
		BufferedReader texSrcFileBReader = macrosBReader;
		if(texSrcFileBReader == null){
			//set to the default local value if not set externally
			texSrcFileBReader = getMacrosBReader();		
		}
		return processInput(thmInputList, texSrcFileBReader, replaceTex, false, false);
	}
	
	/**
	 * Processes list of inputs, for instance, strip tex etc.
	 * @param thmInputList
	 * @param replaceTex Whether to replace latex between $ $ as simply tex
	 * @param texToWords Whether to convert tex symbols to words, eg \oplus->direct sum.
	 * Places these words at beginning of the tex command, so to stay at same part with regard
	 * to "hyp/stm"
	 * @return
	 */
	/*public static List<String> processInput(List<String> thmInputList, boolean replaceTex, boolean texToWords){
		return processInput(thmInputList, replaceTex, texToWords, false);
	}*/
	
	/**
	 * Processes list of inputs, for instance, strip tex etc.
	 * @param thmInputList
	 * @param macrosReader BufferedReader containing macros.
	 * @param replaceTex Whether to replace latex between $ $ as simply tex.
	 * @param texToWords Whether to convert tex symbols to words, eg \oplus->direct sum.
	 * Places these words at beginning of the tex command, so to stay at same part with regard
	 * to "hyp/stm"
	 * @param replaceMacros whether to replace macros in tex
	 * @return
	 */
	public static List<String> processInput(List<String> thmInputList, BufferedReader macrosReader,
			boolean replaceTex, boolean texToWords, boolean replaceMacros){
		List<String> inputProcessedList = new ArrayList<String>();
		List<String> macroReplacedThmPreList = new ArrayList<String>();
		Map<String, String> macrosMap = ExtractMacros.extractDefs(macrosReader);
		
		for(int i = 0; i < thmInputList.size(); i++){
			String thm = thmInputList.get(i);
			String thmProcessed = inputReplace(thm, macrosMap, replaceTex, texToWords, 
					replaceMacros, macroReplacedThmPreList);
			inputProcessedList.add(thmProcessed);
		}
		macroReplacedThmList = macroReplacedThmPreList;
		return inputProcessedList;
	}
	
	/**
	 * Processes list of inputs, for instance, strip tex etc.
	 * @param thmInputList
	 * @param replaceTex Whether to replace latex between $ $ as simply tex.
	 * @param texToWords Whether to convert tex symbols to words, eg \oplus->direct sum.
	 * Places these words at beginning of the tex command, so to stay at same part with regard
	 * to "hyp/stm"
	 * @param replaceMacros whether to replace macros in tex
	 * @return
	 */
	public static List<String> processInput(List<String> thmInputList,
			boolean replaceTex, boolean texToWords, boolean replaceMacros){
		List<String> inputProcessedList = new ArrayList<String>();
		for(int i = 0; i < thmInputList.size(); i++){
			String thm = thmInputList.get(i);
			String thmProcessed = inputReplace(thm, null, replaceTex, texToWords, 
					replaceMacros, null);
			inputProcessedList.add(thmProcessed);
		}
		
		return inputProcessedList;
	}
	
	/**
	 * Simplifies input string by making various string replacements.
	 * @param thm
	 * @param replaceTex
	 * @param replaceMacros
	 * @return
	 */
	private static String inputReplace(String thm, Map<String, String> macrosMap, boolean replaceTex, boolean texToWords, 
			boolean replaceMacros, List<String> macroReplacedThmPreList){
		
		String noTexString = replaceThm(thm, macrosMap, replaceTex, texToWords, replaceMacros, macroReplacedThmPreList);			
		
		return noTexString;
	}

	/**
	 * @param thm
	 * @param replaceTex
	 * @param texToWords whether to replace tex symbols, eg "\oplus", with words, eg "direct sum"
	 * @return
	 */
	private static String replaceThm(String thm, Map<String, String> macrosMap, boolean replaceTex, boolean texToWords, 
			boolean replaceMacros, List<String> macroReplacedThmPreList) {
		String noTexString;
		
		//replace the latex symbols e.g. \oplus into words, e.g. direct sum, 
		if(texToWords || replaceMacros){
			if(macrosMap == null || macroReplacedThmPreList == null){
				throw new IllegalArgumentException("No macros resource provided. "
						+ "macrosMap and macroReplacedThmPreList cannot be null.");
			}
			thm = turnTexToWords(thm, macrosMap, texToWords, replaceMacros, macroReplacedThmPreList);
		}
		if(replaceTex){
			//"\\$[^$]+\\$|\\$\\$[^$]+\\$\\$"
			Matcher m = TEX_CONTENT_PATTERN.matcher(thm);
			m.replaceAll("tex");
			//thm = thm.replaceAll(TEX_CONTENT, "tex");
		}
		
		//Compile this pattern!	
		
		//replaceAll("(?:\\$[^$]+\\$)|(?:\\$\\$[^$]+\\$\\$)", "tex").
		//use capturing groups to capture text inside {\it ... } etc.
		//replace the others with non-captureing groups to speed up.
		//"\\\\begin\\{ali[^}]*\\}|\\\\end\\{ali[^}]*\\}|\\\\begin\\{equ[^}]*\\}|\\\\end\\{equ[^}]*\\}"
		String tempThm = thm.replaceAll("\\\\begin\\{ali[^}]*\\}|\\\\end\\{ali[^}]*\\}|\\\\begin\\{equ[^}]*\\}|\\\\end\\{equ[^}]*\\}", "\\$\\$")
				.replaceAll("\\\\begin\\{[^}]*\\}|\\\\end\\{[^}]*\\}|\\\\cite\\{[^}]*\\}|\\\\item"
						+ "|\\\\ref\\{[^}]*\\}", "").replaceAll("\\{\\\\it([^}]*)\\}", "$1")
				 + "\n";
		/*Pattern regex = Pattern.compile("\\{\\\\it([^}]*)\\}");
		Matcher matcher = regex.matcher(tempThm);
		tempThm = matcher.replaceAll("$1"); */
		//noTexString += tempThm;
		noTexString = tempThm;				
		//System.out.println(thm.replaceAll("(\\$[^$]+\\$)|(\\$\\$[^$]+\\$\\$)", "tex"));
		return noTexString;
	}
	
	/**
	 * Turn tex symbols to words. Walk through the input,
	 * when trigger words encountered, check for stop symbols such as space,
	 * extract command, replace with words in multimap if command contained in map.
	 * @param thm
	 * @return
	 */
	private static String turnTexToWords(String thm, Map<String, String> macrosMap, boolean texToWords, boolean replaceMacros,
			List<String> macroReplacedThmPreList){
		//if word contains the startWords		
		//StringBuilder thmBuilder = new StringBuilder(thm);
		 List<String> thmWordsList = new ArrayList<String>(Arrays.asList(thm.split("\\s+")));
		//remember index in the stringbuilder for startWord
		//insert extract tex word at index of the startword,
		//eg "There exists $\oplus ..." => "There exists direct sum $\oplus ..."
		//the start index of the current tex block, block starting with $ or \[
		//Need two separate calls, since looping indices are shifted.
		
		if(replaceMacros){
			replaceTexOrMacros(!replaceMacros, thmWordsList, macrosMap);	
			StringBuilder thmBuilder = new StringBuilder();			
			for(String word : thmWordsList){
				thmBuilder.append(word + " ");
			}
			macroReplacedThmPreList.add(thmBuilder.toString());
		}
		if(texToWords){
			replaceTexOrMacros(texToWords, thmWordsList, macrosMap);
		}
		
		StringBuilder thmBuilder = new StringBuilder();
		
		for(String word : thmWordsList){
			thmBuilder.append(word + " ");
		}		
		return thmBuilder.toString();
	}

	/**
	 * Only one of texToWords and replaceMacros should be true.
	 * @param texToWordsOrReplaceMacros whether to call texToWords (true) or replaceMacros (false).
	 * @param thmWordsList
	 */
	private static void replaceTexOrMacros(boolean texToWordsOrReplaceMacros, List<String> thmWordsList,
			Map<String, String> macrosMap) {
		int curTexStartIndex = 0;
		boolean texToWords = texToWordsOrReplaceMacros;
		boolean replaceMacros = !texToWordsOrReplaceMacros;
		
		for(int i = 0; i < thmWordsList.size(); i++){
			//thmWordsList.add(" ");
			String curWord = thmWordsList.get(i);
			//avoid cases such as $R$-module
			if(curWord.matches("\\$[^$]+\\$[^\\s]*|\\s*")){
				continue;
			}
			if(curWord.matches(TEX_START)){
				curTexStartIndex = i;
				//insert extracted words at curTexStartIndex
				//returns index of end block.
				//replace macros if replaceMacros == true.				
				if(replaceMacros){
					//pass in builder
					i = replaceMacros(thmWordsList, curTexStartIndex, macrosMap);
				}
				
				//keep replaceMacros and addToIndex separate despite similar code,
				//as addToIndex modifies the list in place
				if(texToWords){
					int texEndIndex = addToIndex(thmWordsList, curTexStartIndex, texToWords);				
					i = (texEndIndex == i && texToWords) ? i+1 : texEndIndex;	
				}
			}
		}
	}
	
	/**
	 * Auxiliary method for turnTexToWords. 
	 * Replace command in commandBuilder with expanded macro if applicable
	 * @param thmWordsList List of space-separated words of thm.
	 * @param curTexStartIndex Starting index of current tex block.
	 * @return index of end of tex block.
	 */
	private static int replaceMacros(List<String> thmWordsList, int curTexStartIndex, 
			Map<String, String> macrosMap){
		//list that's being modified with words inserted that come from tex 
		//List<String> texAddedThmWordsList = new ArrayList<String>(thmWordsList);
		int i;		
		for(i = curTexStartIndex; i < thmWordsList.size(); i++){
			String word = thmWordsList.get(i);
			
			boolean texEnded = word.matches(TEX_END);
			//check for latex commands after "\"
			int slashIndex = word.indexOf("\\");
			int wordLen = word.length();			
			//if does not contain slash
			if(slashIndex == -1){
				//if end of tex block
				if(texEnded){
					break;
				}
				continue;
			}
			//short-circuit if no more \ other than the \ in \]
			if(texEnded && slashIndex == wordLen-2){
				break;
			}
			//get word succeeding "\"
			//iterate through word
			StringBuilder commandBuilder = new StringBuilder();
			//index for storing slashes
			//List<StringBuilder> sbList = new ArrayList<StringBuilder>();
			
			for(int j = slashIndex+1; j < word.length(); j++){
				String curChar = String.valueOf(word.charAt(j));
				//System.out.println(" commandBuilder " + commandBuilder);
				//if stop char reached
				//Would using .indexOf be faster than regex matching?
				if(curChar.matches(COMMAND_STOP_CHAR)){
					//sbList.add(commandBuilder);
					commandBuilder = new StringBuilder();
					continue;
				}
				commandBuilder.append(curChar);	
				
				replaceMacros(thmWordsList, i, commandBuilder, macrosMap);				
			}		
			
			if(texEnded){
				break;
			}
		}
		return i;
	}
	
	/**
	 * Auxiliary method for turnTexToWords.
	 * @param thmWordsList List of space-separated words of thm.
	 * @param curTexStartIndex Starting index of current tex block.
	 * @param texAddedThmWordsList list that's being modified with words inserted that come from tex.
	 * @return index of end of tex block.
	 */
	private static int addToIndex(List<String> thmWordsList, int curTexStartIndex, boolean texToWords){ 
		
		//int texBlockEndIndex = curTexStartIndex;
		//list that's being modified with words inserted that come from tex 
		//List<String> texAddedThmWordsList = new ArrayList<String>(thmWordsList);
		int i;
		//index corresponding to i in the expanded texAddedThmWordsList, texAddedIndex >= i.
		//int texAddedIndex = 0;
		for(i = curTexStartIndex; i < thmWordsList.size(); i++){
			String word = thmWordsList.get(i);
			
			boolean texEnded = word.matches(TEX_END);
			//check for latex commands after "\"
			int slashIndex = word.indexOf("\\");
			int wordLen = word.length();			
			//if does not contain slash
			if(slashIndex == -1){
				//if end of tex block
				if(texEnded){
					break;
				}
				continue;
			}
			//short-circuit if no more \ other than the \ in \]
			if(texEnded && slashIndex == wordLen-2){
				break;
			}
			//get word succeeding "\"
			//iterate through word
			StringBuilder commandBuilder = new StringBuilder();
			//index for storing slashes
			List<StringBuilder> sbList = new ArrayList<StringBuilder>();
			
			for(int j = slashIndex+1; j < word.length(); j++){
				String curChar = String.valueOf(word.charAt(j));
				//System.out.println(" commandBuilder " + commandBuilder);
				//if stop char reached
				//Would using .indexOf be faster than regex matching?
				if(curChar.matches(COMMAND_STOP_CHAR)){
					sbList.add(commandBuilder);
					commandBuilder = new StringBuilder();
					continue;
				}
				commandBuilder.append(curChar);					
			}
			sbList.add(commandBuilder);
			
			if(texToWords){
				//add word for tex in thmWordsList
				i = addTexWords(thmWordsList, curTexStartIndex, i, sbList);
			}			
			
			if(texEnded){
				break;
			}
		}
		return i;
	}

	/**
	 * Replace macros with their expanded form.
	 * @param thmWordsList
	 * @param curTexStartIndex
	 * @param sbList
	 */
	private static void replaceMacros(List<String> thmWordsList, int i, StringBuilder commandBuilder,
			Map<String, String> macrosMap){
		
		//check to see if a command keyword, eg \Spec
		String command = commandBuilder.toString();
		String macroReplacement = macrosMap.get(command);
		//System.out.println(commandBuilder + " command that replaces " + macroReplacement);
		if(macroReplacement != null){			
			//System.out.println("***\\\\" + command);
			String replacedWord = thmWordsList.get(i).replaceAll("\\\\"+command, Matcher.quoteReplacement(macroReplacement));
			//System.out.println("**Macros replacedWord " + replacedWord);
			thmWordsList.remove(i);
			thmWordsList.add(i, replacedWord);
		}
		
		//System.out.println("updated thmWordsList " + thmWordsList);		
	}
	
	/**
	 * @param thmWordsList
	 * @param curTexStartIndex
	 * @param i
	 * @param sbList
	 * @return
	 */
	private static int addTexWords(List<String> thmWordsList, int curTexStartIndex, int i, List<StringBuilder> sbList) {
		for(StringBuilder sb : sbList){
			StringBuilder texWordsBuilder = new StringBuilder();
			//check to see if a command keyword, eg oplus
			Collection<String> texWordsCol = texCommandWordsMMap.get(sb.toString());
			if(!texWordsCol.isEmpty()){
				for(String texWord : texWordsCol){
					texWordsBuilder.append(texWord);
				}
				//add the tex words at the start index
				thmWordsList.add(curTexStartIndex, texWordsBuilder.toString());	
				//skip to the next word to avoid infinite loop, as just added one word
				i++;
			}
		}
		return i;
	}
	
	public static void main(String[] args) throws IOException{
		
		boolean writeToFile = false;
		
		if(writeToFile){
			File inputFile = new File("src/thmp/data/thmFile5.txt");
			Path noTex = Paths.get("src/thmp/data/noTex5.txt");
	
			List<String> noTexStringList = processInput(inputFile, false, false);
			
			Files.write(noTex, noTexStringList, Charset.forName("UTF-8"));
		}
		//System.out.println(turnTexToWords("this is $\\oplus r$ ", true, false));
		//System.out.println(turnTexToWords(" \\[\\oplus r\\] ", true, false));
		BufferedReader macrosBReader = getMacrosBReader();
		Map<String, String> macrosMap = ExtractMacros.extractDefs(macrosBReader);
		List<String> list = new ArrayList<String>();
		System.out.println(turnTexToWords(" \\[\\oplus r\\] $\\Spec R$", macrosMap, true, true, list));
		System.out.println(turnTexToWords("$$ G = \\lim_{\\lambda \\in \\Lambda} G_\\lambda $$", macrosMap, true, true, list));
		System.out.println(list);
	}

}
