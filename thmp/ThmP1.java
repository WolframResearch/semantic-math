package thmp;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;

import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import exceptions.ParseRuntimeException;
import thmp.ParseState.VariableDefinition;
import thmp.ParseToWLTree.WLCommandWrapper;
import thmp.Struct.Article;
import thmp.Struct.ChildRelation;
import thmp.Struct.ChildRelationType;
import thmp.Struct.NodeType;
import thmp.ThmP1AuxiliaryClass.ConjDisjVerbphrase;
import thmp.ThmP1AuxiliaryClass.ConjDisjVerbphrase.ConjDisjType;
import thmp.search.CollectThm;
import thmp.search.NGramSearch;
import thmp.search.ThreeGramSearch;
import thmp.search.TriggerMathThm2;
import thmp.utils.Buggy;
import thmp.utils.WordForms;
import thmp.utils.WordForms.TokenType;

public class ThmP1 {

	// should all be StructH's, since these are ent's. 
	//private static final ListMultimap<String, Struct> variableNamesMap;
	// private static HashMap<String, ArrayList<String>> entityMap =
	// Maps.entityMap;
	// map of structures, for all, disj, etc
	private static final Multimap<String, Rule> structMap;
	private static final Map<String, String> anchorMap;
	// parts of speech map, e.g. "open", "adj"
	private static final ListMultimap<String, String> posMMap;
	// fluff words, e.g. "the", "a"
	private static final Map<String, String> fluffMap;
	
	//private static final Map<String, String> mathObjMap;
	// map for composite adjectives, eg positive semidefinite
	// value is regex string to be matched
	//private static final Map<String, String> adjMap;
	private static final Map<String, Double> probMap;
	// split a sentence into parts, separated by commas, semicolons etc
	// private String[] subSentences;
	//pattern for matching negative of adjectives: "un..."
	private static final Pattern NEGATIVE_ADJECTIVE_PATTERN = Pattern.compile("^(?:un(.+)|non(.+))");	
	private static final Pattern AND_OR_PATTERN = Pattern.compile("and|or");
	private static final Pattern IS_ARE_BE_PATTERN = Pattern.compile("is|are|be");
	private static final Pattern CALLED_PATTERN = Pattern.compile("called|defined|said|denoted");
	private static final Pattern CONJ_DISJ_PATTERN1 = Pattern.compile("(?:conj|disj).*");
	//end part of latex expression word, e.g. " B)$-module"
	private static final Pattern LATEX_END_PATTER = Pattern.compile("[^$]*\\$.*");
	private static final Pattern LATEX_BEGIN_PATTERN = Pattern.compile("[^$]*\\${1,2}.*");
	private static final Pattern POSSIBLE_ADJ_PATTERN = Pattern.compile("(?:.*tive|.*wise|.*ary|.*able|.*ous)$");
	private static final Pattern ESSENTIAL_POS_PATTERN = Pattern.compile("ent|conj_ent|verb|vbs|if|symb|pro");
	private static final Pattern SINGLE_WORD_TEX_PATTERN = Pattern.compile("\\$[^$]+\\$[^\\s]*"); 
	
	//pattern for Latex expressions being possible assert's, i.e. starting/ending with $ and 
	//containing operators such as ">, ="
	private static final Pattern LATEX_ASSERT_PATTERN = Pattern.compile("\\$(?:[^$]+[=><][^$]+)\\$");
	
	// list of parts of speech, ent, verb etc <--should make immutable
	private static final List<String> posList;
	
	// fluff type, skip when adding to parsed ArrayList
	private static final String FLUFF = "Fluff";
	private static final Pattern ARTICLE_PATTERN = Pattern.compile("a|the|an");

	private static final Logger logger = LogManager.getLogger(ThmP1.class);

	// private static final File unknownWordsFile;
	private static final Path unknownWordsFile = Paths.get("src/thmp/data/unknownWords1.txt");
	
	private static final Path parsedExprFile = Paths.get("src/thmp/data/parsedExpr.txt");

	private static final List<String> unknownWords = new ArrayList<String>();
	//moving to parseState...
	private static List<ParsedPair> parsedExpr = new ArrayList<ParsedPair>();
	
	private static final ImmutableListMultimap<String, FixedPhrase> fixedPhraseMap;	
	
	private static final Map<String, Integer> twoGramMap = NGramSearch.get2GramsMap();
	private static final Map<String, Integer> threeGramMap = ThreeGramSearch.get3GramsMap();
	
	/**
	 * List of Stringified Map of parts used to build up a theorem/def etc.  
	 * Global variable, so to be able to pass to other functions.
	 * Not final, since it needs to be cleared and reassigned. 
	 */
	private static List<String> parseStructMapList = new ArrayList<String>();
	//the non-stringified version of parseStructMapList. Only used for unit testing.
	private static List<Multimap<ParseStructType, ParsedPair>> parseStructMaps 
		= new ArrayList<Multimap<ParseStructType, ParsedPair>>();
	//whether current run is part of unit testing.
	private static boolean unitTesting;
	
	private static MaxentTagger posTagger;
	
	//list of context vectors of the highest-scoring parse tree for each input.
	//will be cleared every time this list is retrieved, which should be once per 
	//parse. Default values of context vec entry is the average val over all context vecs.
	//For Nearest[] to work. *Not* final because need reassignment.
	private static final int parseContextVectorSz;
	//this is cumulative, should be cleared per parse!
	private static int[] parseContextVector; 
	/*Array of strings that should not be fused with neighboring ent's. As
	* these often act as verbs. Did it programmatically initially, but too 
	* many ent's that should be fused (e.g. ring) were not, leading to parse
	* explosions. */
	private static final String[] NO_FUSE_ENT = new String[]{"map"};
	private static final Set<String> noFuseEntSet;
	private static final Pattern PUNCTUATION_PATTERN = Pattern.compile("(\\.|;|,|!|:)$");
	
	//used in dfs, to determine whether to iterate over.
	private static final double LONG_FORM_SCORE_THRESHOLD = .7;
	//least num of iterations, even if scores are below LONG_FORM_SCORE_THRESHOLD.
	private static final int MIN_PARSE_ITERATED_COUNT = 3;
	//private static final String DASH_ENT_STRING = null;
	private static final Pattern DASH_ENT_PATTERN = Pattern.compile("\\$[^$]+\\$[^-\\s]*-[^\\s]*");
	private static final int REPARSE_UPPER_SIZE_LIMIT = 6;
	private static final Pattern CONJ_DISJ_VP_PATTERN = Pattern.compile("(?:conj|disj)_verbphrase");
	//directives used to begin latex math mode. *Must* update ALIGN_PATTERN_REPLACEMENT_STR if this is updated.
	private static final Pattern BEGIN_ALIGN_PATTERN = Pattern.compile("(?:.*(\\\\begin\\{align[*]*\\}.*))|(?:.*(\\\\begin\\{equation\\}.*))"
			+ "|(?:.*(\\\\begin\\{eqnarray[*]*\\}.*))");
	private static final Pattern END_ALIGN_PATTERN = Pattern.compile("(?:(.*\\\\end\\{align[*]*\\}).*)|(?:(.*\\\\end\\{equation\\}).*)"
			+ "|(?:.*(\\\\end\\{eqnarray[*]*\\}.*))");
	//This *must* be updated if {BEGIN/END}_ALIGN_PATTERN is updated!
	private static final String ALIGN_PATTERN_REPLACEMENT_STR = "$1$2$3";
	
	private static final boolean DEBUG = InitParseWithResources.isDEBUG();
	//Pattern used to check if word is valid.
	//Don't put \', could be in valid word
	private static final Pattern BACKSLASH_CONTAINMENT_PATTERN = 
			Pattern.compile(".*[\\\\$=\\{\\}\\[\\]()^_+%&\\./,\"\\d\\/|@><].*");
	private static final Pattern DIGITS_PATTERN = Pattern.compile("\\d+");
	
	private static final Pattern HYP_PATTERN = WordForms.get_HYP_PATTERN();
	private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
	private static final String LATEX_PLACEHOLDER_STR = " text ";
	private static final Pattern PAREN_END_PATTERN = Pattern.compile("[^)]*\\)");
	private static final Pattern PAREN_START_PATTERN = Pattern.compile("\\([^)]*");
	private static final Pattern BRACKET_END_PATTERN = Pattern.compile("[^]]*\\]");
	private static final Pattern BRACKET_START_PATTERN = Pattern.compile("\\[[^]]*");	
	private static final Pattern BRACKETS_PATTERN = Pattern.compile("\\[[^]]+\\]");
	private static final Pattern PAREN_PATTERN = Pattern.compile("\\([^)]+\\)");
	private static final double MAX_ALLOWED_ENT_PERCENTAGE = 0.8;
	private static final int MIN_PAIRS_SIZE_THRESHOLD_FOR_FLUFF = 7;
	
	static{
		
		fluffMap = Maps.BuildMaps.fluffMap;
		//mathObjMap = Maps.BuildMaps.mathObjMap;
		fixedPhraseMap = Maps.fixedPhraseMap();
		structMap = Maps.structMap();
		anchorMap = Maps.anchorMap();
		posMMap = Maps.posMMap();		
		//adjMap = Maps.adjMap;
		probMap = Maps.probMap();
		posList = Maps.posList;
	
		parseContextVectorSz = CollectThm.ThmWordsMaps.get_CONTEXT_VEC_WORDS_MAP_size();
		System.out.println("*****+++++ThmP1--parseContextVectorSz: " + parseContextVectorSz);
		parseContextVector = new int[parseContextVectorSz];
		
		noFuseEntSet = new HashSet<String>();
		for(String ent : NO_FUSE_ENT){
			noFuseEntSet.add(ent);
		}
		
	}
	
	private static void setUpPosTagger(){
		String localPathToTagger = "lib/stanford-postagger-2015-12-09/models/english-bidirectional-distsim.tagger";
		String serverPathToTagger = Maps.getServerPosTaggerPathStr();
		
		if(null != serverPathToTagger){
			posTagger = new MaxentTagger(serverPathToTagger);
		}else{
			posTagger = new MaxentTagger(localPathToTagger);
		}
	}
	
	/**
	 * Set parameter that determines whether current run is 
	 * part of a junit test run.
	 */
	public static void setUnitTestingToTrue(){
		unitTesting = true;
	}

	/**
	 * Class consisting of a parsed String and its score
	 */
	public static class ParsedPair{
		private String parsedStr;
		//score based on probabilities as defined in Maps.posMMap.
		private double score;
		//long form or WL-like expr. Useful for "under the hood"
		//can be "long" or "wl". Switch to an enum!
		private String form;
		//parsedExprSz used to group parse components together 
		//when full parse is unavailable
		//private int counter;
		//number of units in this parse, as in numUnits (leaf nodes) in Class Struct.
		//same as commandNumUnits when ParseStructType is NONE.
		private int numUnits;
		//the commandNumUnits associated to the WLCommand that gives this parsedStr.
		private int commandNumUnits;
		//the WLCommand associated to this ParsedPair.
		//Don't serialize wlCommand, will introduce infinite recursion
		//when trying to serialize this, because of circular referencing 
		//between WLCommand and WLCommandWrap.
		private transient WLCommand wlCommand;
		//the ParseStructType of this parsedStr, eg "STM", "HYP", etc.
		private ParseStructType parseStructType;
		//parse struct corresponding to the parseStructType
		//private ParseStruct parseStruct;
		
		private String stringForm;
		
		public ParsedPair(String parsedStr, double score, String form){
			this(parsedStr, score, form, true);
		}
		//toStringForm, True means final form, toString here, false otherwise
		private ParsedPair(String parsedStr, double score, String form, boolean toStringForm){
			this.parsedStr = parsedStr;
			this.score = score;
			this.form = form;
			//toString has to be within several constructor, because GSON needs the private field.
			if(toStringForm){
				this.stringForm = this.toString();
			}
		}
		
		/**
		 * @param parsedStr
		 * @param parseStruct
		 * @param score
		 * @param numUnits
		 * @param commandNumUnits
		 * @param wlCommand @Nullable null if no WLCommand was picked up.
		 */
		public ParsedPair(String parsedStr, double score, int numUnits, 
				int commandNumUnits, WLCommand wlCommand){
			this(parsedStr, score, "", false);
			this.wlCommand = wlCommand;
			this.numUnits = numUnits;
			this.commandNumUnits = commandNumUnits;
			this.stringForm = this.toString();
		}
		
		/**
		 * @return the wlCommand
		 */
		public WLCommand getWlCommand() {
			return wlCommand;
		}
		
		/**
		 * Used in case of no WLCommand parse string, then use alternate measure
		 * of span, deduced from longForm dfs.
		 * @param score
		 * @param numUnits
		 * @param commandNumUnits
		 */
		public ParsedPair(double score, int numUnits, int commandNumUnits, WLCommand wlCommand){
			this("", score, numUnits, commandNumUnits, wlCommand);
		}
		
		public ParsedPair(String parsedStr, double score, int numUnits, int commandNumUnits, WLCommand wlCommand, 
				ParseStructType type){
			this(parsedStr, score, numUnits, commandNumUnits, wlCommand);
			this.parseStructType = type;
			this.stringForm = this.toString();
		}
	
		public String parsedStr(){
			return this.parsedStr;
		}
		
		public double score(){
			return this.score;
		}

		/**
		 * Number of units covered in this parse.
		 * i.e. measures how consolidated the parse is.
		 * Different from commandNumUnits in that it doesn't count
		 * children of StructH, among other things.
		 * Lower numUnits is better.
		 */
		public int numUnits(){
			return numUnits;
		}

		/**
		 * commandNumUnits of the WLCommands involved in this parse.
		 * I.e. number of leaf nodes covered. Higher is better.
		 * If no WLCommand triggered, measures span of longform.
		 * @return
		 */
		public int commandNumUnits(){
			return commandNumUnits;
		}

		public String form(){
			return this.form;
		}
		
		/**
		 * The toString of this ParsedPair.
		 * Used by presenting gson on web form. 
		 * @return
		 */
		public String stringForm(){
			return this.stringForm;
		}
		
		@Override
		public String toString(){
			//As reference, lower numUnits are better, and higher commandNumUnits are better.
			String numUnitsString = numUnits == 0 ? "" : "  " + String.valueOf(this.numUnits);
			numUnitsString += commandNumUnits == 0 ? "" : "  " + String.valueOf(this.commandNumUnits);
			if(parseStructType != null){
				return parseStructType + " :> [" + this.parsedStr + " " + String.valueOf(score) + numUnitsString + "]";
			}else{
				return this.parsedStr + " " + String.valueOf(score) + numUnitsString;
			}
		}
	}

	/**
	 * check in 2- and 3-gram maps. Then determine the type based on 
	 * last word, e.g. "regular local ring"
	 * check 3-gram first. 
	 * @param i
	 * @param str
	 * @param pairs
	 * @param mathIndexList
	 * @return
	 */
	private static int gatherTwoThreeGram(int i, String[] str, List<Pair> pairs, List<Integer> mathIndexList){
		String curWord = str[i];
		String middleWord = str[i+1];
		String nextWord = middleWord;
		String nextWordSingular = WordForms.getSingularForm(nextWord);
		String twoGram = curWord + " " + nextWord;
		String twoGramSingular = curWord + " " + nextWordSingular;
		int newIndex = i;
		
		//Map<String, Integer> twoGramMap = NGramSearch.get2GramsMap();
		//Map<String, Integer> threeGramMap = ThreeGramSearch.get3GramsMap();
		
		if(i < str.length - 2){
			String thirdWord = str[i + 2];	
			String thirdWordSingular = WordForms.getSingularForm(thirdWord);
			String threeGram = twoGram + " " + thirdWord;
			String threeGramSingular = twoGram + " " + thirdWordSingular;
			String threeGramSingularSingular = twoGramSingular + " " + thirdWordSingular;				
			TokenType tokenType = TokenType.THREEGRAM;
			
			//don't want to combine three-grams with "of" in middle
			if(threeGramMap.containsKey(threeGram) && !middleWord.equals("of")){				
				if(addNGramToPairs(pairs, mathIndexList, thirdWord, threeGram, tokenType)){
					newIndex = i+2;
				}
			}else if(threeGramMap.containsKey(threeGramSingular)){				
				if(addNGramToPairs(pairs, mathIndexList, thirdWordSingular, threeGramSingular, tokenType)){
					newIndex = i+2;
				}
			}else if(threeGramMap.containsKey(threeGramSingularSingular)){				
				if(addNGramToPairs(pairs, mathIndexList, thirdWordSingular, threeGramSingularSingular, tokenType)){
					newIndex = i+2;
				}
			}	
		}
		
		if(newIndex == i && i < str.length - 1){
			TokenType tokenType = TokenType.TWOGRAM;
			if(twoGramMap.containsKey(twoGram)){			
				if(addNGramToPairs(pairs, mathIndexList, nextWord, twoGram, tokenType)){
					newIndex = i+1;
				}	
			}else if(twoGramMap.containsKey(twoGramSingular)){
				if(addNGramToPairs(pairs, mathIndexList, nextWordSingular, twoGramSingular, tokenType)){
					newIndex = i+1;
				}
			}
		}
		return newIndex;
	}
	
	/**
	 * Add n gram to pairs list
	 * @param pairsList
	 * @param mathIndexList
	 * @param lastWord
	 * @param nGram
	 * @return Whether n-gram was added to pairs
	 */
	private static boolean addNGramToPairs(List<Pair> pairsList, List<Integer> mathIndexList, String lastWord,
			String nGram, TokenType tokenType) {
		//don't want 2/3 grams to end with a preposition, which can break parsing down the line
		if(posMMap.containsKey(lastWord) && posMMap.get(lastWord).get(0).equals("pre")){
			return false;
		}
		//System.out.println("NGRAM: " + nGram);
		//System.out.println(Arrays.toString(Thread.currentThread().getStackTrace()));
		String pos;
		List<String> posList = posMMap.get(lastWord);		
		if(!posList.isEmpty()){
			pos = posList.get(0);
		}else{
			//try to find part of speech algorithmically
			pos = computeNGramPos(nGram, tokenType);
		}
		Pair phrasePair = new Pair(nGram, pos);								
		pairsList.add(phrasePair);
		if(pos.matches("ent")){ 
			mathIndexList.add(pairsList.size() - 1);
		}
		return true;
	}
	
	/**
	 * Attemps to guess part of speech of n-grams.
	 * @return
	 */
	private static String computeNGramPos(String nGram, TokenType tokenType){
		
		int nGramLen = nGram.length();
		String[] nGramAr = nGram.split("\\s+");
		//use enum instead!
		String pos = "";
		String firstWord = nGramAr[0];
		int firstWordLen = firstWord.length();
		
		if(tokenType.equals(TokenType.TWOGRAM)){			
			
			List<String> posList = posMMap.get(firstWord);
			boolean isFirstWordAdverb = !posList.isEmpty() ? posList.get(0).equals("adverb") : 
				(firstWord.substring(firstWordLen-2, firstWordLen).equals("ly") ? true : false);
			//adverb past-participle pair, e.g "finitely generated"
			if (isFirstWordAdverb && nGram.substring(nGramLen - 2, nGramLen).equals("ed")) {
				pos = "adj";				
			}
		}
		return pos;
	}
	
	/**
	 * Add additional extra pos to given pair.
	 * @param pair
	 * @param posList
	 */
	private static void addExtraPosToPair(Pair pair, List<String> posList){
		
		if(posList.isEmpty()){
			return;
		}
		//don't add "noun" if "ent" already added, since these fulfill equivalent
		//roles in practice.		
		boolean entAdded = posList.get(0).equals("ent");
		//start from 1, since first pos is already added.
		for(int k = 1; k < posList.size(); k++){
			String pos = posList.get(k);
			if(pos.equals("ent")){
				entAdded = true;
			}else if(pos.equals("noun") && entAdded){
				continue;
			}
			pair.addExtraPos(pos);
		}
	}
	
	private static void setParseStateFromPunctuation(String punctuation, ParseState parseState){
		
		ParseStruct curParseStruct = parseState.getCurParseStruct();
		
		if(curParseStruct != null){
			ParseStruct parentParseStruct = curParseStruct.parentParseStruct();
			if(parentParseStruct != null){
				switch(punctuation){
					case ".":					
						parseState.setCurParseStruct(parentParseStruct);
						break;
					case "!":
						parseState.setCurParseStruct(parentParseStruct);
						break;
					default:
				}
			}
		}
	}
	
	/**
	 * Tokenizes input by splitting into comma-separated strings
	 * @param sentence string to be tokenized
	 * @param parseState current state of the parse.
	 * @return List of Struct's
	 */
	public static ParseState tokenize(String sentence, ParseState parseState){
		
		if(WordForms.getWhiteEmptySpacePattern().matcher(sentence).find()){
			return parseState;
		}
		parseState.setCurrentInputStr(sentence);
		
		//check for punctuation, set parseState to reflect punctuation.
		int sentenceLen = sentence.length();
		String lastChar = String.valueOf(sentence.charAt(sentenceLen-1));
		Matcher punctuationMatcher = PUNCTUATION_PATTERN.matcher(lastChar);
		
		if(punctuationMatcher.find()){
			sentence = sentence.substring(0, sentenceLen-1);
			parseState.setPunctuation(punctuationMatcher.group(1));
			/*if(parseState != null){
				setParseStateFromPunctuation(punctuationMatcher.group(1), parseState);
			}*/
		}
		
		// list of indices of "proper" math objects, e.g. "field", but not e.g. "pair"
		List<Integer> mathIndexList = new ArrayList<Integer>();
		// list of indices of anchor words, e.g. "of"
		List<Integer> anchorList = new ArrayList<Integer>();

		// list of each word with their initial type, adj, noun,
		List<Pair> pairs = new ArrayList<Pair>();
		//Only split on white space.
		String[] strAr = WHITESPACE_PATTERN.split(sentence);
		
		//String with latex substituted with the word "text". For use
		//by the posTagger. Since "text" is likely provide the right 
		//tag for words that surround it.
		StringBuilder noTexSB = new StringBuilder(50);
		
		//\begin{enumerate} should be the first word in the sentence, based on how they are built
		//in the preprocessor.
		if(strAr[0].equals("\\begin{enumerate}")){
			TexParseUtils.parseEnumerate(strAr, parseState);
			//parseEnumerate takes care of rest of parsing.
			return parseState;
		}
		
		//if contains nothing other than symbols and ents, probably spurious latex expression,
		//don't parse in that case.
		boolean containsOnlySymbEnt = true;
		strloop: for (int i = 0; i < strAr.length; i++) {

			String curWord = strAr[i];
			//System.out.println("at beginning: curWord: " + curWord);
			
			//sometimes some blank space falls through, in which case just skip.
			if(WordForms.getWhiteEmptySpacePattern().matcher(curWord).find()){
				continue;
			}			
			Matcher negativeAdjMatcher;
			
			//change to enum!
			String type = "ent"; 
			int wordlen = strAr[i].length();
			//this needs to be evaluated at each iteration.
			int strArLength = strAr.length;
			
			/** detect latex expressions, set their pos as "ent" for now **/
			
			//latex expressions that start with \begin{align} or \being{equation}
			Matcher mathModeMatcher;
			Matcher mathModeEndMatcher;
			if((mathModeMatcher = BEGIN_ALIGN_PATTERN.matcher(curWord)).matches()){
				
				StringBuilder latexExprSB = new StringBuilder(mathModeMatcher.replaceAll(ALIGN_PATTERN_REPLACEMENT_STR) + " ");
				
				while(i++ < strArLength-1){
					curWord = strAr[i];
					
					//not checking for nested \begin{align} and \begin{equation}
					if((mathModeEndMatcher = END_ALIGN_PATTERN.matcher(curWord)).matches()){
						latexExprSB.append(" " + mathModeEndMatcher.replaceAll("$1$2"));
						break;
					}else{
						latexExprSB.append(" " + curWord);
					}					
				}
				
				Pair pair = new Pair(latexExprSB.toString(), type);
				pairs.add(pair);
				mathIndexList.add(pairs.size() - 1);
				noTexSB.append(LATEX_PLACEHOLDER_STR);
				//if(true) throw new IllegalStateException("pair "+pair.word());
				continue;
			}
			
			// latex expressions that start with '$'
			if (LATEX_BEGIN_PATTERN.matcher(curWord).matches()) {
				
				String latexExpr = curWord;
				
				//not a single-word latex expression, i.e. $R$-module
				if (i < strArLength - 1 && !SINGLE_WORD_TEX_PATTERN.matcher(curWord).matches( )
						&& (curWord.charAt(wordlen - 1) != '$' || wordlen == 2 || wordlen == 1)) {
					
					i++;
					curWord = strAr[i];
					
					if (i < strArLength - 1 && curWord.equals("")) {
						curWord = strAr[++i];
					//}
					//else if (curWord.matches("[^$]*\\$.*")) {
						//latexExpr += " " + curWord;
						//i++;
					} else {						
						while (i < strArLength && curWord.length() > 0
								&& !LATEX_END_PATTER.matcher(curWord).find()){//curWord.charAt(curWord.length() - 1) != '$') {
							latexExpr += " " + curWord;
							i++;

							if (i == strArLength)
								break;

							curWord = i < strArLength - 1 && strAr[i].equals("") ? strAr[++i] : strAr[i];
							
						}
					}
					//add the end of the latex expression, only if it's the last part (i.e. $)
					//or matching "[^$]*\\$.*"
					if (i < strArLength) {
						//int tempWordlen = strAr[i].length();						
						///if (tempWordlen > 0 && strAr[i].charAt(tempWordlen - 1) == '$')
							//latexExpr += " " + strAr[i];
						if(LATEX_END_PATTER.matcher(strAr[i]).find()){
							latexExpr += " " + strAr[i];
						}
					}
					/*
					if (latexExpr.matches("[^=]+=.+|[^\\\\cong]+\\\\cong.+")
							&& (i+1 == stringLength || i+1 < stringLength && !posMap.get(str[i+1] ).matches("verb|vbs")) ) {
						type = "assert";
					} */
				} else if (curWord.matches("\\$[^$]{1,2}\\$")) {
					type = "symb";
				}
				// go with the pos of the last word, e.g. $k$-algebra
				else if (DASH_ENT_PATTERN.matcher(curWord).find()) { //\\$[^$]+\\$[^-\\s]*-[^\\s]*
					
					String[] curWordAr = curWord.split("-");
					String tempWord = curWordAr[curWordAr.length - 1];
					List<String> tempPosList = posMMap.get(tempWord);
					if (!tempPosList.isEmpty()) {
						type = tempPosList.get(0);
					}					
				}
				
				Pair pair = new Pair(latexExpr, type);
				pairs.add(pair);
				
				if (type.equals("ent")){
					mathIndexList.add(pairs.size() - 1);
				}
				//if(true) throw new IllegalStateException("pair "+pair.word());
				noTexSB.append(LATEX_PLACEHOLDER_STR);
				continue strloop;
			}
			
			noTexSB.append(" " + curWord + " ");
			// check for trigger words of fixed phrases, e.g. "with this said",
			// "all but finitely many", as well as 2- or 3-grams.
			if (i < strAr.length - 1) {
				
				String potentialTrigger = curWord + " " + strAr[i + 1];
				if (fixedPhraseMap.containsKey(potentialTrigger)) {
					//primordial pair to be set if valid fixed phrase is found.
					//Ugly solution, but this avoids creating a whole new class
					//with just an in and a Pair as members.
					Pair emptyPair = new Pair(null, null);
					int numWordsDown = findFixedPhrase(potentialTrigger, i, strAr, emptyPair);
					if(null != emptyPair.pos()){
						pairs.add(emptyPair);			
						if(emptyPair.pos().equals("ent")) mathIndexList.add(pairs.size() - 1);						
						i += numWordsDown - 1;						
						continue strloop;
					}
				}				
				int newIndex = gatherTwoThreeGram(i, strAr, pairs, mathIndexList);
				//a two or three gram was picked up
				if(newIndex > i){ 					
					i = newIndex;
					continue;				
				}				
			}
			
			
			String[] singularForms = WordForms.getSingularForms(curWord);
			
			String singular = singularForms[0];
			String singular2 = singularForms[1]; // ending in "es"
			String singular3 = singularForms[2]; // ending in "ies"
			
			List<String> posList = posMMap.get(curWord);
			List<String> singularPosList = posMMap.get(singular);
						
			String wordPos = null;
					/*posList.isEmpty() 
					? (singularPosList.isEmpty() ? null : posMMap.get(singular).get(0)) 
					: posMMap.get(curWord).get(0);	 */
					
			if(posList.isEmpty()){
				if(!singularPosList.isEmpty()){
					wordPos = posMMap.get(singular).get(0);
				}
			}else{
				wordPos = posMMap.get(curWord).get(0);
			}
			
			//convert "noun" to "ent" if it's the most probable pos,
			//most often noun does no damage, but could prevent spanning parse
			//or commands from being picked up.
			if(wordPos != null && wordPos.equals("noun")){
				wordPos = "ent";
			}

			if (null != wordPos && wordPos.equals("ent")) { 
				
				curWord = posList.isEmpty() ? singular : curWord;
				String tempWord = curWord;
				int pairsSize = pairs.size();
				int k = 1;
				
				//check for previous words, fuse entities if necessary.
				//should be superceded by using the two-gram map above! <--this way can be more deliberate 
				//and flexible than using n-grams, e.g. if composite math noun, eg "finite field"
				while (i - k > -1 && posMMap.containsKey(strAr[i - k] + " " + tempWord)
						&& posMMap.get(strAr[i - k] + " " + tempWord).get(0).equals("ent")) {
					
					// remove previous pair from pairs if it has new match
					// pairs.size should be > 0, ie previous word should be
					// classified already
					if (pairs.size() > 0 && pairs.get(pairsSize - 1).word().equals(strAr[i - k])) {

						// remove from mathIndexList if already counted
						List<String> preWordPosList = posMMap.get(pairs.get(pairsSize - 1).word());
						if (!preWordPosList.isEmpty() && preWordPosList.get(0).equals("ent")) {
							
							mathIndexList.remove(mathIndexList.size() - 1);
						}
						pairs.remove(pairsSize - 1);
					}

					tempWord = strAr[i - k] + " " + tempWord;
					curWord = strAr[i - k] + " " + curWord;
					k++;
				}

				// if previous Pair is also an ent, fuse them, but only if current ent
				// does not belong to noFuseEntSet e.g. "map"			
				pairsSize = pairs.size();
				if (pairs.size() > 0 && pairs.get(pairsSize - 1).pos().matches("ent")) {
					boolean fuseEnt = true;
					String prevWord = pairs.get(pairsSize-1).word();
					//Also check previous ent, ensure its posList does not contain "verb". Do this 
					//instead of including "adj" in above check, to avoid parse explosion.
					//e.g. "f maps prime ideals to prime ideals"
					if(noFuseEntSet.contains(curWord) || noFuseEntSet.contains(prevWord)){
						fuseEnt = false;
						//check for next word, if pre such as "of", then do fuse.
						//e.g. "ring map of finite type."
						if(i < strAr.length - 1){
							String nextWord = strAr[i+1];

							List<String> nextWordPosList = posMMap.get(nextWord);
							if(!nextWordPosList.isEmpty() && nextWordPosList.get(0).equals("pre")
									//or a latx symbol name
									|| nextWord.charAt(0) == '$'){
								fuseEnt = true;
							}
						}						
					}					
					
					if(fuseEnt){
						pairs.get(pairsSize - 1).set_word(pairs.get(pairsSize - 1).word() + " " + curWord);
						continue;
					}					
				}
				
				Pair pair = new Pair(curWord, "ent");
				addExtraPosToPair(pair, posList);
				pairs.add(pair);
				mathIndexList.add(pairs.size() - 1);
			}
			else if (anchorMap.containsKey(curWord)) {
				Pair pair = new Pair(curWord, "anchor");
				// anchorList.add(pairIndex);
				pairs.add(pair);

				int pairsSize = pairs.size();
				anchorList.add(pairsSize - 1);
			}
			// check part of speech (pos)
			else if (posMMap.containsKey(curWord)){ //|| posMMap.containsKey(curWord.toLowerCase())) {
				/*if(posMMap.containsKey(curWord.toLowerCase())){
					curWord = curWord.toLowerCase();
				}*/
				// composite words, such as "for all".
				StringBuilder tempSB = new StringBuilder(curWord);
				String pos;
				List<String> tempPosList = posMMap.get(curWord);
				String tempPos = tempPosList.get(0);
				
				if(curWord.equals("for")){
					//turn "a" into "any" in e.g. "for a field F over Q"	
					if(i < strAr.length-1 && strAr[i+1].equals("a")){
						strAr[i+1] = "any";
					}
				}
				
				Pair emptyPair = new Pair(null, null);
				//keep going until all words in an n-gram are gathered
				while (tempPos.matches("[^_]*_COMP|[^_]*_comp") && i < strAr.length - 1) {					
					
					//if there's a next word, see if these words form triggers for fixed phrases
					if(i < strAr.length - 2){
						String potentialTrigger = strAr[i+1] + " " + strAr[i+2];
						int numWordsDown = findFixedPhrase(potentialTrigger, i+1, strAr,
								emptyPair);
						if(null != emptyPair.pos()){
							//-2 because we were looking ahead and fed i+1 to findFixedPhrase().
							//
							i += numWordsDown - 2;
							break;
						}
					}
					
					tempSB = tempSB.append(" ").append(strAr[i+1]);
					if (!posMMap.containsKey(tempSB.toString())) {
						break;
					}
					
					tempPos = posMMap.get(tempSB.toString()).get(0);
					i++;
				}
				
				Pair pair;
				String temp = tempSB.toString();
				if (posMMap.containsKey(temp)) {
					posList = posMMap.get(temp);
					pos = posList.get(0).split("_")[0];
					pair = new Pair(temp, pos);
					//add any additional pos to pair if applicable.
					addExtraPosToPair(pair, posList);
				} else {
					//guaranteed to contain curWord at this point.
					posList = posMMap.get(curWord);
					pos = posList.get(0).split("_")[0];
					pair = new Pair(curWord, pos);
					addExtraPosToPair(pair, posList);
				}				
				pair = fuseAdverbAdj(pairs, curWord, pair);				
				pairs.add(pair);
				
				if(null != emptyPair.pos()){
					emptyPair = fuseAdverbAdj(pairs, curWord, emptyPair);	
					pairs.add(emptyPair);
					if(emptyPair.pos().equals("ent")) mathIndexList.add(pairs.size() - 1);
					System.out.println("emptyPair added! pairs: " + pairs);
				}				
			}
			// if plural form
			else if (posMMap.containsKey(singular)){
				addSingularWordToPairsList(mathIndexList, pairs, singular);
			}
			else if (posMMap.containsKey(singular2)){
				addSingularWordToPairsList(mathIndexList, pairs, singular2);
			}
			else if (posMMap.containsKey(singular3)){
				addSingularWordToPairsList(mathIndexList, pairs, singular3);
			}
			// classify words with dashes; eg sesqui-linear
			else if (curWord.split("-").length > 1) {
				String[] splitWords = curWord.split("-");
				
				String lastTerm = splitWords[splitWords.length - 1];
				System.out.println("singular" + singular + " curWord: " + curWord + " splitWords: " + Arrays.deepToString(splitWords));
				String[] splitAr;
				String lastTermS1 = singular == null ? "" : (splitAr = singular.split("-"))[splitAr.length - 1];
				String lastTermS2 = singular2 == null ? "" : (splitAr = singular2.split("-"))[splitAr.length - 1];
				String lastTermS3 = singular3 == null ? "" : (splitAr = singular3.split("-"))[splitAr.length - 1];

				String searchKey = "";
				String s = "";
				if (posMMap.containsKey(lastTerm)){
					searchKey = lastTerm;
					s = curWord;
				}else if (posMMap.containsKey(lastTermS1)){
					searchKey = lastTermS1;
					s = singular;
				}else if (posMMap.containsKey(lastTermS2)){
					searchKey = lastTermS2;
					s = singular2;
				}else if (posMMap.containsKey(lastTermS3)){
					searchKey = lastTermS3;
					s = singular3;
				}
				
				if (!searchKey.equals("")) {

					Pair pair = new Pair(curWord, posMMap.get(searchKey).get(0).split("_")[0]);
					pairs.add(pair);
				} // if lastTerm is entity, eg A-module
				
				/*String entName = isTokenEnt(lastTerm) ? lastTerm
					: (isTokenEnt(lastTermS1) ? lastTermS1 
					: (isTokenEnt(lastTermS2) ? lastTermS2
					: (isTokenEnt(lastTermS3) ? lastTermS3 : null)));*/
				
				if (isTokenEnt(lastTerm) || isTokenEnt(lastTermS1)
						|| isTokenEnt(lastTermS2) || isTokenEnt(lastTermS3)) {
					//use this if want to preserve original plurality
					/*Pair pair = new Pair(curWord, "ent"); */
					Pair pair = new Pair(s, "ent");
					pairs.add(pair);
					mathIndexList.add(pairs.size() - 1);
				}
			}
			// check for verbs ending in 'es' & 's'
			else if (wordlen > 0 && curWord.charAt(wordlen - 1) == 's'
					&& posMMap.containsKey(strAr[i].substring(0, wordlen - 1))
					&& posMMap.get(strAr[i].substring(0, wordlen - 1)).get(0).equals("verb")) {

				Pair pair = new Pair(strAr[i], "verb");
				pairs.add(pair);
			} else if (wordlen > 1 && curWord.charAt(wordlen - 1) == 's' && strAr[i].charAt(strAr[i].length() - 2) == 'e'
					&& posMMap.containsKey(strAr[i].substring(0, wordlen - 2))
					&& posMMap.get(strAr[i].substring(0, wordlen - 2)).get(0).equals("verb")) {
				Pair pair = new Pair(strAr[i], "verb");
				pairs.add(pair);

			}
			// adverbs that end with -ly that haven't been screened off before
			else if (wordlen > 1 && curWord.substring(wordlen - 2, wordlen).equals("ly")) {
				Pair pair = new Pair(strAr[i], "adverb");
				pairs.add(pair);
			}
			// participles. Need special list for words such as
			// "given". 
			else if (wordlen > 1 && curWord.substring(wordlen - 2, wordlen).equals("ed")
					&& (posMMap.containsKey(strAr[i].substring(0, wordlen - 2))
							&& posMMap.get(strAr[i].substring(0, wordlen - 2)).contains("verb")
							|| posMMap.containsKey(strAr[i].substring(0, wordlen - 1))
									&& posMMap.get(strAr[i].substring(0, wordlen - 1)).contains("verb"))) 
			{
				// if next word is "by", then
				String curPos = "parti";
				int pairsSize = pairs.size();
				// if next word is "by"
				if (strAr.length > i + 1 && pairsSize > 1) {
					String nextWord = strAr[i + 1];
					if(nextWord.equals("by")){
						curPos = "partiby";
						curWord = curWord + " by";
						String prevPos = pairs.get(pairsSize - 1).pos();
						// if previous word is a verb, combine to form verb
						if (pairsSize > 0 && (prevPos.equals("verb")
								|| prevPos.equals("vbs"))) {
							curWord = pairs.get(pairsSize - 1).word() + " " + curWord;
							curPos = "auxpass"; // passive auxiliary, eg "is determined by"
							pairs.remove(pairsSize - 1);
							pairsSize--;
						}
						i++;
					}else{
						List<String> curPosList = posMMap.get(strAr[i+1]);
						if(curPosList.size() > 0){
							String nextPos = curPosList.get(0);
							if(nextPos.equals("pre")){
								curPos = "adj";
							}
						}
					}
				}
				// previous word is "is, are", then group with previous word to
				// verb
				// e.g. "is called"
				if (pairsSize > 0) {
					Pair prevPair = pairs.get(pairsSize - 1);
					if(IS_ARE_BE_PATTERN.matcher(prevPair.word()).find()){
						//if next word is a preposition, e.g. "is named by"
						if(strAr.length > i + 1 && !posMMap.get(strAr[i+1]).isEmpty()){
							String nextPos = posMMap.get(strAr[i+1]).get(0);
						
							if(nextPos.equals("pre")){
								curWord = pairs.get(pairsSize - 1).word() + " " + curWord;
								pairs.remove(pairsSize - 1);								
								curPos = "auxpass";
							}
							//otherwise likely used as adjective, e.g. "is connected"
							//<--but can't really distinguish from "is called".
							else if(nextPos.equals("if_COMP") || nextPos.equals("if")){
								curPos = "adj";
								//throw new IllegalStateException(nextPos);
							}
						}else if(i == strAr.length - 1){
							curPos = "adj";
						}
					}
					//e.g. "summable indexed family", note the next word cannot be "by"
					//at this point.
					else if(prevPair.pos().equals("adj")){
						curPos = "adj";
					}// if previous word is adj, "finitely presented"
					else if (prevPair.pos().equals("adverb")) {

						curWord = pairs.get(pairsSize - 1).word() + " " + curWord;
						pairs.remove(pairsSize - 1);
						curPos = "adj";
					}
				}				
				// if next word is entity, then adj
				else if (strAr.length > i + 1 && posMMap.containsKey(strAr[i+1]) && 
						posMMap.get(strAr[i+1]).get(0).equals("ent")) {

					// combine with adverb if previous one is adverb
					if (pairsSize > 0 && pairs.get(pairsSize - 1).pos().equals("adverb")) {
						curWord = pairs.get(pairsSize - 1).word() + " " + curWord;
						pairs.remove(pairsSize - 1);
					}
					curPos = "adj";
				}

				Pair pair = new Pair(curWord, curPos);
				pairs.add(pair);
			}//this seems too fine grained! 
			else if (wordlen > 3 && curWord.substring(wordlen - 3, wordlen).equals("ing")
					&& (posMMap.containsKey(curWord.substring(0, wordlen - 3))
							&& posMMap.get(curWord.substring(0, wordlen - 3)).get(0).matches("verb|vbs")
							// verbs ending in "e"
							|| (posMMap.containsKey(curWord.substring(0, wordlen - 3) + 'e')
									&& posMMap.get(curWord.substring(0, wordlen - 3) + 'e').get(0).matches("verb|vbs")))) {
				
				String curType = "gerund";
				if (i < strAr.length - 1 && posMMap.containsKey(strAr[i + 1]) && posMMap.get(strAr[i + 1]).contains("pre")) {
					// eg "consisting of" functions as pre
					curWord = curWord + " " + strAr[++i];
					curType = "pre";
				} else if (i < strAr.length - 1 &&
						posMMap.containsKey(strAr[i + 1]) && posMMap.get(strAr[i + 1]).get(0).matches("ent|noun")){
					// eg "vanishing locus" functions as amod: adjectivial
					// modifier
					//curWord = curWord + " " + str[++i];
					//curType = "amod";
					curType = "adj"; //adj, so can be grouped together with ent's later
				}//e.g. "the following are equivalent"
				else if (i > 0 && ARTICLE_PATTERN.matcher(strAr[i - 1]).find()){
					curType = "ent";
				}
				
				int pairsSz = pairs.size();
				//e.g. $f$ is inclusion-preserving
				if(i > 1 && pairsSz > 1//e.g. inclusion preserving
						 && pairs.get(pairsSz-1).pos().matches("noun|\\d+|ent")
						 && pairs.get(pairsSz-2).pos().matches("verb|vbs")
						 //&& posMMap.containsKey(strAr[i - 1]) && posMMap.get(strAr[i - 1]).get(0).matches("ent|noun")
						 ){
					Pair prevPair = pairs.get(pairsSz-1);
					prevPair.set_pos("adj");
					prevPair.set_word(prevPair.word() + " " + curWord);
				}else{				
					Pair pair = new Pair(curWord, curType);
					pairs.add(pair);
				}
			}
			else if (curWord.matches("[a-zA-Z]")) {
				// variable/symbols
				Pair pair = new Pair(strAr[i], "symb");
				pairs.add(pair);
			}
			// Get numbers. Incorporate written-out numbers, eg "two"
			else if (curWord.matches("\\d+")) {
				Pair pair = new Pair(strAr[i], "num");
				pairs.add(pair);
			}//negative adjective word, not that unusual.
			else if((negativeAdjMatcher = NEGATIVE_ADJECTIVE_PATTERN.matcher(curWord)).find()){
				
				String curAdjWord = null == negativeAdjMatcher.group(1)
						? negativeAdjMatcher.group(2) : negativeAdjMatcher.group(1);
				
				posList = posMMap.get(curAdjWord);
				
				if (!posList.isEmpty()) {
					String pos = posList.get(0).split("_")[0];
					Pair pair = new Pair(curWord, pos);
					//add any additional pos to pair if applicable.
					addExtraPosToPair(pair, posList);
					pair = fuseAdverbAdj(pairs, curWord, pair);					
					pairs.add(pair);
				}else{
					pairs.add(new Pair(curWord, ""));
				}
				
			}
			else if (!curWord.matches("\\s+")) { // try to minimize this case.
				
				System.out.println("word not in dictionary: " + curWord);
				pairs.add(new Pair(curWord, ""));
				
				if(parseState.writeUnknownWordsToFileBool() && isValidWord(curWord)){
				// collect & write unknown words to file
					unknownWords.add(curWord);
				}
			} else { 
				// must be blank space at this point, so curWord doesn't count
				continue;
			}

			int pairsSize = pairs.size();
			//case only meaningful if pairsSize>2
			if (pairsSize > 2) {
				Pair pair = pairs.get(pairsSize - 1);

				// combine "no" and "not" with verbs
				if (pair.pos().matches("verb|vbs")) {
					String word = pairs.get(pairsSize - 2).word();
					if (pairs.size() > 1 && (word.matches("not|no")
							|| pairs.get(pairsSize - 2).pos().matches("not"))) {
						//String newWord = pair.word().matches("is|are") ? "not" : "not " + pair.word();
						String newWord = word + " " + pair.word();
						pair.set_word(newWord);
						pairs.remove(pairsSize - 2);
					}

					if (i + 1 < strAr.length && strAr[i + 1].matches("not|no")) {
						//String newWord = pair.word().matches("is|are") ? "not" : "not " + pair.word();
						String newWord = strAr[i+1] + " " + pair.word();
						pair.set_word(newWord);
						i++;
					}
				}
			}
		}
		
		int symbEntCount = 0;
		for (Pair pair : pairs) {
			String pos = pair.pos();
			if(pair.pos().equals("ent") || pos.equals("symb")
					|| pos.equals("")){
				symbEntCount++;				
			}else{
				containsOnlySymbEnt = false;
			}
		}
		double pairs_sz = pairs.size();
		//return if no meaningful parse could be extracted. This happens for 
		//if all expressions are latex. Heuristic for detecting sentences that are just
		//conglomerates of latex expressions, but that are not enclosed in "$" or "\begin{equation}", etc
		if(containsOnlySymbEnt 
				|| (symbEntCount/pairs_sz > MAX_ALLOWED_ENT_PERCENTAGE && pairs_sz > MIN_PAIRS_SIZE_THRESHOLD_FOR_FLUFF)
				|| symbEntCount > 12){
			//set trivial structlist, so that previous structlist doesn't get parsed again.
			parseState.setTokenList(new ArrayList<Struct>());
			return parseState;
		}
		
		// If phrase isn't in dictionary, ie has type "", then use probMap to
		// postulate type probabilistically.
		int pairsLen = pairs.size();
		Pair curpair;
		
		double bestCurProb = 0;
		String prevType = "", nextType = "", tempCurType = "", tempPrevType = "", tempNextType = "", bestCurType = "";

		int posListSz = posList.size();
		//System.out.println("FIRST pairs: " + pairs);
		for (int index = 0; index < pairsLen; index++) {
			//int pairsLen = pairs.size();
			curpair = pairs.get(index);
			String curWord = curpair.word();
			
			//first check it's not spurious tex expression.
			if(!isValidWord(curWord)){
				continue;
			}
			
			String curPos = curpair.pos();
			int curWordLen = curWord.length();
			
			//don't already have this pos in dictionary
			boolean unknownWordPos = false;			

			if (curPos.equals("")) {
				unknownWordPos = true;
				prevType = index > 0 ? pairs.get(index - 1).pos() : "";
				nextType = index < pairsLen - 1 ? pairs.get(index + 1).pos() : "";

				prevType = prevType.equals("anchor") ? "pre" : prevType;
				nextType = nextType.equals("anchor") ? "pre" : nextType;

				// iterate through list of types, ent, verb etc, to make best guess
				for (int k = 0; k < posListSz; k++) {
					tempCurType = posList.get(k);

					// FIRST/LAST indicate positions
					tempPrevType = index > 0 ? prevType + "_" + tempCurType : tempCurType + "_FIRST";
					tempNextType = index < pairsLen - 1 ? tempCurType + "_" + nextType : tempCurType + "_LAST";

					if(0 == index && !probMap.containsKey(tempPrevType)){
						tempPrevType = "FIRST";
					}
					if(pairsLen - 1 == index && !probMap.containsKey(tempNextType)){
						tempNextType = "LAST";
					}
					
					if (probMap.get(tempPrevType) != null && probMap.get(tempNextType) != null) {
						double score = probMap.get(tempPrevType) * probMap.get(tempNextType);
						if (score > bestCurProb) {
							bestCurProb = score;
							bestCurType = tempCurType;
						}
						//check for patterns
						if(curWordLen > 3){
							//e.g. "generic"
							if(curWord.charAt(curWordLen-1) == 'c' && curWord.charAt(curWordLen-2) == 'i' 
									&& tempCurType.equals("adj")){
								break;
							}
						}
					}
				}
				if(bestCurProb > 0.5){
					curpair.set_pos(bestCurType);
					if (bestCurType.equals("ent")){
						mathIndexList.add(index);
					}
				}
				
			}
			//if still no pos, try to guess part of speech based on endings 
			if(curpair.pos().equals("")){
				if(POSSIBLE_ADJ_PATTERN.matcher(curWord).matches()){
					curpair.set_pos("adj");
				}				
			}
			//if yet still no pos found, use the Stanford NLP tagger, calls to which 
			//does incur overhead.			
			//Only try to find pos for words that don't contain "\"
			if(curpair.pos().equals("")){
				//tag the whole sentence to find the most accurate tag, since the tagger
				//uses contextual tags to maximize entropy.
				if(null == posTagger){
					setUpPosTagger();
				}
				
				//use sentence with latex substituted.
				String sentenceToTag = noTexSB.toString();
				
				String taggedSentence = posTagger.tagString(sentenceToTag);
				//must find word in tagged sentence, which looks like 
				//e.g. "a_DT field_NN is_VBZ a_DT ring_NN".
				//better way to fish the pos out?
				String wordToTag = " " + curWord + "_";
				int wordStartIndex = taggedSentence.indexOf(wordToTag);
				//in case it's the first word.
				if(-1 == wordStartIndex){
					wordToTag = curWord + "_";
					wordStartIndex = taggedSentence.indexOf(wordToTag);
				}
				
				//necessary check in case noTexSB dropped the word.
				//Sometimes the tagger strips away parts of words, such as "[prime" -> "prime".
				if(-1 == wordStartIndex && logger.getLevel().equals(Level.INFO)){
					String msg = "wordToTag: " + wordToTag + ", not found in taggedSentence: "
							+ taggedSentence;
					//throw new IllegalStateException();
					logger.info(msg);					
				}else{				
					int posIndex = wordStartIndex + wordToTag.length();
					//pos is 2 characters long.
					String wordPos = taggedSentence.substring(posIndex, posIndex+2);
					
					String pos = WordForms.getPosFromTagger(wordPos);
					curpair.set_pos(pos);
					if (pos.equals("ent")){
						mathIndexList.add(index);
					}
					//System.out.println("!!pairs " + pairs);
					System.out.println("Using posTagger to tag word: " +  curWord + " with pos: " + pos);
				}
			}
			
			String pos = curpair.pos();
			if(unknownWordPos && !pos.equals("")){
				parseState.addUnknownWordPosToMap(curWord, pos);
				System.out.println("Added " + curWord + " to posmap with pos: " + pos);
			}
		}
		
		//turn "symb" pos into "ent" if followed by "pre",
		//e.g. "given $x$ in a compact space"
		//pairsLen = pairs.size();
		for (int index = 0; index < pairsLen; index++) {
			
			curpair = pairs.get(index);
			String curPos = curpair.pos();
				
			if(curPos.equals("symb") &&index < pairsLen-1 
					&& pairs.get(index+1).pos().equals("pre") ){
				curpair.set_pos("ent");
				mathIndexList.add(index);
			}
		}
		
		// map of math entities, has mathObj + ppt's
		List<StructH<HashMap<String, String>>> mathEntList = new ArrayList<StructH<HashMap<String, String>>>();

		// combine adj with math ent's
		for (int j = 0; j < mathIndexList.size(); j++) {
			
			int index = mathIndexList.get(j);
			Pair mathPair = pairs.get(index);
			String mathObjName = mathPair.word();
			String entPosStr = String.valueOf(j);
			mathPair.set_pos(entPosStr);
			
			StructH<HashMap<String, String>> tempStructH = new StructH<HashMap<String, String>>("ent");
			
			if(LATEX_ASSERT_PATTERN.matcher(mathObjName).matches()){
				tempStructH.setLatexStructToTrue();
			}
			
			List<String> posList = posMMap.get(mathObjName);
			
			boolean entAdded = false;
			if(!posList.isEmpty()){
				entAdded = posList.get(0).equals("ent");
			}
			
			//pos will be added later
			boolean fuseSymbEnt = true;
			
			//start from 1, as extraPosList only contains *additional* pos
			for(int l = 1; l < posList.size(); l++){
				String pos = posList.get(l);
				if(pos.equals("ent")){
					entAdded = true;
				}
				else if(pos.equals("noun") && entAdded){
					continue;
				}
				else if(pos.equals("verb")){
					fuseSymbEnt = false;
				}
				tempStructH.addExtraPos(pos);
			}
			
			HashMap<String, String> tempMap = new HashMap<String, String>();
			if(null == mathObjName){
				throw new IllegalArgumentException( pairs.toString());
			}
			tempMap.put("name", mathObjName);
			
			// if next pair is also ent, and is latex expression
			if (j < mathIndexList.size() - 1 && mathIndexList.get(j + 1) == index + 1) {
				Pair nextPair = pairs.get(index + 1);
				String name = nextPair.word();
				if (name.contains("$")) {
					tempMap.put("tex", name);
					nextPair.set_pos(entPosStr);
					mathIndexList.remove(j + 1);
				}
			}

			// look right one place in pairs, if symbol found, add it to
			// namesMap
			// if it's the given name for an ent.
			int pairsSize = pairs.size();
			if (index + 1 < pairsSize && pairs.get(index + 1).pos().equals("symb")
					//don't fuse if e.g. mathObjName is "map"
					&& !noFuseEntSet.contains(mathObjName)) {
				//the word following symbol is "and"
				if (index + 2 == pairsSize 
						|| AND_OR_PATTERN.matcher(pairs.get(index+2).pos()).find()){
								
				}else{
					pairs.get(index + 1).set_pos(entPosStr);
					String givenName = pairs.get(index + 1).word();
					tempMap.put("called", givenName);
					// do not overwrite previously named symbol
					
				}
			} /*
				 * else if ((index + 2 < pairsSize && pairs.get(index +
				 * 2).pos().equals("symb"))) { pairs.get(index +
				 * 2).set_pos(String.valueOf(j)); String givenName =
				 * pairs.get(index + 2).word(); tempMap.put("called",
				 * givenName); namesMap.put(givenName, tempStructH); }
				 */
			// look left one place, combine symb_ent, but only if curWord
			// doesn't also have other pos, e.g. verb, as in "$f$ maps a to b".
			if (index > 0 && pairs.get(index - 1).pos().equals("symb")) {
				
				if(fuseSymbEnt){
					pairs.get(index - 1).set_pos(entPosStr);
					String givenName = pairs.get(index - 1).word();
					// combine the symbol with ent's name together
					tempMap.put("name", givenName + " " + tempMap.get("name"));
				}

			}
			// combine nouns with ent's right after, ie noun_ent
			else if (index > 0 && pairs.get(index - 1).pos().equals("noun")) {
				pairs.get(index - 1).set_pos(entPosStr);
				String prevNoun = pairs.get(index - 1).word();
				tempMap.put("name", prevNoun + " " + tempMap.get("name"));
			}
			// and combine ent_noun together
			else if (index + 1 < pairsSize && pairs.get(index + 1).pos().equals("noun")) {
				pairs.get(index + 1).set_pos(entPosStr);
				String prevNoun = pairs.get(index + 1).word();
				tempMap.put("name", tempMap.get("name") + " " + prevNoun);
			}

			// look to left and right
			int k = 1;
			// combine multiple adjectives into entities
			// ...get more than adj ... multi-word descriptions
			// set the pos as the current index in mathEntList
			// adjectives or determiners	
			
			while (index - k > -1 && pairs.get(index - k).pos().matches("adj|det|num")) {
				Pair curPair = pairs.get(index - k);
				String curWord = curPair.word();
				String curPos = curPair.pos();
				
				//combine adverb-adj pair (not redundant with prior calls to fuseAdjAdverbPair())
				if(curPos.equals("adj") && index-k-1 > -1){
					Pair prevPair = pairs.get(index-k-1);
					if(prevPair.pos().equals("adverb")){
						curWord = prevPair.word() + " " + curWord;
						prevPair.set_pos(entPosStr);
					}
				}
					
					// look for composite adj (two for now)
					/*if (index - k - 1 > -1 && !posMMap.get(curWord).isEmpty() && posMMap.get(curWord).get(0).matches("adj")) {
						// if composite adj
						if (pairs.get(index - k - 1).word().matches(adjMap.get(curWord))) {
							curWord = pairs.get(index - k - 1).word() + " " + curWord;
							// mark pos field to indicate entity
							pairs.get(index - k).set_pos(String.valueOf(j));
							k++;
						}
					}*/
				//System.out.println("^^^^^^^^^^put word " + curWord);
				tempMap.put(curWord, "ppt");
				// mark the pos field in those absorbed pairs as index in
				// mathEntList
				curPair.set_pos(entPosStr);
				k++;
			}
			
			// combine multiple adj connected by "and/or"
			// hacky way: check if index-k-2 is a verb, only combine adj's if
			// not. Obsolete because of adj_ent rule later
			/*if (index - k - 2 > -1 && pairs.get(index - k).pos().matches("or|and")
					&& pairs.get(index - k - 1).pos().equals("adj")) {
				List<String> tempPosList = posMMap.get(pairs.get(index - k - 2).word());
				if (!tempPosList.isEmpty() && !tempPosList.get(0).matches("verb|vbs|verb_comp|vbs_comp")) {
					// set pos() of or/and to the right index
					pairs.get(index - k).set_pos(entPosStr);
					String curWord = pairs.get(index - k - 1).word();
					tempMap.put(curWord, "ppt");
					pairs.get(index - k - 1).set_pos(String.valueOf(j));

				}
			}*/

			// look forwards
			k = 1;
			while (index + k < pairs.size() && pairs.get(index + k).pos().matches("adj|num")) {
				//but don't add if next word is of type "pre"
				//e.g. "ideal maximal among ..."
				if(index + k + 1 < pairs.size() && pairs.get(index+k+1).pos().equals("pre")){					
					break;
				}
				tempMap.put(pairs.get(index + k).word(), "ppt");
				pairs.get(index + k).set_pos(entPosStr);
				k++;
			}

			tempStructH.set_struct(tempMap);
			mathEntList.add(tempStructH);
		}
		
		System.out.println("PAIRS! " + pairs);
		// combine anchors into entities. Such as "of," "has"
		for (int j = anchorList.size() - 1; j > -1; j--) {
			int index = anchorList.get(j);
			String anchor = pairs.get(index).word();

			// combine entities, like in case of "of"
			switch (anchor) {
			case "of":
				// the expression before this anchor is an entity
				if (index > 0 && index + 1 < pairs.size()) {

					Pair nextPair = pairs.get(index + 1);
					Pair prevPair = pairs.get(index - 1);
					String nextPos = nextPair.pos();
					String prevPos = prevPair.pos();
					// should handle later with grammar rules in mx! commented out 11/14/16.	
					//<--commented back in on 11/24, makes a difference in e.g.
					//"I is a formal integer combination of curves". And this gives more customization,
					//also helps mitigate parse explosions, by associating the token after "of" with 
					//the token immediately before "of", rather than allowing combinations with any prior token.
					if (prevPos.matches("\\d+") ) { 
						// ent of ent
						int mathObjIndex = Integer.valueOf(prevPair.pos());
						StructH<HashMap<String, String>> tempStruct = mathEntList.get(mathObjIndex);
						Struct childStruct = null;
						
						//check whether next token is an ent, or an article followed by an ent.
						//in which case fuse.
						boolean nextPairEnt = false;
						String entPos = "";
						if(nextPos.matches("\\d+")){
							nextPairEnt = true;
							childStruct = mathEntList.get(Integer.valueOf(nextPos));
							entPos = nextPos;
						}else if(index + 2 < pairs.size() && nextPos.equals("art")){
							String nextNextPos = pairs.get(index+2).pos();
							if(nextNextPos.matches("\\d+")){
								nextPairEnt = true;
								entPos = nextNextPos;
								childStruct = mathEntList.get(Integer.valueOf(nextNextPos));
							}							
							
						}
						if(nextPairEnt){							
	
							pairs.get(index).set_pos(entPos);
													
							// set to null instead of removing, to keep indices
							// right.
							if (entPos != prevPair.pos()){
								mathEntList.set(Integer.valueOf(entPos), null);
							}							
							tempStruct.add_child(childStruct, new ChildRelation("of"));
							//if(true) throw new IllegalStateException("of");
						}else if(nextPos.equals("symb")){
							
							pairs.get(index).set_pos(prevPair.pos());
							childStruct = new StructA<String, String>(nextPair.word(), NodeType.STR, "", NodeType.STR, "symb");
							tempStruct.add_child(childStruct, new ChildRelation("of"));
							nextPair.set_pos(prevPair.pos());							
						}
					} // "noun of ent".
					else 
					if (prevPair.pos().equals("noun") && nextPos.matches("\\d+")) {
						int mathObjIndex = Integer.valueOf(nextPos);
						// Combine the something into the ent
						StructH<HashMap<String, String>> tempStruct = mathEntList.get(mathObjIndex);

						String entName = tempStruct.struct().get("name");
						tempStruct.struct().put("name", prevPair.word() + " of " + entName);

						pairs.get(index).set_pos(nextPos);
						prevPair.set_pos(nextPos);

					} // special case: "of form"

					else { 
						// set anchor to its normal part of speech word, like
						// "of" to pre
						pairs.get(index).set_pos(posMMap.get(anchor).get(0));
					}

				} 
				else {
					// if the previous token is not an ent.
					// Set anchor to its normal part of speech word, like "of"
					// to "pre"
					pairs.get(index).set_pos(posMMap.get(anchor).get(0));
				}				
				break;
			}
		}

		// list of structs to return
		List<Struct> structList = new ArrayList<Struct>();

		String prevPos = "-1";
		// use anchors ("of") to gather terms together into entities
		int pairsSz = pairs.size();
		
		//turn the List pairs into a List of Struct's.
		for (int i = 0; i < pairsSz; i++) {
			Pair curPair = pairs.get(i);
			String curPos = curPair.pos();
			String curWord = curPair.word();
			
			//can pos ever be null? <--could be set to null prior
			if (curPos == null)
				continue;
			//if has pos "ent"
			if (DIGITS_PATTERN.matcher(curPos).matches()) {

				if (curPos.equals(prevPos)) {
					continue;
				}
				StructH<HashMap<String, String>> curStruct = mathEntList.get(Integer.valueOf(curPos));
				
				Set<String> posList = curPair.extraPosSet();
				
				if(posList != null){
					//start from 1, as extraPosList only contains *additional* pos
					//besides the most probable pos.
					for(String pos : posList){
						 if(pos.equals("noun")){
							continue;
						}
						curStruct.addExtraPos(pos);
					}
				}
				//could have been set to null
				if (curStruct != null) {
					structList.add(curStruct);
				}

				prevPos = curPos;
				
			} else {
				String prev2 = "";
				//check if article
				if(curPos.equals("art")){
					
					if(i < pairsSz-1){
						//combine into subsequent ent
						Pair nextPair = pairs.get(i+1);
						if(DIGITS_PATTERN.matcher(nextPair.pos()).matches()){
							StructH<HashMap<String, String>> nextStruct = mathEntList.get(Integer.valueOf(nextPair.pos()));						
							if (nextStruct != null) {
								nextStruct.setArticle(Article.getArticle(curWord));
							}
						}
					}
					continue;
				}else if(curPos.equals("symb")){
					//if has pos "symb", then try to look for definitions that stand for this symbol.
					Struct definingStruct = parseState.getVariableDefinition(curWord);
					//System.out.println("getGlobalVariableNamesMMap: " + parseState.getGlobalVariableNamesMMap().toString());
					if(null != definingStruct){
						prev2 = definingStruct.nameStr();
						
					}
				}
				
				// current word hasn't classified into an ent, make structA
				int structListSize = structList.size();
				
				// combine adverbs into the prior verb if applicable,
				//but only if subsequent word is not of type "parti", 
				//e.g. "$K$ is separably generated over $k$"
				/*if (curPair.pos().equals("adverb")) {
				//bad because this adverb could be modifying subsequent words, e.g. "converges uniformly"
					if (structListSize > 1 && structList.get(structListSize - 1).type().matches("verb|vbs")) {
						//only if the subsequent word is not an adjective, or no subsequent word
						String nextPairPos;
						if(i == pairsSz - 1 
								|| !(nextPairPos = pairs.get(i+1).pos()).equals("adj")
								&& !nextPairPos.equals("parti")){
							
							StructA<?, ?> verbStruct = (StructA<?, ?>) structList.get(structListSize - 1);
							// verbStruct should not have prev2, set prev2 type
							// to String <--improve this.
							verbStruct.set_prev2(curPair.word());
							continue;
						}
					}
				}*/

				//String curWord = curPair.word();

				// leaf of prev2 is empty string ""
				StructA<String, String> newStruct = 
						new StructA<String, String>(curWord, NodeType.STR, prev2, NodeType.STR, curPair.pos());
				//add extra pos from curPair's extraPosList. In the other case when extraPos is not added, 
				//that's when the primary pos fits well already.
				Set<String> posSet = curPair.extraPosSet();
				if(posSet != null){
					for(String pos : posSet){
						newStruct.addExtraPos(pos);
					}
				}
				
				//combine adverb-adjective together
				if (curPair.pos().equals("adj")) {
					if (structListSize > 0 && structList.get(structListSize - 1).type().equals("adverb")) {
						
						//if(structListSize == 1 || !structList.get(structListSize - 2).type().matches("verb|vbs")){
						Struct adverbStruct = structList.get(structListSize - 1);
						String newContent = adverbStruct.prev1().toString() + " " + curWord;
						newStruct.set_prev1(newContent);
							//newStruct.set_prev2(adverbStruct);						
							// remove the adverb Struct
						structList.remove(structListSize - 1);
						//}
					}
				}
				// combine det into nouns and verbs, change
				else if (curPair.pos().equals("noun") && structListSize > 0
						&& structList.get(structListSize - 1).type().equals("det")) {
					String det = (String) structList.get(structListSize - 1).prev1();
					newStruct.set_prev2(det);
					structList.remove(structListSize - 1);
				}

				structList.add(newStruct);

			}

			// ...try multiple entities instead of first one found
			// add to list of properties
			// if adjective, group with nearest entity
			// after going through entities in sentence first
			// also templating, "is" is a big hint word
			// add as property

		}
		System.out.println("\n^^^^structList: " + structList);
		
		parseState.setTokenList(structList);
		return parseState;
	}

	/**
	 * @param mathIndexList
	 * @param pairs
	 * @param singular3
	 * @return
	 */
	private static void addSingularWordToPairsList(List<Integer> mathIndexList, List<Pair> pairs,
			String singular3) {
		List<String> posList;
		posList = posMMap.get(singular3);				
		String pos = posList.get(0).split("_")[0];
		Pair pair = new Pair(singular3, pos);				
		pairs.add(pair);	
		addExtraPosToPair(pair, posList);
		if("ent".equals(pos)){
			mathIndexList.add(pairs.size() - 1);
		}
	}
	
	/**
	 * Check if curWord is valid English word, and not spurious latex expression.
	 * @param curWord
	 * @return
	 */
	private static boolean isValidWord(String curWord) {
		return !BACKSLASH_CONTAINMENT_PATTERN.matcher(curWord).find();
	}

	/**
	 * 
	 * @param potentialTrigger
	 * @param i Starting index of this fixed phrase.
	 * @param strAr
	 * @param emptyPair
	 * @return
	 */
	private static int findFixedPhrase(String potentialTrigger, int i, String[] strAr,
			Pair emptyPair) {
		// do first two words instead of 1, e.g. "for all" instead
		// of just "for"
		// since compound words contain at least 2 words
		List<FixedPhrase> fixedPhraseList = fixedPhraseMap.get(potentialTrigger);
		int numWordsDown = 0;
		//fixedPhrases should actually reside in a trie instead a Multimap!
		Iterator<FixedPhrase> fixedPhraseListIter = fixedPhraseList.iterator();
		while (fixedPhraseListIter.hasNext()) {
			FixedPhrase fixedPhrase = fixedPhraseListIter.next();
			numWordsDown = fixedPhrase.numWordsDown();
			
			StringBuilder joinedSB = new StringBuilder(20);
			
			int k = i;
			while (k < strAr.length && k - i < numWordsDown) {
				
				joinedSB.append(strAr[k]).append(" ");
				k++;
			}
			
			String joinedTrimmed = joinedSB.toString().trim();
			Matcher matcher = fixedPhrase.phrasePattern().matcher(joinedTrimmed);
			if (matcher.matches()) {
				String pos = fixedPhrase.pos();
				if(!pos.equals("fluff")){				
					emptyPair.set_word(joinedTrimmed);
					emptyPair.set_pos(pos);
				}
			}
		}
		return numWordsDown;
	}

	/**
	 * Determines whether the token string represents a math
	 * entity.
	 * @return
	 */
	private static boolean isTokenEnt(String word){
		List<String> lastTermPosList = posMMap.get(word);
		if(!lastTermPosList.isEmpty()){
			//iterate through list?
			if(lastTermPosList.get(0).equals("ent")){
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Fuses adverb-adj pair, e.g. "fantastically awesome".
	 * @param pairs
	 * @param curWord
	 * @param pair
	 * @return
	 */
	private static Pair fuseAdverbAdj(List<Pair> pairs, String curWord, Pair pair) {
		//boolean addIndex;
		int pairsSize = pairs.size();
		
		// if adverb-adj pair, eg "clearly good"
		// And combine adj_adj to adj, eg right exact
		List<String> posList = posMMap.get(curWord);
		if (pairs.size() > 0 && !posList.isEmpty() && posList.get(0).equals("adj")) {
			if (pairs.get(pairsSize - 1).pos().matches("adverb|adj")) {
				curWord = pairs.get(pairsSize - 1).word() + " " + curWord;
				// remove previous Pair
				pairs.remove(pairsSize - 1);
				pair = new Pair(curWord, "adj");				
				//addIndex = false;
			}			
		}
		return pair;
	}
	
	/**
	 * Takes in list of entities/ppt, and connectives parse using
	 * structMap, and obtain sentence structures with Chart parser.
	 * @param recentEnt, ent used to keep track of recent MathObj/Ent, for pronoun
	 * reference assignment.
	 * @return parseState
	 */
	public static ParseState parse(ParseState parseState) {
		return parse(parseState, false);
	}
	
	/**
	 * Counts total num units contained in all structs in list.
	 * @param structList
	 * @return
	 */
	private static int totalNumUnitsInStructList(List<Struct> structList){
		int totalNumUnits = 0;
		
		for(int i = 0; i < structList.size(); i++){
			totalNumUnits += structList.get(i).numUnits();
		}
		return totalNumUnits;
	}
	
	/**
	 * Takes in list of entities/ppt, and connectives parse using
	 * structMap, and obtain sentence structures with Chart parser.
	 * @param recentEnt, ent used to keep track of recent MathObj/Ent, for pronoun
	 * reference assignment.
	 * @param isReparse, whether this is a second time parse (only happen if certain 
	 * conditions satisfied)
	 */
	public static ParseState parse(ParseState parseState, boolean isReparse) {
		/*System.out.println("**********");
		System.out.println("Pos of open " + posMMap.get("open"));
		System.out.println("**********");*/
		//int parseContextVectorSz = TriggerMathThm2.keywordDictSize();
		//this is cumulative, should be cleared per parse! Move this back
		//to initializer after debugging!
		//parseContextVector = new int[parseContextVectorSz];
		
		List<Struct> inputStructList = parseState.getTokenList();		
		int inputStructListSize = inputStructList.size();
		
		if(null == inputStructList || 0 == inputStructListSize){
			return parseState;
		}
		
		//If contains nothing other than symbols and ents, probably spurious latex expression,
		//skip in that case.
		boolean containsOnlySymbEnt = true;
		for(Struct struct : inputStructList){
			if(!struct.type().equals("ent") && !struct.type().equals("symb")){
				containsOnlySymbEnt = false;
				break;
			}
		}
		if(containsOnlySymbEnt){ 
			return parseState;
		}
		
		Struct recentEnt = parseState.getRecentEnt();	
		// shouldn't be 0 to start with?!
		if (inputStructListSize == 0)
			return parseState;

		// first Struct
		Struct firstEnt = null;
		boolean foundFirstEnt = false;
		// track the most recent entity for use for pronouns
		//Struct recentEnt = null;
		// index for recentEnt, ensure we don't count later structs
		// for pronouns
		int recentEntIndex = -1;

		// A matrix of List's. Dimenions of first two Lists are same: square
		// matrix
		List<List<StructList>> mx = new ArrayList<List<StructList>>(inputStructListSize);

		for (int l = 0; l < inputStructListSize; l++) {
			List<StructList> tempList = new ArrayList<StructList>();

			for (int i = 0; i < inputStructListSize; i++) {
				// initialize Lists now so no need to repeatedly check if null
				// later
				// but does use more space! Need to revisist.
				tempList.add(new StructList());
			}

			mx.add(tempList);
			/*
			 * mx.add(new ArrayList<Struct>(len));
			 * 
			 * for (int i = 0; i < len; i++) { // add len number of null's
			 * mx.get(l).get(i) .add(null); }
			 */
		}
		
		// which row to start at for the next column
		int nextColStartRow = -1;
		outerloop: for (int j = 0; j < inputStructListSize; j++) {

			// fill in diagonal elements
			// ArrayList<Struct> diagonalStruct = new ArrayList<Struct>();

			// mx.get(j).set(j, diagonalStruct);
			Struct diagonalStruct = inputStructList.get(j);
			diagonalStruct.set_structList(mx.get(j).get(j));
			
			mx.get(j).get(j).add(diagonalStruct);
			
			String structName;
			if(diagonalStruct.isStructA()){
				//prev1 should be string, since this is a leaf struct.
				structName = diagonalStruct.prev1().toString();
			}else{
				structName = diagonalStruct.struct().get("name");
			}
			
			//create additional structs on the diagonal if extra pos present.
			Set<String> extraPosSet = diagonalStruct.extraPosSet();
			//System.out.println("@@@@@@ diagonalStruct " + diagonalStruct + "|||" +extraPosSet);
			if(extraPosSet != null){
				
				for(String pos : extraPosSet){
					Struct newStruct;
					if(pos.equals("ent")){
						StructH<HashMap<String, String>> tempStruct = new StructH<HashMap<String, String>>("ent");
						HashMap<String, String> tempMap = new HashMap<String, String>();
						tempMap.put("name", structName);
						tempStruct.set_struct(tempMap);
						newStruct = tempStruct;
					}else{
						newStruct = new StructA<String, String>(structName, NodeType.STR, "", NodeType.STR, pos);					
					}
					//System.out.println("NEW STRUCT ADDED " + newStruct + " " + extraPosSet.size());
					mx.get(j).get(j).add(newStruct);
					newStruct.set_structList(mx.get(j).get(j));
				}
			}
			// mx.get(j).set(j, inputList.get(j));

			// startRow should actually *always* be < j
			int i = j - 1;
			if (nextColStartRow != -1 && nextColStartRow < j) {
				if (nextColStartRow == 0) {
					nextColStartRow = -1;
					continue;
				}
				i = nextColStartRow - 1;
				nextColStartRow = -1;
			}
			innerloop: for (; i >= 0; i--) {
				for (int k = j - 1; k >= i; k--) {
					/* pairs are at mx positions (i,k), and (k+1,j) */

					StructList structList1 = mx.get(i).get(k);
					StructList structList2 = mx.get(k + 1).get(j);
					//System.out.println("++++ "+i + " " + k + " structList1: " + structList1);
					//System.out.println("---- "+(k+1) + " " + j + " structList2: " + structList2);
					// Struct struct1 = mx.get(i).get(k);
					// Struct struct2 = mx.get(k + 1).get(j);

					if (structList1 == null || structList2 == null || structList1.size() == 0
							|| structList2.size() == 0) {						
						continue;
					}
					//System.out.println("=====++++ i, j pairs " + (i) + " " + k + ", " + " col " + (k+1) + " " +j );
					//if(j==7) System.out.println(structList2);
					// need to refactor to make methods more modular!

					Iterator<Struct> structList1Iter = structList1.structList().iterator();
					List<Struct> struct2List = structList2.structList();
					
					//System.out.println("!structList1: " + structList1.structList() + " " + structList1.structList().size());
					//System.out.println("!structList2: " + structList2.structList() + " " + structList2.structList().size());
					
					while (structList1Iter.hasNext()) {
						
						Struct struct1 = structList1Iter.next();
						//System.out.println("STRUCT1 " + struct1);						
						Iterator<Struct> structList2Iter = struct2List.iterator();
						
						while (structList2Iter.hasNext()) {
							Struct struct2 = structList2Iter.next();
							//System.out.println("...with STRUCT2 " + struct2);
							
							// combine/reduce types, like or_ppt, for_ent,
							// in_ent
							String type1 = struct1.type();
							String type2 = struct2.type();
							
							// for types such as conj_verbphrase
							String[] split1 = type1.split("_");
							//this causes conj_ent to be counted as ent, so should
							//*not* use "ent" type to determine whether StructH or not!
							
							if (split1.length > 1 && CONJ_DISJ_PATTERN1.matcher(split1[0]).find()) {
								type1 = split1[1];
							}

							String[] split2 = type2.split("_");

							if (split2.length > 1 && CONJ_DISJ_PATTERN1.matcher(split2[0]).find()) {
								type2 = split2[1];
							}

							// if recentEntIndex < j, it was deliberately
							// skipped in a previous pair when it was the 2nd struct.
							if (!struct1.isStructA()
									&& (!(recentEntIndex < j) || !foundFirstEnt)) {
								if (!foundFirstEnt) {
									firstEnt = struct1;
									foundFirstEnt = true;
								}
								recentEnt = struct1;
								recentEntIndex = j;
							}

							// if pronoun, now refers to most recent ent
							// should refer to ent that's the object of previous
							// assertion,
							// sentence, or "complete" phrase.
							// Note that different pronouns might need diferent rules.
							if (type1.equals("pro") && struct1.prev1NodeType().equals(NodeType.STR)
									&& ((String) struct1.prev1()).matches("it|they") && struct1.prev2() != null
									&& struct1.prev2().equals("")) {
								if (recentEnt != null && recentEntIndex < j) {
									String tempName = recentEnt.struct().get("name");
									
									String name = tempName != null ? tempName : "";
									struct1.set_prev2(name);
								}
							}
							
							if (!struct2.isStructA() && !type1.matches("verb|pre")) {
								if (!foundFirstEnt) {
									firstEnt = struct1;
									foundFirstEnt = true;
								}
								recentEnt = struct2;
								recentEntIndex = j;
							}

							// look up combined in struct table, like or_ent
							// get value as name for new hash table, table with
							// prev field
							// new type? entity, with extra ppt
							// name: or. combined ex: or_adj (returns ent),
							// or_ent (ent)
							String combined = type1 + "_" + type2;
							
							// handle pattern ent_of_symb
							//should *not* use "ent" type to determine whether StructH or not!
							//since conj_ent is counted as ent. Also these checks are terrible.
							if (!struct1.isStructA() && type2.matches("pre") && struct2.prev1() != null
									&& struct2.prev1().toString().matches("of") && j + 1 < inputStructListSize
									&& inputStructList.get(j + 1).type().equals("symb")) {
								
								List<Struct> childrenList = struct1.children();
							
								//System.out.println("STRUCT1 " + struct1 + " CHILDREN " + struct1.children());
								
								boolean childAdded = false;
								//iterate backwards, want the latest-added child that fits
								int childrenListSize = childrenList.size();
								for(int p = childrenListSize - 1; p > -1; p--){
									Struct child = childrenList.get(p);
									if(child.type().equals("ent") && !child.isStructA()){
										child.add_child(inputStructList.get(j + 1), new ChildRelation("of"));
										inputStructList.get(j + 1).set_parentStruct(child);
										childAdded = true;
										break;
									}
								}
								if(!childAdded){
									struct1.add_child(inputStructList.get(j + 1), new ChildRelation("of"));
									inputStructList.get(j + 1).set_parentStruct(struct1);
								}
								
								mx.get(i).get(j + 1).add(struct1);
								
								nextColStartRow = i;
							} else if (combined.equals("pro_verb")) {
								if (struct1.prev1().equals("we") && struct2.prev1().equals("say")) {
									struct1.set_type(FLUFF);
									// mx.get(i).set(j, struct1);
									mx.get(i).get(j).add(struct1);
								}
							}

							if (combined.equals("adj_ent") && !struct2.isStructA()) {
								// update struct
								
									// should be StructH
								Struct newStruct = struct2.copy();
								String newPpt = "";
								if (struct1.type().equals("conj_adj")) {
									if (struct1.prev1NodeType().isTypeStruct()) {
										newPpt += ((Struct) struct1.prev1()).prev1();
									}
									if (struct1.prev2NodeType().isTypeStruct()) {
										newPpt += ((Struct) struct1.prev2()).prev1();
									}
								} else {
									
									newPpt += struct1.prev1();
								}
								newStruct.struct().put(newPpt, "ppt");
								// mx.get(i).set(j, newStruct);								
								mx.get(i).get(j).add(newStruct);
								continue innerloop;
								
							}
							//posessive pronouns with ent
							else if(combined.equals("poss_ent")){
								Struct newStruct = struct2.copy();
								//add reference to previous ent that this poss most likely refers to
								//to struct2. Put new entry in struct2.struct, with key "poss".
								//put the new field "possesivePrev" in Struct
								if(foundFirstEnt && recentEntIndex < j){
									newStruct.set_possessivePrev(recentEnt);									
								}
								
								mx.get(i).get(j).add(newStruct);
								continue innerloop;
							}
						
							// handle "is called" -- "verb_parti", also "is
							// defined"
							// for definitions
							else if (combined.matches("verb_parti") && IS_ARE_BE_PATTERN.matcher(struct1.prev1().toString()).find()
									&& CALLED_PATTERN.matcher(struct2.prev1().toString()).find()) {
								String called = "";
								int l = j + 1;
								// whether definition has started, ie "is called
								// subgroup of G"
								boolean defStarted = false;
								while (l < inputStructListSize) {

									Struct nextStruct = inputStructList.get(l);
									if (!nextStruct.type().matches("pre|prep|be|verb")) {
										defStarted = true;

										if (nextStruct.isStructA()) {
											called += nextStruct.prev1();
										} else {
											called += nextStruct.struct().get("name");
										}

										if (l != inputStructListSize - 1)
											called += " ";

									}
									// reached end of newly defined word, now
									// more usual sentence
									// ie move from "subgroup" to "of G"
									else if (defStarted) {
										// remove last added space
										called = called.trim();
										break;
									}
									l++;
								}

								// Record the symbol/given name associated to an
								// ent, needed if referring to it later.
								if (firstEnt != null) {
									StructA<Struct, String> parentStruct = 
											new StructA<Struct, String>(firstEnt, NodeType.STRUCTH, 
													called, NodeType.STR, "def", mx.get(0).get(inputStructListSize - 1));
									firstEnt.set_parentStruct(parentStruct);
									
									mx.get(0).get(inputStructListSize - 1).add(parentStruct);

									/*int q = 0;
									String[] calledArray = called.split(" ");
									String curWord = "";

									while (q < calledArray.length - 1) {
										curWord += calledArray[q] + " ";
										q++;
									}
									curWord += calledArray[calledArray.length - 1];*/
									
									// recentEnt is defined to be "called"
									parseState.addLocalVariableStructPair(called, recentEnt);
									
									continue outerloop;
									//continue innerloop;
								}
							}

							// search for tokens larger than immediate ones
							// in case A_or_A or A_and_A set the mx element
							// right below
							// to null
							// to set precedence, so A won't be grouped to
							// others later
							// and if the next word is a verb, it is not
							// singular
							// ie F and G is isomorphic
							
							// iterate through the List at position (i-t, i-1), for conj/disj
							if (i > 0 && i + 1 < inputStructListSize) {

								/*
								 * // set parent struct in row // above //
								 * mx.get(i - 1).set(j, // parentStruct);
								 * mx.get(i - 1).get(j).add(parentStruct); //
								 * set the next token to "", so // not //
								 * classified again // with others //
								 * mx.get(i+1).set(j, null); // already
								 * classified, no need // to // keep reduce with
								 * mx // manipulations break; } } } // this case
								 * can be combined with if // statement //
								 * above, use single while loop } else
								 */
								if (type1.matches("or|and")) {
									
									int t = 1;
									searchConjLoop: while (i - t > -1) {
										//i-1 because looking at the column before i.
										List<Struct> structArrayList = mx.get(i - t).get(i - 1).structList();

										int structArrayListSz = structArrayList.size();
										if (structArrayListSz == 0) {
											t++;
											continue;
										}
										//search for the farthest allowable ent, keep going up along the column.
										//e.g. "Given ring of finite presentation and field of finite type"	
										if(type2.equals("ent") && i - t - 2 > -1){
											List<Struct> structArrayList2 = mx.get(i-t-1).get(i-1).structList();
											List<Struct> structArrayList3 = mx.get(i-t-2).get(i-1).structList();
											//not double-looping O(mn) on average because of the conditionals.
											for(Struct list2Struct : structArrayList2){
												if(list2Struct.type().equals("prep")){
													for(Struct list3Struct : structArrayList3){
														if(list3Struct.type().equals("ent")){
															t++;
															//System.out.println("!!! list2Struct: " + list2Struct + " " + list3Struct);
															continue searchConjLoop;
														}
													}
													break;
												}
											}
											/*if(structArrayList2.get(0).type().equals("prep")
													&& structArrayList3.get(0).type().equals("ent")){
												t++;
												continue;
											}*/		
										}
										// iterate over Structs at (i-l, i-1)
										for (int p = 0; p < structArrayListSz; p++) {
											
											Struct p_struct = structArrayList.get(p);
											
											if (type2.equals(p_struct.type())) {
												
												// In case of conj, only proceed
												// if // next
												// word is not a singular verb.
												// // Single case with
												// complicated
												// logic, // so it's easier more
												// readable to // write //
												// if(this
												// case){ // }then{do_something}
												// // over if(not
												// this // case){do_something}
												Struct nextStruct = j + 1 < inputStructListSize ? inputStructList.get(j + 1) : null;
												
												if (nextStruct != null && type1.equals("and")
														&& nextStruct.prev1NodeType().equals(NodeType.STR)
														&& isSingularVerb((String) nextStruct.prev1())) {
													/* Intentionally left blank, for improved clarity. */
													//In case of conj, only proceed if next
													// word is not a singular verb.
												} else {

													String newType = type1.equals("or") ? "disj" : "conj";
													// type is expression, eg "a
													// and
													// b".
													// Come up with a scoring
													// system
													// for and/or!
													// should work a score in to
													// conj/disj! The longer the
													// conj/disj the higher
													NodeType struct1Type = struct1.isStructA() ? NodeType.STRUCTA : NodeType.STRUCTH;
													NodeType struct2Type = struct2.isStructA() ? NodeType.STRUCTA : NodeType.STRUCTH;
													
													//System.out.println("^^^Inside CONJ/DISJ. Struct1 " + struct1 + " Struct2 " + struct2);
													
													StructA<Struct, Struct> parentStruct = new StructA<Struct, Struct>(
															p_struct, struct1Type, struct2, struct2Type, newType + "_" + type2,
															mx.get(i - t).get(j));
													//if(true) throw new RuntimeException(parentStruct.toString());
													p_struct.set_parentStruct(parentStruct);
													struct2.set_parentStruct(parentStruct);
													//types are same, so scores should be same, so should only be punished once
													//use score instead of product
													double maxDownPathScore = p_struct.maxDownPathScore();
													parentStruct.set_maxDownPathScore(maxDownPathScore);

													mx.get(i - t).get(j).add(parentStruct);
													// mx.get(i - l).set(j,
													// parentStruct);
													// mx.get(i+1).set(j, null);
													//stopLoop = true;
													break searchConjLoop;
												}
											}
										}
										// if (stopLoop)
										// break;
										t++;
									}

								}
							}

							// potentially change assert to latex expr. <--Why?
							/*if (type2.equals("assert") && struct2.prev1NodeType().equals(NodeType.STR)
									&& ((String) struct2.prev1()).charAt(0) == '$'
									&& !structMap.containsKey(combined)) {
								struct2.set_type("expr");
								combined = type1 + "_" + "expr";
							}*/
							// update localVariablesMap
							if (type1.equals("ent") && !struct1.isStructA()) {
								String called = struct1.struct().get("called");
								if (called != null){
									parseState.addLocalVariableStructPair(called, struct1);
									
								}
							}

							// reduce if structMap has a rule for reducing combined
							if (structMap.containsKey(combined)) {
								Collection<Rule> ruleCol = structMap.get(combined);

								Iterator<Rule> ruleColIter = ruleCol.iterator();
								while (ruleColIter.hasNext()) {
									Rule ruleColNext = ruleColIter.next();
									EntityBundle entityBundle = reduce(mx, ruleColNext, struct1, struct2, firstEnt, recentEnt, recentEntIndex, i, j,
											k, type1, type2, parseState);
									//if nothing was updated
									if(null == entityBundle){
										continue;
									}
									recentEnt = entityBundle.recentEnt;
									firstEnt = entityBundle.firstEnt;
									recentEntIndex = entityBundle.recentEntIndex;
								}
							}

						} // loop listIter1 ends here
					} // loop listIter2 ends here
					//System.out.println("loop for (int k = j - 1; k >= i; k--)");
					// loop for (int k = j - 1; k >= i; k--) { ends here
				}
				//System.out.println("loop for (; i > 0; i--)");
				// loop for (; i > 0; i--) ends here
			}
			//System.out.println("loop for ( j = 0; j < len; j++)");
			// loop for ( j = 0; j < len; j++) ends here
		}

		// string together the parsed pieces
		// ArrayList (better at get/set) or LinkedList (better at add/remove)?
		// iterating over all headStruct
		StructList headStructList = mx.get(0).get(inputStructListSize - 1);
		int headStructListSz = headStructList.size();
		
		//System.out.println("headStructListSz " + headStructListSz);
		if(logger.getLevel().equals(Level.INFO)){
			String msg = "headStructListSz " + headStructListSz;
			System.out.println(msg);
			logger.info(msg);
		}
		
		Map<String, WLCommandWrapper> triggerWordsMap;
		//logger.info("headStructListSz " + headStructListSz);
		//the default entry value in context vec should be the average of all entries in matrix.
		//list of context vectors.
		List<int[]> contextVecList = new ArrayList<int[]>();		
		
		if (headStructListSz > 0) {		
			
			List<Struct> structList = headStructList.structList();
			//sort headStructList so that only dfs over the head structs whose
			//scores are above a certain threshold, and to guarantee that a minimum 
			//number of spanning parses have been counted
			Collections.sort(structList, new HeadStructComparator());
			
			StringBuilder parsedSB = new StringBuilder();
			
			// System.out.println("index of highest score: " +
			// ArrayDFS(headStructList));
			//temporary list to store the ParsedPairs to be sorted. It's list of multimaps, 
			//each Multimap corresponds to the commands picked up for the parse tree whose root
			//is an element of headStructList.
			List<Multimap<ParseStructType, ParsedPair>> parsedPairMMapList = new ArrayList<Multimap<ParseStructType, ParsedPair>>();
			//List of headParseStruct's, each entry corresponds to the entry of same index in parsedPairMMapList.
			List<ParseStruct> headParseStructList = new ArrayList<ParseStruct>();
			//list of ParsedPairs used to store long forms, same order of pairs as in parsedPairMMapList
			List<ParsedPair> longFormParsedPairList = new ArrayList<ParsedPair>();
			
			//to compare with headStructListSz.
			int actualCommandsIteratedCount = 0;
			//should just sort headStructList, but that costs time.
			//boolean someParseCounted = false;
			
			for (int u = 0; u < headStructListSz; u++) {
				Struct uHeadStruct = structList.get(u);
				
				double maxDownPathScore = uHeadStruct.maxDownPathScore();
				//System.out.println("*&&*#*&%%%##### ");
				//don't keep iterating if sufficiently many long parses have been built
				//with scores above a certain threshold. But only if some, however bad,
				//has been counted.
				// Reduces parse explosion.
				if(maxDownPathScore < LONG_FORM_SCORE_THRESHOLD 
						&& actualCommandsIteratedCount > MIN_PARSE_ITERATED_COUNT){					
					continue;
				}
				actualCommandsIteratedCount++;
				
				uHeadStruct.set_dfsDepth(0);
				
				int[] curStructContextvec = new int[parseContextVectorSz];		
				//keep track of span of longForm, used as commandNumUnits in case
				//no WLCommand parse. The bigger the better.
				int span = 0;
				ConjDisjVerbphrase conjDisjVerbphrase = new ConjDisjVerbphrase();
				boolean isRightChild = true;
				//get the "long" form, not WL form, with this dfs()
				span = buildLongFormParseDFS(uHeadStruct, parsedSB, span, conjDisjVerbphrase, isRightChild);
				//if conj_verbphrase or disj_verbphrase encountered, and these conclude the 
				//sentence, e.g. "if $R$ is a field and has a zero.", separate the conj_verbphrase
				//out to "if $R$ is a field and if $R$ has a zero", this makes command-triggering
				//much more feasible.
				if(conjDisjVerbphrase.isHasConjDisjVerbphrase() && conjDisjVerbphrase.assertTypeFound()){
					ConjDisjVerbphrase.reorganizeConjDisjVerbphraseTree(
							conjDisjVerbphrase);
					//no use, since parsedSB has been built already
					//headStructList.set(u, uHeadStruct);
					//throw new IllegalStateException();
				}
				
				//ParseStruct headParseStruct = new ParseStruct();
				//now get the WL form build from WLCommand's
				//should pass in the wlSB, instead of creating one anew each time. <--It's ok.
				treeTraversal(uHeadStruct, headParseStructList, parsedPairMMapList, curStructContextvec, span,
						parseState);
				//headParseStructList.add(headParseStruct);
				contextVecList.add(curStructContextvec);
				
				System.out.println("+++Previous long parse: " + parsedSB);
				
				//defer these additions to orderPairsAndPutToLists()
				//parsedExpr.add(new ParsedPair(wlSB.toString(), maxDownPathScore, "short"));		
				//System.out.println("*******SHORT FORM: " + wlSB);
				//parsedExpr.add(new ParsedPair(parsedSB.toString(), maxDownPathScore, "long"));				
				
				longFormParsedPairList.add(new ParsedPair(parsedSB.toString(), maxDownPathScore, "long"));
				
				System.out.println(maxDownPathScore);
				System.out.println(uHeadStruct.numUnits());

				String parsedString = ParseToWL.parseToWL(uHeadStruct);
				//parsedExpr.add(new ParsedPair(parsedString, maxDownPathScore, "wl"));
				System.out.print(parsedString + " \n ** ");
				
				parsedSB.setLength(0); //should just declare new StringBuilder instead!
			}
			
			if(logger.getLevel().equals(Level.INFO)){
				logger.info("actualCommandsIteratedCount: " + actualCommandsIteratedCount);
			}
			//order maps from parsedPairMMapList and put into parseStructMapList and parsedExpr.
			//Also add context vector of highest scores
			
			orderPairsAndPutToLists(parsedPairMMapList, headParseStructList, parseState, longFormParsedPairList, contextVecList);
			//parseStructMapList.add(parseStructMap.toString() + "\n");
			//append the highest-ranked parse to parseState.curParseStruct
			
		}
		// if no full parse, try again with the previous parse segment's structure
		//substituted with this segment's Structs.
		//e.g. Let $F$ be a field, and $R$ a ring. But only if previous segment 
		//generated a spanning parse and satisfied certain WLCommands.
		//and current structlist is not too long
		else if(!isReparse && null != parseState.getHeadParseStruct()
				&& parseState.getTokenList().size() < REPARSE_UPPER_SIZE_LIMIT
				//the headStruct is still from the parse of the previous parse segment,
				//since there was no parse, and partial parses haven's happened yet.
				&& ((triggerWordsMap = parseState.getHeadParseStruct().getTriggerWordsMap())
						.containsKey("define") 
						|| triggerWordsMap
						.containsKey("let"))
				)
		{
			System.out.println("No full parse!");
			
			//See if desired trigger words are present.
			//Since trigger words determine which conditional parse to use
			//(should have an enum for conditionalParseType once more conditional parse
			//methods are introduced). List to be extended.
			
			isReparse = true;
			//negative side effects to parseState that should be discarded? <--side effects yet, 
			//but don't think they are negative
			parseState.setTokenList(ConditionalParse.superimposeStructList(parseState.getPrevTokenList(), 
					parseState.getTokenList()));
			parseState = parse(parseState, isReparse);
			
		}// if no full parse, and last token is verb, i.e. "$xyz$ holds'
		else if(!isReparse && inputStructList.get(inputStructListSize-1).type().equals("verb") ){
			inputStructList.get(inputStructListSize-1).set_type("verbAlone");
			parseState.setTokenList(inputStructList);
			parseState = parse(parseState, true);
		} 
		else if(!isReparse || totalNumUnitsInStructList(inputStructList) > 2){
			
			List<StructList> structListList = new ArrayList<StructList>();
			//list of integral 2-tuples with row and column coordinates.
			//The coordinates at index i are those of the struct at index i
			//in parsedStructList.
			List<int[]> structCoordinates = new ArrayList<int[]>();
			
			int i = 0, j = inputStructListSize - 1;
			while (j > -1) {
				i = 0;
				while (mx.get(i).get(j).size() == 0) {
					i++;
					// some diagonal elements can be set to null on purpose
					if (i >= j) {
						break;
					}
				}
				
				StructList tempStructList = mx.get(i).get(j);

				if (tempStructList.size() > 0) {					
					//but adding at 0 is slow! Add at end and reverse!
					structListList.add(0, tempStructList);
					structCoordinates.add(0, new int[]{i, j});
				}
				// a singleton on the diagonal <--not necessarily true any more
				if (i == j) {
					j--;
				} else {
					j = i - 1;
				}
			}

			// if not full parse, try to make into full parse by fishing out the
			// essential sentence structure, and discarding the singletons, i.e.
			//ones that did not form into any grammar rules in previous round.
			//recursively call this, discard bigger and bigger components, that are
			//the smallest in each round.			
			if(!isReparse){
			
				int parsedStructListSize = structListList.size();
				
				//if only one ent,
				//e.g. "then $ $", misrepresented StructH as ent, 
				//if should have been an "assert"
				if(structListList.size() < 3){
					
					//go through structList see 
					//boolean couldConvertToAssert = false;
					List<Struct> entSubstitutedStructList = new ArrayList<Struct>(inputStructList);
					Struct toBeConvertedStruct = null;
					
					for(StructList sList : structListList){
						Struct struct = sList.get(0);
						//entToAssertStructList.add(struct);
						if(struct.isLatexStruct()){
							//if(true) throw new IllegalStateException(structListList.toString());
							assert !struct.isStructA() 
								: "Struct must be StructA to be latexStruct.";						
							//couldConvertToAssert = true;
							toBeConvertedStruct = struct;
							break;
						}						
					}
					
					if(null != toBeConvertedStruct){
						
						//need to convert toBeConvertedStruct to a StructA 
						//with type "assert".						
						String toBeConvertedStructName = toBeConvertedStruct.nameStr();
						
						for(int k = 0; k < entSubstitutedStructList.size(); k++){
							
							Struct structToSubstitute = entSubstitutedStructList.get(k);
							if(structToSubstitute.nameStr().equals(toBeConvertedStructName)){
								//if s already has child, then means probably should not turn into assertion,
								//since most children are appended during mx-building
								if(structToSubstitute.has_child()){
									break;
								}
								
								//StructH should not have any properties .  Look through properties of toBeConvertedStruct?
								StructA<String, String> convertedStructA = new StructA<String, String>(toBeConvertedStructName, 
										NodeType.STR, "", NodeType.STR, "texAssert");
								
								convertedStructA.set_parentStruct(structToSubstitute.parentStruct());
								//convertedStructA.set_maxDownPathScore(structToSubstitute.maxDownPathScore());
								
								entSubstitutedStructList.set(k, convertedStructA);
								//if(true) throw new IllegalStateException(inputStructList.toString());
								//isReparse = true;
								parseState.setTokenList(entSubstitutedStructList);
								//don't set isReparse, so to allow defluffing in the recursion call.
								System.out.println("~~REPARSING with assert");
								parse(parseState);						
								System.out.println("~~REPARSING with assert DONE");
							}
						}						
					}
				}						
			if(!parseState.isRecentParseSpanning()){
			//String totalParsedString = "";
			double totalScore = 1; //product of component scores
			//list of multimaps, each Multimap corresponds to the commands picked up
			//from a parsedStructList entry.
			List<Multimap<ParseStructType, ParsedPair>> parsedPairMMapList = new ArrayList<Multimap<ParseStructType, ParsedPair>>();
			List<ParseStruct> headParseStructList = new ArrayList<ParseStruct>();
			//list of long forms, with flattened tree structure.
			List<ParsedPair> longFormParsedPairList = new ArrayList<ParsedPair>();
			
			int[] curStructContextvec = new int[parseContextVectorSz];	
			
			for (int k = 0; k < parsedStructListSize; k++) {
				StringBuilder parsedSB = new StringBuilder();
				// int highestScoreIndex = ArrayDFS(parsedStructList.get(k));
				int highestScoreIndex = 0;
				Struct kHeadStruct = structListList.get(k).structList().get(highestScoreIndex);
				//measures span of longForm (how many leaf nodes reached), use in place of 
				//commandNumUnits if no full WLCommand parse.
				int span = 0;
				kHeadStruct.set_dfsDepth(0);
				
				ConjDisjVerbphrase conjDisjVerbphrase = new ConjDisjVerbphrase();
				boolean isRightChild = true;
				//get the "long" form, not WL form, with this dfs()
				span = buildLongFormParseDFS(kHeadStruct, parsedSB, span, conjDisjVerbphrase, isRightChild);
				//if conj_verbphrase or disj_verbphrase encountered, and these conclude the 
				//sentence, e.g. "if $R$ is a field and has a zero.", separate the conj_verbphrase
				//out to "if $R$ is a field and if $R$ has a zero", this makes command-triggering
				//much more feasible.
				if(conjDisjVerbphrase.isHasConjDisjVerbphrase() && conjDisjVerbphrase.assertTypeFound()){
					kHeadStruct = ConjDisjVerbphrase.reorganizeConjDisjVerbphraseTree(
							conjDisjVerbphrase);
				}
				
				//only getting first component parse. Should use priority queue instead of list?
				//or at least get highest score
				totalScore *= kHeadStruct.maxDownPathScore();
				String parsedString = ParseToWL.parseToWL(kHeadStruct);
				
				if (k < parsedStructListSize - 1){
					//totalParsedString += parsedString + "; ";
					System.out.print(";  ");
					parsedSB.append("; ");
				}
				
				StringBuilder wlSB = treeTraversal(kHeadStruct, headParseStructList, parsedPairMMapList, curStructContextvec, 
						span, parseState);
				longFormParsedPairList.add(new ParsedPair(parsedSB.toString(), totalScore, "long"));
			}
			
			//add only one vector to list since the pieces are part of one single parse, if no full parse
			contextVecList.add(curStructContextvec);
			
			//defer these to ordered addition in orderPairsAndPutToLists!
			//parsedExpr.add(new ParsedPair(parsedSB.toString(), totalScore, "long"));						

			//get total number of commandNumUnits that belong to parsedPairs with "NONE" as head, e.g. NONE :> [vbs[is] 1.0  1  1],
			//add up the e.g. 1's at the end. If the sum is below a certain percentage of inputStructList.size(), 
			//which means the rest span well, don't spend time doing second parse.
			int commandNumUnitsWithHeadNoneSum = 0;
			
			for(Multimap<ParseStructType, ParsedPair> mmap: parsedPairMMapList){
				Collection<ParsedPair> pairColl = mmap.get(ParseStructType.NONE);
				//there shouldn't be more than one per pairColl, since NONE indicates
				//no commmands were picked up for this parsedStructList entry.
				for(ParsedPair pair : pairColl){
					//same as pair.numUnits.
					commandNumUnitsWithHeadNoneSum += pair.commandNumUnits;
				}
			}
			orderPairsAndPutToLists(parsedPairMMapList, headParseStructList, parseState, longFormParsedPairList, contextVecList);
			
			//if commandNumUnitsWithHeadNoneSum sufficiently low: so sufficiently few parses
			//with NONE, don't parse again. Half is a good threshold
			if(commandNumUnitsWithHeadNoneSum > inputStructList.size()/2){
				//parse again.
				//form new structList
				List<Struct> newStructList = new ArrayList<Struct>();
				
				for(int k = 0; k < structListList.size(); k++){
					StructList structList_i = structListList.get(k);					
					//get something other than 0th??
					newStructList.add(structList_i.get(0));					
				}
				parseState.setTokenList(newStructList);
				
				//parseAgain defluffs based on ones that are not connected to neighbors.
				ParseAgain.parseAgain(newStructList, structCoordinates, parseState);
				
				System.out.println("\n=__+++++_======structList after Defluffing round 1: " + parseState.getTokenList());
				
				//if still no spanning parse found in above defluffing approach, now
				//try another approach: dropping elements, 
				if(!parseState.isRecentParseSpanning()){
				
					//form new structList
					List<Struct> structList = new ArrayList<Struct>();
					
					for(int k = 0; k < newStructList.size(); k++){
						Struct struct_k = newStructList.get(k);
						//System.out.print(structList_i.get(0)+"\t");
						//check if diagonal element, i.e. whether row index == column index.
						if(structCoordinates.get(k)[0] == structCoordinates.get(k)[1]
								//or is essential, e.g. ent, verb, symb <--too ad hoc
								&& !ESSENTIAL_POS_PATTERN.matcher(struct_k.type()).matches() //|pro|symb|if|hyp 
								){ 
							continue;	
						}						
						structList.add(struct_k);						
					}
					//if structList has same size as before, e.g. containing all ents, then don't re-parse,
					//will lead to infinite recursion!
					//In this case it's probably not a valid sentence (i.e. just sequence
					//of Tex expressions that showed up).
					if(structList.size() < parseState.getTokenList().size()){
						parseState.setTokenList(structList);
						
						boolean isReparseAgain = true;
						parseState = parse(parseState, isReparseAgain);
						
					}
					
				}
			}
			
			//if still no full parse try to see if converting latex expressions $...$ 
			//from "ent" to "assert" helps.			
			
			System.out.println("%%%%%\n");
		}
		}
		}
		// print out scores
		/*
		 * StructList headStructList = mx.get(0).get(len-1); int
		 * headStructListSz = headStructList.size(); System.out.println(
		 * "headStructListSz " + headStructListSz ); for(int u = 0; u <
		 * headStructListSz; u++){
		 * System.out.println(headStructList.structList().get(u) ); }
		 */

		/*
		 * Don't delete this part! System.out.println("\nWL: "); StructList
		 * headStructList = mx.get(len - 1).get(len - 1); //should pick out best
		 * parse before WL and just parse to WL for that particular parse!
		 */
		parseState.setRecentEnt(recentEnt);
		if(headStructListSz > 0){
			parseState.setRecentParseSpanning(true);
		}else{
			parseState.setRecentParseSpanning(false);
		}
		return parseState;
	}


	private static class HeadStructComparator implements Comparator<Struct>{
		
		//want the highest-scoring ones first, while iterating.
		@Override
		public int compare(Struct struct1, Struct struct2){
			double score1 = struct1.maxDownPathScore();
			double score2 = struct2.maxDownPathScore();
			return score1 < score2 ? 1 : (score1 > score2 ? -1 : 0);
		}
	}
	
	/**
	 * Order parsedPairMMapList and add to parseStructMapList and parsedExpr (both static members).
	 * Uses insertion sort, as since number of maps in parsedPairMMapList is usually very small, ~1-5.
	 * @param parsedPairMMapList
	 * @param headParseStructList 
	 * @param longFormParsedPairList List of long forms.
	 * @param contextVecList is list of context vectors, pick out the highest one and use as global context vec.
	 * contextVecList does not need to have same size as parsedPairMMapList or longFormParsedPairList, which have
	 * the same length. Length 1 if no spanning parse.
	 */
	private static void orderPairsAndPutToLists(List<Multimap<ParseStructType, ParsedPair>> parsedPairMMapList,
			List<ParseStruct> headParseStructList, ParseState parseState,
			List<ParsedPair> longFormParsedPairList, List<int[]> contextVecList){
		
		/*use insertion sort, since number of maps in parsedPairMMapList is usually very small, ~1-5.
		 * For maps with multiple entries (e.g. one sentence with both a HYP and a STM), add the numUnits and 
		 * commandNumUnits across entries. Keep track of this multiplicity of entries, favor map with smaller 
		 * number of entries if span the same.
		 */
		List<Multimap<ParseStructType, ParsedPair>> sortedParsedPairMMapList = new ArrayList<Multimap<ParseStructType, ParsedPair>>();
		//evolving list of numUnits scores for elements in sortedParsedPairMMapList
		List<Integer> numUnitsList = new ArrayList<Integer>();
		List<Integer> commandNumUnitsList = new ArrayList<Integer>();
		//use scores to tie-break once scores become more comprehensive
		List<Double> scoresList = new ArrayList<Double>();
		//number of entries in a Multimap in parsedPairMMapList.
		List<Integer> mMapSizeList = new ArrayList<Integer>();
		//whether there exists nontrivial (non-None) ParseStructType's
		boolean nonTrivialTypeExists = false;
		
		//ordering in the sorted list, the value list.get(i) is the index of the pair in the original parsedPairMMapList
		List<Integer> finalOrderingList = new ArrayList<Integer>();
		//this shouldn't be necessary, since can use .add to finalOrderingList works instead of .set
		for(int i = 0; i < parsedPairMMapList.size(); i++){
			finalOrderingList.add(0);
		}
		Multimap<ParseStructType, ParsedPair> firstMMap = parsedPairMMapList.get(0);
		sortedParsedPairMMapList.add(firstMMap);
		
		int firstNumUnits = 0;
		int firstCommandNumUnits = 0;
		double firstScore = 1;
		int firstMMapSize = 0;
		
		if(firstMMap.containsKey(ParseStructType.HYP) 
						|| firstMMap.containsKey(ParseStructType.STM)
						|| firstMMap.containsKey(ParseStructType.HYP_iff)){
			nonTrivialTypeExists = true;
		}
		
		for(Map.Entry<ParseStructType, ParsedPair> entry : firstMMap.entries()){
			
			ParseStructType parseStructType = entry.getKey();
			if(ParseStructType.NONE != parseStructType){
				firstMMapSize++;
			}
			ParsedPair parsedPair = entry.getValue();			
			firstNumUnits += parsedPair.numUnits();
			firstCommandNumUnits += parsedPair.commandNumUnits();
			firstScore *= parsedPair.score();
		}
		
		System.out.println("****parsedPairMMapList " + parsedPairMMapList);
		numUnitsList.add(firstNumUnits);
		commandNumUnitsList.add(firstCommandNumUnits);
		scoresList.add(firstScore);
		mMapSizeList.add(firstMMapSize);
		
		for(int i = 1; i < parsedPairMMapList.size(); i++){
			int numUnits = 0;
			int commandNumUnits = 0;
			double score = 1;
			Multimap<ParseStructType, ParsedPair> mmap = parsedPairMMapList.get(i);
			int mMapSize = 0;
			
			if(!nonTrivialTypeExists 
					&& (mmap.containsKey(ParseStructType.HYP) 
							|| mmap.containsKey(ParseStructType.STM)
							|| mmap.containsKey(ParseStructType.HYP_iff))){
				nonTrivialTypeExists = true;
			}
			//should only count entries where the key is not NONE 
			for(Map.Entry<ParseStructType, ParsedPair> entry : mmap.entries()){				
				ParseStructType parseStructType = entry.getKey();
				if(ParseStructType.NONE != parseStructType){
					mMapSize++;
				}
				ParsedPair parsedPair = entry.getValue();
				numUnits += parsedPair.numUnits();
				commandNumUnits += parsedPair.commandNumUnits();
				//multiplying score has the added benefit that
				//extraneous parses (extra commands triggered
				//but not eliminated) will lower the score.
				score *= parsedPair.score();				
			}
			
			int listSz = sortedParsedPairMMapList.size();
			//put into sortedParsedPairMMapList in sorted order, best parse first
			//should sort rest according to numUnits!
			for(int j = 0; j < listSz; j++){
				//Multimap<ParseStructType, ParsedPair> sortedMap = sortedParsedPairMMapList.get(j);
				//commandNumUnits weigh the most, use numUnits as tie-breakers, use numUnits if
				//commandNumUnits differ by more than 1. Count commandNumUnits diff 3/2 as much weight
				//as numUnits diff
				int sortedNumUnits = numUnitsList.get(j);
				int sortedCommandNumUnits = commandNumUnitsList.get(j);
				double sortedScore = scoresList.get(j);
				int sortedMMapSize = mMapSizeList.get(j);
				
				//current ParsedPair will rank higher if the following:
				//Make magic number into constant after experimenting.
				if(sortedCommandNumUnits < commandNumUnits 						
						|| (sortedNumUnits - numUnits) > ((double)sortedCommandNumUnits - commandNumUnits)*3/2
								//or there is MMapSize difference.
						|| (sortedMMapSize > 0 && mMapSize > 0 && sortedMMapSize > mMapSize
						   && (sortedNumUnits - numUnits) > ((double)sortedCommandNumUnits - commandNumUnits)/2)){
					//insert with right order
					sortedParsedPairMMapList.add(j, mmap);
					numUnitsList.add(j, numUnits);
					commandNumUnitsList.add(j, commandNumUnits);
					scoresList.add(j, score);
					finalOrderingList.add(j, i);
					mMapSizeList.add(j, mMapSize);
					break;
				}
				//numUnits can be a little worse, but not more worse by more than 1 unit.
				else if(sortedCommandNumUnits == commandNumUnits && sortedNumUnits+1 >= numUnits 
						&& sortedScore < score){
					//now sortedNumUnits
					//use scoresList to tie-break
					//insert with right order
					sortedParsedPairMMapList.add(j, mmap);
					numUnitsList.add(j, numUnits);
					commandNumUnitsList.add(j, commandNumUnits);
					scoresList.add(j, score);
					finalOrderingList.add(j, i);
					mMapSizeList.add(j, mMapSize);
					break;
				}				
			}
			//add at the end if doesn't beat any mmap prior.
			if(listSz == sortedParsedPairMMapList.size()){
				sortedParsedPairMMapList.add(listSz, mmap);
				numUnitsList.add(listSz, numUnits);
				commandNumUnitsList.add(listSz, commandNumUnits);
				scoresList.add(listSz, score);
				finalOrderingList.add(listSz, i);
				mMapSizeList.add(listSz, mMapSize);
			}			
		}
		//if top-ranked element has ParseStructType None and there exist lower-ranked
		//ones that have nontrivial ParseStructType's. <--Shouldn't be a problem now, Jan 2017.
		if(nonTrivialTypeExists){
			
			Multimap<ParseStructType, ParsedPair> firstMap = sortedParsedPairMMapList.get(0);
			
			if(1 == firstMap.size() && firstMap.containsKey(ParseStructType.NONE)){				
				//the top-ranked one has type None, even when there are nontrivial ones that rank lower
				for(int i = 1; i < sortedParsedPairMMapList.size(); i++){
					Multimap<ParseStructType, ParsedPair> mmap = sortedParsedPairMMapList.get(i);
					if(mmap.containsKey(ParseStructType.HYP) 
							|| mmap.containsKey(ParseStructType.STM)
							|| mmap.containsKey(ParseStructType.HYP_iff)){
						sortedParsedPairMMapList.remove(i);
						sortedParsedPairMMapList.add(0, mmap);	
						break;
					}
				}				
			}
		}
		
		if(DEBUG) System.out.println("##commandNumUnitsList " + commandNumUnitsList );
		//only add nontrivial results, but add trivial results (>=3) if no nontrivial ones exist.
		//This works as the results are sorted, and nontrivial ones come first in sorted list.
		boolean parsedExprAdded = false;
		for(int i = 0; i < sortedParsedPairMMapList.size(); i++){
			//only add nontrivial results //finalOrderingList.get(i)
			if(parsedExprAdded && commandNumUnitsList.get(i) < 3){				
				continue;
			}else{
				parsedExprAdded = true;
			}
			
			Multimap<ParseStructType, ParsedPair> map = sortedParsedPairMMapList.get(i);
			
			parseStructMapList.add(map.toString() + "\n");
			if(unitTesting) parseStructMaps.add(map);
			
			//add to parsedExpr  parsedExpr.add(new ParsedPair(totalParsedString, totalScore, "wl"));
			//note that Multimap does not necessarily preserve insertion order!
			for(Map.Entry<ParseStructType, ParsedPair> structTypePair : map.entries()){
				ParsedPair pair = structTypePair.getValue();
				ParseStructType parseStructType = structTypePair.getKey();
				WLCommand wlCommand = pair.wlCommand;
				parsedExpr.add(new ParsedPair(pair.parsedStr, pair.score, pair.numUnits, pair.commandNumUnits, 
						wlCommand, parseStructType));				
			}
			
			//Also add the long form to parsedExpr	
			parsedExpr.add(longFormParsedPairList.get(finalOrderingList.get(i)));
			if(DEBUG){
				System.out.println("longForm, commandUnits: " + commandNumUnitsList.get(i) +". numUnits: " +numUnitsList.get(i) 
					+ ". "+ longFormParsedPairList.get(finalOrderingList.get(i)));
			}
		}	
		//assign the global context vec as the vec of the highest-ranked parse
		int bestIndex = finalOrderingList.get(0);
		if(contextVecList.size() == 1){
			//in case there was no full parse, list should only contain one element.
			//since same vector was passed around to be filled.
			parseContextVector = contextVecList.get(0);
		}else{
			parseContextVector = contextVecList.get(finalOrderingList.get(0));			
			//System.out.println("Best context vector added: " +  Arrays.toString(parseContextVector));
		}
		parseState.addContextVecToCurThmParse(parseContextVector);
		
		ParseStruct bestParseStruct = headParseStructList.get(bestIndex);
		bestParseStruct.set_parentParseStruct(parseState.getCurParseStruct());
		
		//build relation vector for the highest-ranked parse, set relation vector to parseState.
		Multimap<ParseStructType, ParsedPair> topParsedPairMMap = sortedParsedPairMMapList.get(0);
		BigInteger relationVec = RelationVec.buildRelationVec(topParsedPairMMap);
		parseState.setRelationalContextVec(relationVec);
		//System.out.println("-+++++++++++best head!!! " + headParseStructList);
		//set head for this run of current part that triggered the command.
		if(null == parseState.getHeadParseStruct()){ 
			//curParseStruct is also null in this case.
			parseState.setHeadParseStruct(bestParseStruct);
			parseState.setCurParseStruct(bestParseStruct);
		}
		else{
			applyWrappersToParseState(parseState, bestParseStruct);
		}
		//parseState.addToHeadParseStructList(bestParseStruct);
		
		//System.out.println("curStructContextVec " + curStructContextVec);		
		//add the relevant wrapper (and so command strings) to curParseStruct in parseState.				
		//Decide whether to jump out of current ParseStruct layer
		String punctuation = parseState.getAndClearCurPunctuation();
		if(punctuation != null){
			setParseStateFromPunctuation(punctuation, parseState);
		}
				
	}
	
	/**
	 * Traverses and produces parse tree by calling various dfs methods,
	 * by matching commands. Returns
	 * string representation of the parse tree. Tree uses WLCommands.
	 * Not recursive.
	 * @param uHeadStruct
	 * @param headParseStruct, head ParseStruct list corresponding to parsedPairMMapList.
	 * @param parseState current parseState
	 * @return LongForm parse
	 */
	private static StringBuilder treeTraversal(Struct uHeadStruct, List<ParseStruct> headParseStructList, 
			List<Multimap<ParseStructType, ParsedPair>> parsedPairMMapList,
			int[] curStructContextVec, int span, ParseState parseState) {
		
		StringBuilder parseStructSB = new StringBuilder();
		//ParseStructType parseStructType = ParseStructType.getType(uHeadStruct);
		//gathering a new round of parseStruct's, i.e. new parse tree.
		//only gather the highest ranked parse for each parse segment (separated by 
		//punctuation) <--don't know highest ranked parse yet!
		
		ParseStruct curParseStruct = new ParseStruct();
		//set current parse struct for this run of the parse segment (delimited
		//by punctuations).
		//parseState.setCurParseStruct(curParseStruct);		
		headParseStructList.add(curParseStruct);
		
		/*if(0 == uHeadStructIndex){
			if(null == parseState.getCurParseStruct()){
				ParseStruct curParseStruct = new ParseStruct();
				parseState.setCurParseStruct(curParseStruct);
				parseState.setHeadParseStruct(curParseStruct);
				parseState.addToHeadParseStructList(curParseStruct);
			}
		}else{
			//don't set the headParseStruct to lesser-ranked parses.
			ParseStruct curParseStruct = new ParseStruct();
			parseState.setCurParseStruct(curParseStruct);
			parseState.addToHeadParseStructList(curParseStruct);
		}*/
		
		//whether to print the commands in tiers with the spaces in subsequent lines.
		boolean printTiers = false;
		//builds the parse tree by matching triggered commands. In particular, build WLCommand
		//parse tree by building triggered WLCommand's.
		ParseToWLTree.buildCommandsDfs(uHeadStruct, parseStructSB, 0, printTiers, parseState);
		System.out.println("\n DONE ParseStruct DFS  + parseStructSB:" + parseStructSB + "  \n");
		StringBuilder wlSB = new StringBuilder();
		/**
		 * Map of parts used to build up a theorem/def etc. 
		 * Parts can be any ParseStructType. Should make this a local var.
		 */
		Multimap<ParseStructType, ParsedPair> parseStructMap = ArrayListMultimap.create();
		
		//fills the parseStructMap and produces String representation, collect commands built above.	
		boolean contextVecConstructed = false;
		contextVecConstructed = ParseToWLTree.collectCommandsDfs(parseStructMap, curParseStruct, uHeadStruct, wlSB, 
				curStructContextVec, true, contextVecConstructed, parseState);
		
		if(!contextVecConstructed){
			ParseTreeToVec.tree2vec(uHeadStruct, curStructContextVec);
		}
		
		System.out.println("Parts: " + parseStructMap);
		//**parseStructMapList.add(parseStructMap.toString() + "\n");
		//if parseStructMap empty, ie no WLCommand was found, but long parse form might still be good
		if(parseStructMap.isEmpty()){
			ParsedPair pair = new ParsedPair(wlSB.toString(), //parseState.getCurParseStruct(), 
					uHeadStruct.maxDownPathScore(), 
					uHeadStruct.numUnits(), span, null);
			//partsMap.put(type, curWrapper.WLCommandStr);	
			parseStructMap.put(ParseStructType.NONE, pair);
		}
		
		parsedPairMMapList.add(parseStructMap);
		
		//ParseToWLTree.dfs(uHeadStruct, wlSB, true);		
		//parsedSB.append("\n");
		//ParseToWLTree.dfs(uHeadStruct, parsedSB, true);	
		System.out.println(wlSB);
		ParseToWLTree.dfsCleanUp(uHeadStruct);
		System.out.println("~~~ DONE WLCommands DFS ~~~");
		return wlSB;
	}

	/**
	 * Append wrappers to parseStruct appropriately, either create new layer,
	 * or continue adding to wrapperList of current layer.
	 * Take them from the
	 * waiting map of wrappers (wrapperMap), waiting because don't know 
	 * if HYP is amongst them, so don't know if need to jump out one layer.
	 * @param parseState
	 * @param waitingParseStruct parseStruct containing wrapperList (for parse segment), that need
	 * to be applied to parseState's curParseStruct. No depth! Meaning only one layer, no sublists
	 * of ParseStructs, can only have non-empty wrapperList. We don't expect more depth within the 
	 * same parse segment.
	 * Temporary placeholder.
	 */
	private static void applyWrappersToParseState(ParseState parseState, ParseStruct waitingParseStruct){
		
		ParseStruct curParseStruct = parseState.getCurParseStruct();
		//Multimap<ParseStructType, WLCommandWrapper> wrapperMap = parseState.retrieveAndClearWrapperMMap();
		
		Multimap<ParseStructType, WLCommandWrapper> wrapperMap = waitingParseStruct.getWLCommandWrapperMMap();
		//System.out.println("********WRAPPER MAP" + wrapperMap);
		if(wrapperMap.containsKey(ParseStructType.HYP) || wrapperMap.containsKey(ParseStructType.HYP_iff)){
			//create new parseStruct
			ParseStruct childParseStruct = new ParseStruct();
			
			childParseStruct.addParseStructWrapper(wrapperMap);
			
			curParseStruct.addToSubtree(childParseStruct);

			childParseStruct.set_parentParseStruct(curParseStruct);
			
			//set the reference of the current struct to point to the newly created struct
			parseState.setCurParseStruct(childParseStruct);
		}else{
			curParseStruct.addParseStructWrapper(wrapperMap);
		}
	
	}
	
	/**
	 * Returns true iff word is a singular verb (meaning associated to singular
	 * subj, eg "gives")
	 * 
	 * @param word
	 *            word to be checked
	 * @return
	 */
	private static boolean isSingularVerb(String word) {
		// did not find singular verbs that don't end in "s"
		int wordLen = word.length();

		if (wordLen < 2 || word.charAt(wordLen - 1) != 's')
			return false;

		// strip away 's'
		List<String> posList = posMMap.get(word.substring(0, wordLen - 2));
		//String pos = posMMap.get(word.substring(0, wordLen - 2)).get(0);
		if (!posList.isEmpty() && posList.get(0).matches("verb|verb_comp")) {
			return true;
		}
		// strip away es if applicable
		else if (wordLen > 2 && word.charAt(wordLen - 2) == 'e') {
			posList = posMMap.get(word.substring(0, wordLen - 3));


			if (!posList.isEmpty() && posList.get(0).matches("verb|verb_comp"))
				return true;
		}
		// could be special singular form, eg "is"
		else if (!(posList = posMMap.get(word)).isEmpty() && posList.get(0).matches("vbs|vbs_comp")) {
			return true;
		}
		return false;
	}

	/**
	 * Reduce based on returned grammar rules.
	 * @param mx
	 * @param newRule
	 * @param struct1
	 * @param struct2
	 * @param firstEnt
	 * @param recentEnt
	 * @param recentEntIndex
	 * @param i Indices in mx.
	 * @param j
	 * @param k
	 * @param type1
	 * @param type2
	 * @param parseState
	 * @return @Nullable EntityBundle containing entities such as recentEnt, etc.  
	 * null if not changed, so no wasting resources creating new objects.
	 */
	public static EntityBundle reduce(List<List<StructList>> mx, Rule newRule, Struct struct1, Struct struct2,
			Struct firstEnt, Struct recentEnt, int recentEntIndex, int i, int j, int k, String type1, String type2,
			ParseState parseState) {
		
		/*if(type2.equals("ent")){
			System.out.println("Reducing _ent! i " + newRule.relation() + ". struct1: " + struct1 +  "  " + struct2);
		}*/
		String newType = newRule.relation();
		double newScore = newRule.prob();
		double newDownPathScore = struct1.maxDownPathScore() * struct2.maxDownPathScore() * newScore;
		double parentDownPathScore = struct1.maxDownPathScore() * struct2.maxDownPathScore() * newScore;
		int parentNumUnits = struct1.numUnits() + struct2.numUnits();
		
		// newChild means to fuse second entity into first one
		if (newType.equals("newchild")) {
			// get a (semi)deep copy of this StructH, since
			// later-added children may not
			// get used eventually, ie hard to remove children
			// added during matrix building that do not belong
			// to the eventual parse. 
			Struct newStruct = struct1.copy();
			
			//could be struct1, or prev2 of struct1
			Struct structToAppendChild = newStruct;
			// struct1 should be of type StructH to receive a
			// child. If of type "conj_ent" or "disj_ent", then use 
			// the latter element in the conjunction/disjunction.			
			if(CONJ_DISJ_PATTERN1.matcher(struct1.type()).matches()
					&& struct1.prev2NodeType().equals(NodeType.STRUCTH)){
				structToAppendChild = ((Struct)struct1.prev2()).copy();
				newStruct.set_prev2(structToAppendChild);
			}
			
			// assert ensures rule correctness
			assert !structToAppendChild.isStructA() : "struct1 does not have type StructH in reduce() for \"newchild\" relation!";
			
			ChildRelation childRelation = null;
			Struct childToAdd = struct2;
			
			if(struct2.type().equals("hypo")){
				
				//prev1 has type Struct
				//e.g. "Field which is perfect", ...hypo[hyp[which is], perfect]
				//e.g. "which do not contain" is of type "hyp", but is parsed
				//to a StructA	...hypo[rpro[which], verb[do not contain]]
				assert struct2.prev1NodeType().equals(NodeType.STRUCTA);
				
				StructA<?, ?> hypStruct = (StructA<?, ?>)struct2.prev1();
				if(hypStruct.type().equals("hyp") ){
					childRelation = extractHypChildRelation(hypStruct);
					//throw new IllegalStateException();
				}
				
				//sometimes hypStruct does not have type "hyp" despite struct2 having
				//type "hypo"
				if(null == childRelation){
					childRelation = new ChildRelation("");
				}
				//which should be attached to immediately prior word
				//System.out.println("children: " + hypStruct+ " &&&"+ struct1 + " *** " + struct2);
				if(childRelation.childRelationStr().contains("which") ){
					//System.out.println("structToAppendChild : " + (structToAppendChild instanceof StructH) + " ! structToAppendChild.children(): " + structToAppendChild.children() );
					if(structToAppendChild.children().size() > 0){
						return new EntityBundle(firstEnt, recentEnt, recentEntIndex);
					}
				}
				childToAdd = (Struct)struct2.prev2();
			}else if(struct2.type().equals("Cond")){
				//e.g. "prime $I$ such that it's maximal."
				//prev1 should be of type "cond", and have content e.g. "such that"
				assert struct2.prev1NodeType().equals(NodeType.STRUCTA);
				
				StructA<?, ?> hypStruct = (StructA<?, ?>)struct2.prev1();
				if(hypStruct.type().equals("cond") ){
					childRelation = extractHypChildRelation(hypStruct);
				}
				childToAdd = (Struct)struct2.prev2();
				
			}else{			
			// add to child relation, usually a preposition, 
			//eg  "from", "over"
			// could also be verb, "consist", "lies"			
				List<Struct> kPlus1StructArrayList = mx.get(k + 1).get(k + 1).structList();			
				
				for(int p = 0; p < kPlus1StructArrayList.size(); p++){
					Struct struct = kPlus1StructArrayList.get(p);
					if(struct.prev1NodeType().equals(NodeType.STR)){
						
						String childRelationStr = struct.prev1().toString();
						childRelation = new ChildRelation.PrepChildRelation(childRelationStr);
						
						break;
					}
				}
			}
			
			if(childRelation == null){
				//should throw checked exception, and let the program keep running, rather than
				//runtime exception 				
				logger.info("Inside ThmP1.reduce(), childRelation should not be null! " + parseState.getTokenList());
				childRelation = new ChildRelation("");
				//return null;
				//throw new ParseRuntimeException("Inside ThmP1.reduce(), childRelation should not be null! " + parseState.getTokenList());
				//throw new IllegalStateException("Inside ThmP1.reduce(), childRelation should not be null! " + parseState.getTokenList());
			}
			
			//if type equivalent to to-be-parent's type and is "pre", don't add, 
			//e.g. "field F in C over Q"
			ChildRelationType childRelationType = childRelation.childRelationType();
			if(childRelationType.equals(ChildRelationType.PREP) && 
					//use struct1 and not structToAppendChild.
					struct1.childRelationType().equals(ChildRelationType.PREP)){
				return new EntityBundle(firstEnt, recentEnt, recentEntIndex);
			}
			
			if(struct2.type().equals("phrase")){
				//e.g. "ideal maximal among ..."
				// in which case treat as hyp
				assert struct2.prev1NodeType().equals(NodeType.STRUCTA);
				//phrase is a compound pos
				assert struct2.prev2NodeType().isTypeStruct();
				
				StructA<?, ?> struct2Prev1 = (StructA<?, ?>)struct2.prev1();
				if(struct2Prev1.type().equals("adj") ){
					childRelation = new ChildRelation.HypChildRelation(struct2Prev1.prev1().toString());
					childToAdd = (Struct)struct2.prev2();
				}
				
				Struct struct2Prev2 = (Struct)struct2.prev2();				
				
				if(struct2.prev2NodeType().equals(NodeType.STRUCTH)){
					//e.g. "phrase[adj[maximal], prep[pre[among], [ent{name=ring}]]]"
					//childRelation = extractHypChildRelation(struct2Prev1);
					//childRelation = new ChildRelation.HypChildRelation(((Struct)struct2Prev1.prev1()).contentStr());
					childRelation = new ChildRelation.HypChildRelation(struct2Prev1.prev1().toString());	
					((Struct)struct2Prev2).set_parentStruct(newStruct);
					childToAdd = struct2Prev2;
				}else if(struct2Prev2.prev2NodeType().equals(NodeType.STRUCTH)){
					//the right-most grandchild
					//System.out.println("^######^#^##^#^#^!@@ prev2Struct.prev2 " + struct2Prev2.prev2());
					//childRelation = extractHypChildRelation((Struct)struct2Prev2.prev1());
					childRelation = new ChildRelation.HypChildRelation(struct2Prev2.prev1().toString());
					((Struct)(struct2Prev2.prev2())).set_parentStruct(newStruct);
					childToAdd = (Struct)struct2Prev2.prev2();
					//System.out.println("&^^^^setting (Struct)(prev2Struct.prev2()) " + (Struct)(struct2Prev2.prev2()) + 
						//	" for parent " + newStruct);					
				}
			}
			
			// update firstEnt so firstEnt has the right children
			if (firstEnt == struct1) {
				firstEnt = structToAppendChild;
			}

			//should not even call add child if struct1 is a StructA
			if (!structToAppendChild.isStructA()) {
				// why does this cast not trigger unchecked warning <-- Because wildcard.
				//if already has child that's pre_ent, attach to that child,
				//e.g. A with B over C. <--This led to bug with too many children added!
				/*List<Struct> childrenList = newStruct.children();
				boolean childAdded = false;
				//iterate backwards, want the latest-added child that fits
				int childrenListSize = childrenList.size();
				for(int p = childrenListSize - 1; p > -1; p--){
					Struct child = childrenList.get(p);
					if(child.type().equals("ent") && !child.isStructA()){
						child.add_child(struct2, childRelation);
						struct2.set_parentStruct(child);
						childAdded = true;
						break;
					}
				}*/
				 
				//if struct2 is e.g. a prep, only want the ent in the prep
				//to be added.
				
				if(struct2.type().equals("prep") && struct2.prev2NodeType().equals(NodeType.STRUCTH)
						//if struct2 is of type "hypo", only add the condition, i.e. second
						//e.g. "ideal which is prime"
						|| struct2.type().equals("hypo") && struct2.prev2NodeType().isTypeStruct()){
					childToAdd = (Struct)struct2.prev2();
				}
				//set the type, corresponds to whether the relation is via preposition or conditional.
				//e.g. "over" vs "such as".
				childToAdd.set_childRelationType(childRelation.childRelationType());
				
				((StructH<?>) structToAppendChild).add_child(childToAdd, childRelation); 
				
				struct2.set_parentStruct(structToAppendChild); //redundant
				childToAdd.set_parentStruct(structToAppendChild); //redundant
				
				recentEnt = structToAppendChild;
				recentEntIndex = j;				
			}

			newStruct.set_maxDownPathScore(newDownPathScore);

			mx.get(i).get(j).add(newStruct);

		} 
		else if (newType.equals("addstruct")){
			// add struct2 content to struct1.struct, depending on type2
		
			if(type2.equals("expr")){
				Struct newStruct = struct1.copy();

				// update firstEnt so to have the right children
				if (firstEnt == struct1) {
					firstEnt = newStruct;
				}

				if (!struct1.isStructA()  && struct2.prev1NodeType().equals(NodeType.STR)) {
					((StructH<?>) newStruct).struct().put("tex", (String)struct2.prev1());
				}
				
				recentEnt = newStruct;
				recentEntIndex = j;
				
				newStruct.set_maxDownPathScore(newDownPathScore);

				mx.get(i).get(j).add(newStruct);
			}
		}else if(newType.equals("fuse")){	
			//fuse ent's, e.g. "integer linear combination"
			if(!struct1.isStructA() && !struct2.isStructA()){
				//System.out.println(Arrays.toString(Thread.currentThread().getStackTrace()));
				//System.out.println("struct1 " + struct1);
				//first struct cannot have collected children for this
				//rule to be meaningful
				//struct1 can't be independent word if it has picked up children.
				if(!struct1.children().isEmpty()){
					
					return null;
				}
				Struct newStruct = struct2.copy();
				
				Map<String, String> structMap = newStruct.struct();
				structMap.put("name", struct1.nameStr() + " " + structMap.get("name")) ;
				
				//put properties of struct1 to structMap
				Map<String, String> struct1Map = struct1.struct();
				for(Map.Entry<String, String> entry : struct1Map.entrySet()){
					if(entry.getValue().equals("ppt")){
						structMap.put(entry.getKey(), "ppt");
					}
				}
				
				recentEnt = newStruct;
				recentEntIndex = j;
				newStruct.set_maxDownPathScore(newDownPathScore);
				
				mx.get(i).get(j).add(newStruct);
			}
		}
		//absorb first into second
		else if(newType.equals("absorb1")){
			
			assert(struct1.isStructA() && !struct2.isStructA());

			if(!struct1.isStructA() || struct2.isStructA()){
				return null;
			}
			
			//absorb the non-struct into the struct
			Struct absorbingStruct = struct2;
			Struct absorbedStruct = struct1;
			
			Struct tempEnt = absorbStruct(mx, firstEnt, i, j, newDownPathScore, absorbingStruct, absorbedStruct);
			if(tempEnt != null){
				recentEnt = tempEnt;
				recentEntIndex = j;
			}
		}
		//absorb second into first
		else if(newType.equals("absorb2")){
			assert(!struct1.isStructA() && struct2.isStructA());
			
			if(struct1.isStructA() || !struct2.isStructA()){
				return null;
			}
			
			Struct absorbingStruct = struct1;
			Struct absorbedStruct = struct2;
			
			Struct tempEnt = absorbStruct(mx, firstEnt, i, j, newDownPathScore, absorbingStruct, absorbedStruct);
			if(tempEnt != null){
				recentEnt = tempEnt;
				recentEntIndex = j;
			}
		}
		//"if A is p so is B", make substitution with recent Ent
		else if(newType.equals("So")){
			Struct recentAssert = parseState.getRecentAssert();
			
			if(recentAssert != null){
				Struct parentPrev1;
				Struct parentPrev2;
				//"so has B", "so does B", etc
				if(recentAssert.prev2NodeType().isTypeStruct() && struct2.prev2NodeType().isTypeStruct()){
					parentPrev1 = (Struct) struct2.prev2();
					parentPrev2 = (Struct) recentAssert.prev2();					
					
					NodeType struct1Type = parentPrev1.isStructA() ? NodeType.STRUCTA : NodeType.STRUCTH;
					NodeType struct2Type = parentPrev2.isStructA() ? NodeType.STRUCTA : NodeType.STRUCTH;
					
					String updatedType = "assert";
					StructA<Struct, Struct> parentStruct = new StructA<Struct, Struct>(parentPrev1, struct1Type, 
							parentPrev2, struct2Type, updatedType, newScore, mx.get(i).get(j), parentDownPathScore, parentNumUnits);
					
					parentPrev1.set_parentStruct(parentStruct);
					//struct2.set_parentStruct(parentStruct);
					
					// mx.get(i).set(j, parentStruct);
					mx.get(i).get(j).add(parentStruct);					
				}				
			}
		}
		else if (newType.equals("noun")) {
			if (type1.equals("adj") && type2.equals("noun")) {
				// combine adj and noun
				String adj = struct1.prev1().toString();
				struct2.set_prev1(adj + " " + struct2.prev1());
				
				struct2.set_maxDownPathScore(struct2.maxDownPathScore() * newScore);
				// mx.get(i).set(j, struct2);
				mx.get(i).get(j).add(struct2);
				//throw new IllegalStateException("struct2:" + struct2);
			}
		}else if(type1.equals("rpro") && type2.equals("assert")){
			//e.g. "we say that $p$ is prime."
			if(struct1.prev1().equals("that")){
				//put the assert in the parent mx entry, ignore "that".
				mx.get(i).get(j).add(struct2);
			}
		}
		else {
			// if symbol and a given name to some entity
			// use "called" to relate entities
			Struct structToSet = null; //update those names pls
			
			if (type1.equals("symb") && struct1.prev1NodeType().equals(NodeType.STR)) {
				
				structToSet = struct1; 
				
				/*String entKey = struct1.prev1().toString();
				List<Struct> namesList = variableNamesMap.get(entKey);
				if (!namesList.isEmpty()) {
					int namesListLen = namesList.size();
					Struct curEnt = namesList.get(namesListLen-1);
					String structName = curEnt.struct().get("name");
					//don't combine for structs with names such as "map"
					if(!noFuseEntSet.contains(structName)){
						struct1.set_prev2(curEnt.struct().get("name"));
						//System.out.println("\n=======SETTING NAME curEnt: " + curEnt);
					}					
				}*/
			}

			// update struct2 with name if applicable
			// type could have been stripped down from conj_symb
			if (type2.equals("symb") && struct2.prev1NodeType().equals(NodeType.STR)) {

				structToSet = struct2; 
				/*String entKey = struct2.prev1().toString();
				List<Struct> namesList = variableNamesMap.get(entKey);
				if (!namesList.isEmpty()) {
					int namesListLen = namesList.size();
					Struct curEnt = namesList.get(namesListLen-1);
					
					struct2.set_prev2(curEnt.struct().get("name"));
				}*/
			}
			
			if(structToSet != null){
					String entKey = structToSet.prev1().toString();
					
					List<VariableDefinition> namesList = parseState.getNamedStructList(entKey);
					if (!namesList.isEmpty()) {
						int namesListLen = namesList.size();
						Struct curEnt = namesList.get(namesListLen-1).getDefiningStruct();
						//System.out.println("curEnt: " + curEnt + " !curEnt.struct(): " + curEnt.struct());
						if(null != curEnt && null != curEnt.struct()){
							String structName = curEnt.struct().get("name");
							//don't combine for structs with names such as "map"
							if(null != structName && !noFuseEntSet.contains(structName)){
								structToSet.set_prev2(curEnt.struct().get("name"));
								//System.out.println("\n=======SETTING NAME curEnt: " + curEnt);
							}					
						}
					}
				//}
			}
			else if(newType.equals("assert_prep")){
				//make 2nd type into "hypo"
				struct2.set_type("hypo");
			}
			// add to namesMap if letbe defines a name for an ent
			else if (newType.equals("letbe") && i+1 < mx.size() && k+2 < mx.size()
					&& mx.get(i + 1).get(k).size() > 0 && mx.get(k + 2).get(j).size() > 0) {
				// temporary patch Rewrite StructA to avoid cast
				// assert(struct1 instanceof StructA);
				// assert(struct2 instanceof StructA);
				// get previous nodes
				
				// now need to iterate through structList's for these two
				// Structs
				List<Struct> tempSymStructList = mx.get(i + 1).get(k).structList();
				List<Struct> tempEntStructList = mx.get(k + 2).get(j).structList();

				int tempSymStructListSz = tempSymStructList.size();
				ploop: for (int p = 0; p < tempSymStructListSz; p++) {
					Struct tempSymStruct = tempSymStructList.get(p);

					if (tempSymStruct.type().equals("symb")) {

						for (int q = 0; q < tempSymStructListSz; q++) {

							Struct tempEntStruct = tempEntStructList.get(q);
							if (!tempEntStruct.isStructA()) {
								
								// assumes that symb is a leaf struct! Need to
								// make more robust. check to ensure symb is in fact a leaf.
								//variableNamesMap.put(tempSymStruct.prev1().toString(), tempEntStruct);
								String name = tempSymStruct.prev1().toString();
								parseState.addLocalVariableStructPair(name, tempEntStruct);
								tempEntStruct.struct().put("called", name);
								break ploop;
							}
						}
					}
				}
			}
			
			// create new StructA and put in mx, along with score for
			// struct1_struct2 combo
			//double parentDownPathScore = struct1.maxDownPathScore() * struct2.maxDownPathScore() * newScore;
			//int parentNumUnits = struct1.numUnits() + struct2.numUnits();
			
			NodeType struct1Type = struct1.isStructA() ? NodeType.STRUCTA : NodeType.STRUCTH;
			NodeType struct2Type = struct2.isStructA() ? NodeType.STRUCTA : NodeType.STRUCTH;
			
			StructA<Struct, Struct> parentStruct = new StructA<Struct, Struct>(struct1, struct1Type, 
					struct2, struct2Type, newType, newScore, mx.get(i).get(j), parentDownPathScore, parentNumUnits);
			if(null == struct1.parentStruct()){
				struct1.set_parentStruct(parentStruct);
			}
			if(null == struct2.parentStruct()){
				struct2.set_parentStruct(parentStruct);
			}
			
			if(newType.equals("assert")){
				parseState.setRecentAssert(parentStruct);
				//System.out.println("Assert added! parentStruct" + parentStruct);
			}
			
			// mx.get(i).set(j, parentStruct);
			mx.get(i).get(j).add(parentStruct);
		}
		// found a grammar rule match, move on to next mx column
		// *****actually, should keep going and keep scores!
		// break;
		return new EntityBundle(firstEnt, recentEnt, recentEntIndex);
	}

	/**
	 * @param hypStruct
	 * @return
	 */
	private static ChildRelation extractHypChildRelation(Struct hypStruct) {
		ChildRelation childRelation;
		String hypStructPrev1Str = "";
		String hypStructPrev2Str = "";
		
		if(hypStruct.prev1NodeType().equals(NodeType.STRUCTA) ){
			hypStructPrev1Str = ((Struct)hypStruct.prev1()).prev1().toString();
		}else if(hypStruct.prev1NodeType().equals(NodeType.STR)){
			hypStructPrev1Str = hypStruct.prev1().toString();
		}
		
		if(hypStruct.prev2NodeType().equals(NodeType.STRUCTA) ){
			hypStructPrev2Str = ((Struct)hypStruct.prev2()).prev1().toString();
		}else if(hypStruct.prev2NodeType().equals(NodeType.STR)){
			hypStructPrev1Str = hypStruct.prev2().toString();
		}
		
		String relationStr = hypStructPrev1Str + (hypStructPrev2Str.equals("") ? "" : " " + hypStructPrev2Str);	
		
		//System.out.println("=++++++=========++++ HYPO " + relationStr);
		childRelation = new ChildRelation.HypChildRelation(relationStr);
		return childRelation;
	}
	
	/**
	 * Use as return type to wrap around recentEnt and firstEnt
	 */
	private static class EntityBundle{
		
		private Struct recentEnt;
		private Struct firstEnt;
		private int recentEntIndex;
		
		/**
		 * @return the recentEnt
		 */
		public Struct getRecentEnt() {
			return recentEnt;
		}

		/**
		 * @return the firstEnt
		 */
		public Struct getFirstEnt() {
			return firstEnt;
		}

		/**
		 * @return the recentEntIndex
		 */
		public int getRecentEntIndex() {
			return recentEntIndex;
		}

		
		public EntityBundle(Struct firstEnt, Struct recentEnt, int recentEntIndex){
			this.recentEnt = recentEnt;
			this.firstEnt = firstEnt;
			this.recentEntIndex = recentEntIndex;
		}
	}

	/**
	 * @param mx
	 * @param firstEnt
	 * @param i
	 * @param j
	 * @param newDownPathScore
	 * @param absorbingStruct
	 * @param absorbedStruct
	 */
	private static Struct absorbStruct(List<List<StructList>> mx, Struct firstEnt, int i, int j, double newDownPathScore,
			Struct absorbingStruct, Struct absorbedStruct) {
		Struct recentEnt = null;
		//if(struct1.isStructA() && !struct2.isStructA()){
			Struct newStruct = absorbingStruct.copy();				
			//add as property
			String ppt = "";
			if(absorbedStruct.prev1NodeType().isTypeStruct()){
				//ppt = ((Struct)absorbedStruct.prev1()).simpleToString(true,  ); //<--deal with ent_symb!
			}else{
				ppt = absorbedStruct.prev1().toString();
			}
			if(absorbedStruct.type().equals("symb")){
				newStruct.struct().put("called", ppt);
			}else{
				newStruct.struct().put(ppt, "ppt");
			}
			// update firstEnt so to have the right children
			if (firstEnt == absorbingStruct) {
				firstEnt = newStruct;
			}
			recentEnt = newStruct;
			newStruct.set_maxDownPathScore(newDownPathScore);
			mx.get(i).get(j).add(newStruct);
		//}
			return recentEnt;
	}

	/**
	 * Entry point for depth first first. Initialize score mx of List's of
	 * MatrixPathNodes
	 * 
	 * @param structList,
	 *            the head, at mx position (len-1, len-1)
	 * @return
	 * @deprecated
	 */
	private static int ArrayDFS(StructList structList) {
		// ArrayList<MatrixPathNode> mxPathNodeList = new
		// ArrayList<MatrixPathNode>();
		// fill in the list of MatrixPathNode's from structList

		// highest score encountered so far in list
		double highestDownScore = 0;
		int highestDownScoreIndex = 0;

		List<Struct> structArList = structList.structList();

		int structListSz = structArList.size();

		for (int i = 0; i < structListSz; i++) {
			Struct curStruct = structArList.get(i);

			//double ownScore = curStruct.score();
			// create appropriate right and left Node's

			MatrixPathNode curMxPathNode = new MatrixPathNode(curStruct);
			
			// put path into corresponding Struct, the score along the path
			// down from here
			double pathScore = ArrayDFS(curMxPathNode);
			curStruct.set_maxDownPathScore(pathScore);

			if (pathScore > highestDownScore) {
				highestDownScore = pathScore;
				highestDownScoreIndex = i;
			}
			System.out.println("pathScore: " + pathScore);
		}

		structList.set_highestDownScoreIndex(highestDownScoreIndex);
		return highestDownScoreIndex;
	}

	/**
	 * depth-first-search with arrays construct Keep track of path via tree of
	 * MatrixPathNode's, and the scores thus far through the tree
	 * 
	 * @param mxPathNode
	 *            MatrixPathNode, corresponding to current Struct
	 * @return score
	 * @deprecated
	 */
	// combine iteration of arraylist and recursion
	private static double ArrayDFS(MatrixPathNode mxPathNode) {

		Struct mxPathNodeStruct = mxPathNode.curStruct();
		StructList structList = mxPathNodeStruct.get_StructList();

		// highest down score encountered so far in list
		double highestDownScore = 0;
		// System.out.println(mxPathNodeStruct);
		// if structList == null at this point, it means the Struct is leaf, so
		// can return
		if (structList == null)
			return 1;

		int highestDownScoreIndex = structList.highestDownScoreIndex();
		// Iterator<Struct> structListIter = structList.iterator();

		// maintain index in list of highest score
		// don't iterate through if scores already computed
		if (highestDownScoreIndex == -1) {
			List<Struct> structArList = structList.structList();

			int structListSz = structArList.size();
			// int tempHighestDownScoreIndex = 0;

			// score so far along this path corresponding to this Node (not
			// Struct!)
			// double scoreSoFar = mxPathNode.scoreSoFar();

			for (int i = 0; i < structListSz; i++) {

				Struct struct = structArList.get(i);

				double structScore = struct.score();
				// highest score down from this Struct
				double tempDownScore = structScore;

				// don't like instanceof here
				if (struct instanceof StructA) {

					// System.out.print(struct.type());
					// System.out.print("[");
					// don't know type at compile time
					if (struct.prev1() instanceof Struct) {
						// create new MatrixPathNode,
						// int index, double ownScore, double scoreSoFar,

						// leftMxPathNode corresponds to Struct struct.prev1()
						MatrixPathNode leftMxPathNode = new MatrixPathNode((Struct) struct.prev1());
						tempDownScore *= ArrayDFS(leftMxPathNode);
					}

					//
					if (struct.prev2() instanceof Struct) {
						// System.out.print(", ");
						// dfs((Struct) struct.prev2());
						// construct new MatrixPathNode for right child
						MatrixPathNode rightMxPathNode = new MatrixPathNode((Struct) struct.prev2());
						tempDownScore *= ArrayDFS(rightMxPathNode);
					}

					// reached leaf. Add score to mxPathNode being passed in,
					// return own score
					if (struct.prev1() instanceof String) {
						// System.out.print(struct.prev1());
						// do nothing because String leaves don't count towards
						// score
					}
					if (struct.prev2() instanceof String) {

						// if (!struct.prev2().equals(""))
						// System.out.print(", ");
						// System.out.print(struct.prev2());
					}

					// System.out.print("]");
				} else if (struct instanceof StructH) {

					// System.out.print(struct.toString());
					List<Struct> childrenStructList = struct.children();

					if (childrenStructList != null && childrenStructList.size() > 0) {
						// return 1;

						// System.out.print("[");
						for (int j = 0; j < childrenStructList.size(); j++) {
							// System.out.print(childRelation.get(j) + " ");
							// dfs(childrenStructList.get(j));

							Struct childStruct = childrenStructList.get(j);

							// double curStructScore = childStruct.score();
							// create MatrixPathNode for each child Struct
							MatrixPathNode childMxPathNode = new MatrixPathNode(childStruct);

							tempDownScore *= ArrayDFS(childMxPathNode);
						}
					}
					// System.out.print("]");
				}
				if (tempDownScore > highestDownScore) {
					highestDownScore = tempDownScore;
					highestDownScoreIndex = i;
				}

				mxPathNodeStruct.set_maxDownPathScore(tempDownScore);

			} // end for loop through structList

			// set highestDownScoreIndex
			structList.set_highestDownScoreIndex(highestDownScoreIndex);
		} else {
			highestDownScoreIndex = structList.highestDownScoreIndex();
			highestDownScore = structList.structList().get(highestDownScoreIndex).maxDownPathScore();

		}
		return highestDownScore;
	}

	/**
	 * Write unknown words to file to classify them.
	 * 
	 * @throws IOException
	 */
	public static void writeUnknownWordsToFile() {
		
		if(unknownWords.isEmpty()) return;
		
		try{
			Files.write(unknownWordsFile, unknownWords, Charset.forName("UTF-8"));
		}catch(IOException e){
			e.printStackTrace();
			throw new IllegalArgumentException(e);			
		}
	}

	/**
	 * write unknown words to file to classify them.
	 * Resets parsedExpr List by clearing.
	 * @throws IOException
	 */
	public static void writeParsedExprToFile() throws IOException {		
		List<String> parsedExprStringList = new ArrayList<String>();
		//just call .toString() directly on parsedExpr!
		for(ParsedPair parsedPair : parsedExpr){
			parsedExprStringList.add(parsedPair.toString());
		}
		Files.write(parsedExprFile, parsedExprStringList, Charset.forName("UTF-8"));
		parsedExpr = new ArrayList<ParsedPair>();
	}
	
	/**
	 * @return the List of parsed expressions, with different scores.
	 * Resets parsedExpr by re-initializing.
	 * Defensively copies List and returns copy.
	 */
	public static List<ParsedPair> getAndClearParsedExpr(){
		
		ImmutableList<ParsedPair> parsedExprCopy = ImmutableList.copyOf(parsedExpr);		
		parsedExpr = new ArrayList<ParsedPair>();
		return parsedExprCopy;
	}
	
	/**
	 * Clear parsedExpr.
	 */
	private static void clearParsedExpr(){				
		parsedExpr = new ArrayList<ParsedPair>();		
	}
	
	/**
	 * Should be called once per parse segment to capture value,
	 * for each String in return array of ThmP1.preprocess(input).
	 * @return the context vector of highest-ranking parse.
	 */
	public static int[] getParseContextVector(){
		//return null if context vec not meaningful! Like for 2 terms.
		int[] parseContextVectorCopy = Arrays.copyOf(parseContextVector, parseContextVectorSz);
		//parseContextVector = new int[parseContextVectorSz];
		return parseContextVectorCopy;
	}
	
	/** 
	 * @return The ParseStruct parts of each parse since last retrieval.
	 */
	public static List<String> getAndClearParseStructMapList(){		
		//List<String> parseStructMapListCopy = new ArrayList<String>(parseStructMapList);
		ImmutableList<String> parseStructMapListCopy = ImmutableList.copyOf(parseStructMapList);
		parseStructMapList = new ArrayList<String>();
		return parseStructMapListCopy;
	}
	
	/** 
	 * This method iterates through the lists of parseStructMaps.
	 * Should *only* be used for unit testing. Otherwise, should build separate field
	 * to avoid iterating here.
	 * @return The ParseStruct ParsedPairs of each parse since last retrieval.
	 */
	public static List<Multimap<ParseStructType, String>> getAndClearParseStructMapsForTesting(){		
		
		List<Multimap<ParseStructType, String>> parseStructStringList = new ArrayList<Multimap<ParseStructType, String>>();
		//get parsedStr in each parsedPair
		for(Multimap<ParseStructType, ParsedPair> map : parseStructMaps){
			Multimap<ParseStructType, String> newMap = ArrayListMultimap.create();
			for(Map.Entry<ParseStructType, ParsedPair> entry : map.entries()){
				newMap.put(entry.getKey(), entry.getValue().parsedStr.trim());
			}
			parseStructStringList.add(newMap);
		}
		parseStructMaps = new ArrayList<Multimap<ParseStructType, ParsedPair>>();
		return parseStructStringList;
	}
	
	/**
	 * if not full parse, try to make into full parse by fishing out the
	 * essential sentence structure, and discarding the phrases still not
	 * labeled after 2nd round
	 *
	 * @param parsedStructList
	 *            is list output by first round of parsing
	 */
	public static void parse2(List<Struct> inputList) {

	}

	/**
	 * Depth first search, to build initial parse tree.
	 * @param struct
	 * @param parsedSB
	 * @param span
	 * @param  conjDisjVerbphrase
	 * Don't need isRightChild, will remove in a week if still not needed, Dec 2016.
	 * @return
	 */
	private static int buildLongFormParseDFS(Struct struct, StringBuilder parsedSB, int span,
			ConjDisjVerbphrase conjDisjVerbphrase, boolean isRightChild) {
		
		int structDepth = struct.dfsDepth();
		String structType = struct.type();
		
		if (struct.isStructA()) {
			
			if(structType.equals("hyp") || structType.equals("if")){
				struct.setContainsHyp(true);
			}else if(CONJ_DISJ_VP_PATTERN.matcher(structType).matches()){
				conjDisjVerbphrase.setHasConjDisjVerbphrase(true);
				Struct parentStruct = struct.parentStruct();				
				
				if(null != parentStruct && parentStruct.type().equals("assert")){
					conjDisjVerbphrase.setAssertParentFound(true);
					conjDisjVerbphrase.setAssertStruct(parentStruct);
				}
				conjDisjVerbphrase.setConjDisjType(ConjDisjType.findConjDisjType(structType));
				//throw new IllegalStateException(isRightChild+"");
			}
			
			System.out.print(struct.type());
			parsedSB.append(struct.type());
			
			System.out.print("[");
			parsedSB.append("[");
			
			if (struct.prev1NodeType().isTypeStruct()) {
				
				Struct prev1Struct = (Struct) struct.prev1();
				prev1Struct.set_dfsDepth(structDepth + 1);
				span = buildLongFormParseDFS(prev1Struct, parsedSB, span,
						conjDisjVerbphrase, false);
				
				if(prev1Struct.containsHyp()){
					struct.setContainsHyp(true);
				}
			}			

			if (struct.prev1NodeType().equals(NodeType.STR)) {
				System.out.print(struct.prev1());
				parsedSB.append(struct.prev1());
				//don't include prepositions for spanning purposes, since prepositions are almost 
				//always counted if its subsequent entity is, but counting it gives false high span
				//scores, especially compared to the case when they are absorbed into a StructH, in
				//which case they are not counted.
				if(!struct.type().equals("pre")){
					span++;
				}
			}			

			// if(struct.prev2() != null && !struct.prev2().equals(""))
			// System.out.print(", ");
			if (struct.prev2NodeType().isTypeStruct()) {
				// avoid printing is[is], ie case when parent has same type as
				// child
				System.out.print(", ");
				parsedSB.append(", ");
				Struct prev2Struct = (Struct) struct.prev2();
				prev2Struct.set_dfsDepth(structDepth + 1);
				span = buildLongFormParseDFS(prev2Struct, parsedSB, span, conjDisjVerbphrase,
						isRightChild);
				
				if(prev2Struct.containsHyp()){
					struct.setContainsHyp(true);
				}
			}
			
			if (struct.prev2NodeType().equals(NodeType.STR)) {
				if (!struct.prev2().equals("")){
					System.out.print(", ");
					parsedSB.append(", ");	
					if(!struct.type().equals("pre")){
						span++;
					}
				}				
				System.out.print(struct.prev2());
				parsedSB.append(struct.prev2());
			}

			System.out.print("]");
			parsedSB.append("]");
		} else {
			
			System.out.print(struct.toString());
			parsedSB.append(struct.toString());
			span++;
			
			List<Struct> children = struct.children();
			List<ChildRelation> childRelation = struct.childRelationList();
			
			if (children == null || children.size() == 0)
				return span;

			System.out.print("[");
			parsedSB.append("[");
						
			for (int i = 0; i < children.size(); i++) {
				
				System.out.print(childRelation.get(i) + " ");
				parsedSB.append(childRelation.get(i) + " ");
				
				Struct child_i = children.get(i);
				child_i.set_dfsDepth(structDepth + 1);
				
				span = buildLongFormParseDFS(child_i, parsedSB, span, conjDisjVerbphrase, isRightChild);
			}
			System.out.print("]");
			parsedSB.append("]");
		}
		return span;
	}

	/**
	 * Preprocess. Remove fluff words. the, a, an
	 * 
	 * @param str
	 *            is string of all input to be processed.
	 * @return array of sentence Strings
	 */
	public static String[] preprocess(String inputStr) {
		
		if(logger.getLevel().equals(Level.INFO)){
			logger.info("Input: " + inputStr);
		}
		
		clearParsedExpr();
		
		ArrayList<String> sentenceList = new ArrayList<String>();
		
		//separate out punctuations, separate out words away from punctuations.
		//compile this!		
		String[] wordsArray = inputStr.replaceAll("([^\\.,!:;]*)([\\.,:!;]{1})", "$1 $2").split("\\s+");
		
		//System.out.println("wordsArray " + Arrays.toString(wordsArray));
		int wordsArrayLen = wordsArray.length;

		StringBuilder sentenceBuilder = new StringBuilder();
		// String newSentence = "";
		String curWord;
		// whether in latex expression
		boolean inTex = false; 
		boolean madeReplacement = false;
		boolean toLowerCase = true;
		// whether in parenthetical remark
		boolean inParen = false; 
		boolean inBrackets = false;
		
		String prevSentence = "";		
		String lastWordAdded = "";
		
		wordsArrayLoop: for (int i = 0; i < wordsArrayLen; i++) {

			curWord = wordsArray[i];
			
			//Skips parenthesized and bracketed items.
			//compile these!
			if (!inTex) {
				if (inParen && PAREN_END_PATTERN.matcher(curWord).matches()) {
					inParen = false;
					continue;
				} else if (inBrackets && BRACKET_END_PATTERN.matcher(curWord).matches()) {
					inBrackets = false;
					continue;
				} else if (inParen || inBrackets || PAREN_PATTERN.matcher(curWord).matches() 
						|| BRACKETS_PATTERN.matcher(curWord).matches()) { 
					continue;
				} else if (PAREN_START_PATTERN.matcher(curWord).matches()) {
					inParen = true;
					continue;
				} else if(BRACKET_START_PATTERN.matcher(curWord).matches()){
					inBrackets = true;
					continue;
				}
			}

			if (!inTex && curWord.matches("\\$.*") && !curWord.matches("^\\$[^$]+\\$.*$")) {
				inTex = true;
			} else if (inTex && curWord.contains("$")) {
				// }else if(curWord.matches("[^$]*\\$|\\$[^$]+\\$.*") ){
				inTex = false;
				toLowerCase = false;
			} else if (SINGLE_WORD_TEX_PATTERN.matcher(curWord).matches()) {
				toLowerCase = false;
			}

			if(!inTex && BEGIN_ALIGN_PATTERN.matcher(curWord).matches()){
				inTex = true;				
			}else if(inTex && END_ALIGN_PATTERN.matcher(curWord).matches()){
				inTex = false;
				toLowerCase = false;
			}
			
			//take entire \begin{enumerate} block as one sentence
			if(curWord.equals("\\begin{enumerate}")){
				if(sentenceBuilder.length() > 0){
					sentenceList.add(sentenceBuilder.toString());
					sentenceBuilder.setLength(0);
				}
				//StringBuilder enumerateSb = new StringBuilder();
				while(i < wordsArrayLen && !curWord.equals("\\end{enumerate}")){
					curWord = wordsArray[i];
					sentenceBuilder.append(" ").append(curWord);
					i++;
				}
				
				if(curWord.equals("\\end{enumerate}")){
					sentenceBuilder.append(" ").append(curWord);
				}
				
				sentenceList.add(sentenceBuilder.toString().trim());
				sentenceBuilder.setLength(0);
				
				if(i == wordsArrayLen){					
					return sentenceList.toArray(new String[0]);
				}
			}

			// fluff phrases all start in posMap
			String curWordLower = curWord.toLowerCase();
			if (posMMap.containsKey(curWordLower)) {
				String pos = posMMap.get(curWordLower).get(0);
				String[] posAr = pos.split("_");
				String tempWord = curWord;

				int j = i;
				// potentially a fluff phrase <--improve defluffing!
				if (posAr[posAr.length - 1].equals("comp") && j < wordsArrayLen - 1) {
					// keep reading in string characters, until there is no
					// match.
					tempWord += " " + wordsArray[++j];
					
					while (posMMap.containsKey(tempWord.toLowerCase()) && j < wordsArrayLen - 1) {
						tempWord += " " + wordsArray[++j];
					}
					
					//**tempWord sometimes invokes too many strings!***** <--when?
					//System.out.println("tempWord: " + tempWord);				
					
					String replacement = fluffMap.get(tempWord.toLowerCase());
					if (replacement != null) {
						sentenceBuilder.append(" ").append(replacement);
						lastWordAdded = replacement;
						madeReplacement = true;
						i = j;
					}
					
				}
			}

			// composite fluff words taken care of, with e.g. fixed phrases
			if (!madeReplacement && !PUNCTUATION_PATTERN.matcher(curWord).matches() 
					&& !fluffMap.containsKey(curWord.toLowerCase())) {
				
				if (inTex || !toLowerCase) {
					sentenceBuilder.append(" ").append(curWord);
					lastWordAdded = curWord;
					toLowerCase = true;
				} else{
					String wordToAdd = curWord.toLowerCase();
					sentenceBuilder.append(" ").append(wordToAdd);
					lastWordAdded = wordToAdd;
				}
			}
			madeReplacement = false;
			
			if (PUNCTUATION_PATTERN.matcher(curWord).matches()) {
				
				if (!inTex) {
					//if the token before and after the comma 
					//are not similar enough.
					if(curWord.equals(",") && i < wordsArrayLen - 1){
						//get next word
						String nextWord = wordsArray[i+1].toLowerCase();
						//get complete latex 
						if(nextWord.charAt(0) == '$'){
							String texExpr = getEntireLatexExpr(wordsArray, i+1);
							// if tex expressions are similar, e.g. "let $G$, $H$ be groups" 
							if(WordForms.areTexExprSimilar(lastWordAdded, texExpr)){
								//substitute the comma with "and"
								sentenceBuilder.append(" and ");
								//if(true) throw new IllegalStateException();
								continue;
							}
						}
						
						List<String> nextWordPosList = posMMap.get(nextWord);
						//e.g. $F$ is a ring, with no nontrivial elements. 
						if(!nextWordPosList.isEmpty() && nextWordPosList.get(0).equals("pre")){
							//don't add curWord in this case, let the sentence continue.
							continue wordsArrayLoop;
						}
						
						if(AND_OR_PATTERN.matcher(nextWord).matches()){
							//if next word is "and"/"or", and has the same sentence type, i.e.
							//either both stm or hyp, so either both contain hypothetical words,
							//or neither do. e.g. "$G$ is a group, and if $R$ is its group algebra".
							//Try to detect hyp pattern.
							//Read until the end of the sentence that starts from nextWord
							//for (int i = 0; i < wordsArrayLen; i++)
							int j = i+2;
							StringBuilder nextSentenceSB = new StringBuilder(nextWord);
							while(j < wordsArrayLen){
								String word = wordsArray[j++];
								if(PUNCTUATION_PATTERN.matcher(word).matches()){
									break;
								}
								nextSentenceSB.append(" ").append(word);
							}
							
							boolean prevSentenceHyp = HYP_PATTERN.matcher(sentenceBuilder.toString()).matches();
							boolean nextSentenceHyp = HYP_PATTERN.matcher(nextSentenceSB.toString()).matches();
							
							//System.out.println("!!prevSentence: " + sentenceBuilder + " nextSentenceSB: " + nextSentenceSB);
							//System.out.println(!(prevSentenceHyp ^ nextSentenceHyp));
							//take xor, so only skip comma if structures are the same.
							if(!(prevSentenceHyp ^ nextSentenceHyp)){
								continue wordsArrayLoop;
							}else{
								//if different type, skip the conjunction/disjunction token.
								i++;
							}
						}
												
					}
					//add the punctuation to use later
					sentenceBuilder.append(" ").append(curWord);
					//System.out.println("curWord: " +curWord+". sentence to append " + sentenceBuilder.toString());
					
					prevSentence = sentenceBuilder.toString();
					sentenceList.add(prevSentence);
					
					sentenceBuilder.setLength(0);
					
				} else { //in Latex
					lastWordAdded = curWord;
					sentenceBuilder.append(" ").append(curWord);
				}
				//throw new IllegalStateException("punctuation: " + curWord);
			} else if(i == wordsArrayLen - 1){ 
				sentenceList.add(sentenceBuilder.toString());
				sentenceBuilder.setLength(0);
			}
		}
		if(sentenceList.isEmpty()){
			sentenceList.add(sentenceBuilder.toString());
		}
		
		System.out.println("sentenceList " + sentenceList);
		return sentenceList.toArray(new String[0]);
	}
	
	/**
	 * Gets the entire tex expression starting from index i.
	 * @param wordsArray
	 * @param i
	 * @return
	 */
	private static String getEntireLatexExpr(String[] wordsArray, int i) {
		
		String curWord = wordsArray[i];
		if(SINGLE_WORD_TEX_PATTERN.matcher(curWord).matches()){
			return curWord;
		}
		i++;
		StringBuilder sb = new StringBuilder(curWord);
		while(i < wordsArray.length){
			curWord = wordsArray[i];
			sb.append(" " + curWord);
			if(curWord.contains("$")){
				return sb.toString();
			}
			i++;
		}
		return sb.toString();
	}

}
