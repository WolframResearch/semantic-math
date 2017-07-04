package thmp.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;

import thmp.parse.Maps;
import thmp.parse.Pair;
import thmp.parse.ParsedExpression;
import thmp.parse.Struct;
import thmp.parse.ThmP1;
import thmp.search.WordFrequency;

public class WordForms {

	private static final Logger logger = LogManager.getLogger(WordForms.class);
	//delimiters to split on when making words out of input
	private static final String SPLIT_DELIM = "\\s+|\'|\\(|\\)|\\{|\\}|\\[|\\]|\\.|\\;|\\,|:|-|_|~|!";
	private static final Pattern BACKSLASH_PATTERN = Pattern.compile("(?<!\\\\)\\\\(?!\\\\)");
	private static final Pattern ALL_WHITE_EMPTY_SPACE_PATTERN = Pattern.compile("^\\s*$");
	private static final Pattern ALL_WHITE_NONEMPTY_SPACE_PATTERN = Pattern.compile("^\\s+$");
	private static final Pattern WHITE_NONEMPTY_SPACE_PATTERN = Pattern.compile("\\s+");
	private static final Pattern BRACES_PATTERN = Pattern.compile("(\\{|\\}|\\[|\\])");
	
	public static final Pattern SPECIAL_CHARS_PATTERN = Pattern.compile(".*[-\\{\\[\\)\\(\\}\\]$\\\\%/|*.;,:_~!+^&\"\'+<>=#].*");
	public static final Pattern ALPHABET_PATTERN = Pattern.compile("[A-Za-z]");
	/*Used to remove specical characters from words*/
	private static final Pattern SPECIAL_CHARS_AROUND_WORD_PATTERN 
		= Pattern.compile("[\\{\\[\\(]+(.+?)[\\}\\]\\)]*|[\\{\\[\\(]*(.+?)[\\}\\]\\)]+");
	private static final Pattern IRREG_PLURAL_ENDINGS_PATTERN = Pattern.compile("s|h|x");
	/*Don't include numerical quantities such as "one", "five" etc here, which should be determined from algorithm */
	public static final Pattern CARDINALITY_PPT_PATTERN = Pattern.compile("some|a|an|the|unique|infinite|infinitely many");
	private static final Pattern NUMBER_PATTERN = Pattern.compile("one|two|three|four|five|six|seven|eight|nine|ten|twenty|"
			+ "thirty|forty|fourty|fifty|sixty|seventy|eighty|ninety|hundred|thousand|million|billion");
	//private static final Pattern NUMBER_PATTERN2 = Pattern.compile("hundred|thousand|million|billion"); 
	private static final Pattern SPACES_AROUND_TEXT_PATTERN = Pattern.compile("\\s*(.+)\\s*");
	private static final Pattern DASH_PATTERN = Pattern.compile("-");
	//pattern for Latex expressions being possible assert's, i.e. starting/ending with $ and 
	//containing operators such as ">, ="
	private static final Pattern LATEX_ASSERT_PATTERN = Pattern.compile("\\$(?:[^$]+)\\$");
	private static final Pattern COMMAND_BEGIN_PATTERN = Pattern.compile("\\\\");
	//indicates termination of a Latex command
	private static final Pattern COMMAND_END_PATTERN = Pattern.compile("[\\$(\\[{\\])}_;,:!'`~%.\\-\"\\s]");
	
	public static final String QUANTITY_POS = "quant";
	
	private static final String synonymsFileStr = "src/thmp/data/synonyms.txt";
	private static final ImmutableMap<String, String> synonymRepMap;
	private static final ImmutableMultimap<String, String> stemToWordsMMap;
	
	//pattern for lines to skip any kind of parsing, even hypothesis-detection.
		//skip examples and bibliographies  
	private static final Pattern SKIP_PATTERN = Pattern.compile("\\\\begin\\{proof\\}.*|\\\\begin\\{exam.*|\\\\begin\\{thebib.*");
	private static final Pattern END_SKIP_PATTERN = Pattern.compile("\\\\end\\{proof\\}.*|\\\\end\\{exam.*|\\\\end\\{thebib.*");
		
		//single lines to skip. Such as comments
	private static final Pattern SINGLE_LINE_SKIP_PATTERN = Pattern.compile("^%.*|\\\\begin\\{bib.*|.*FFFFFF.*|.*fffff.*|\\/.*");
	
	//small lists of fluff words, used in, e.g., n gram extraction.
	//*don't* put "of" here, will interfere with 3 gram collection
	private static final String FLUFF_WORDS_SMALL = "a|the|tex|of|and|on|let|lemma|for|to|that|with|is|be|are|there|by"
			+ "|any|as|if|we|suppose|then|which|in|from|this|assume|this|have|just|may|an|every|it|between|given|itself|has"
			+ "|more|where|but";
	private static final Set<String> FLUFF_WORDS_SMALL_SET;
	
	private static final Set<String> freqWordsSet; 
	//brackets pattern
	private static final Pattern BRACKETS_PATTERN = Pattern.compile("\\[([^\\]]*)\\]");	
	private static final Pattern LATEX_PATTERN = Pattern.compile("\\$([^$]+)\\$");
	private static final Pattern NONSINGULAR_ENDING_PATTERN = Pattern.compile(".*(?:us$?|is$?|ness|has)");
	
	//pattern matching is faster than calling str.contains() repeatedly 
	//which is O(mn) time.
	private static final Pattern HYP_PATTERN = Pattern.compile(".*(?:assume|denote|define|let|is said|suppose"
			+ "|where|is called|if|given).+") ;
	private static final Pattern SPLIT_DELIM_PATTERN = Pattern.compile(SPLIT_DELIM);
	
	private static final Set<String> GREEK_ALPHA_SET;

	static{		
		FLUFF_WORDS_SMALL_SET = new HashSet<String>();
		String[] fluffAr = FLUFF_WORDS_SMALL.split("\\|");
		for(String word : fluffAr){
			FLUFF_WORDS_SMALL_SET.add(word);
		}
		freqWordsSet = new HashSet<String>();
		Map<String, String> synonymsPreMap = new HashMap<String, String>();
		BufferedReader synonymsBF = null;
		//create synonym map from file	
		ServletContext servletContext = FileUtils.getServletContext();
		if(null != servletContext){
			synonymsBF = new BufferedReader(new InputStreamReader(servletContext.getResourceAsStream(synonymsFileStr)));			
		}else{
			try{
				synonymsBF = new BufferedReader(new FileReader(new File(synonymsFileStr)));
			}catch(FileNotFoundException e){
				String msg = "WordForms.java initializer - FileNotFoundException when creating FileReader for synonymsFileStr";
				logger.error(msg);
				throw new IllegalStateException(msg);
			}			
		}
		//gather synonyms together from file
		readSynonymMapFromFile(synonymsPreMap, synonymsBF);
		
		//contains word representatives, e.g. "annihilate", "annihilator", etc all map to "annihilat"
		Map<String, String> stemWordsMap = deserializeStemWordsMap(servletContext);	
		synonymsPreMap.putAll(stemWordsMap);
		synonymRepMap = ImmutableMap.copyOf(synonymsPreMap);
		//create "inverse" Multimap for the different forms for each word stem.
		stemToWordsMMap = ImmutableMultimap.copyOf(createStemToWordsMMap(stemWordsMap));
				
		FileUtils.silentClose(synonymsBF);
		
		GREEK_ALPHA_SET = new HashSet<String>();
		String[] GREEK_ALPHA = new String[]{"alpha","beta","gamma","delta","epsilon"};
		for(String s : GREEK_ALPHA){
			GREEK_ALPHA_SET.add(s);
		}		
	}
	
	private static Multimap<String, String> createStemToWordsMMap(Map<String, String> stemWordsMap){
		Multimap<String, String> stemToWordsMMap = ArrayListMultimap.create();		
		for(Map.Entry<String, String> entry: stemWordsMap.entrySet()){
			stemToWordsMMap.put(entry.getValue(), entry.getKey());
		}
		return stemToWordsMMap;
	}
	
	private static Set<String> getFreqWordsSet(){
		if(freqWordsSet.isEmpty()){
			synchronized(WordForms.class){
				if(freqWordsSet.isEmpty()){
					freqWordsSet.addAll(WordFrequency.ComputeFrequencyData.get_FreqWords());
					freqWordsSet.addAll(Maps.posMMap().keySet());
				}
			}
		}
		return freqWordsSet;
	}
	
	@SuppressWarnings("unchecked")
	private static Map<String, String> deserializeStemWordsMap(ServletContext servletContext) {

		String stemWordsMapFileStr = "src/thmp/data/stemWordsMap.dat";
		Map<String, String> stemWordsMap;
		if(null != servletContext){
			InputStream stemWordsMapInputStream = servletContext.getResourceAsStream(stemWordsMapFileStr);			
			stemWordsMap = ((List<Map<String, String>>)FileUtils
					.deserializeListFromInputStream(stemWordsMapInputStream)).get(0);
		}else{
			stemWordsMap = ((List<Map<String, String>>)FileUtils.deserializeListFromFile(stemWordsMapFileStr)).get(0);
		}
		return stemWordsMap;
	}

	/**
	 * Populate synonymsMMap_ with synonyms read in from synonymsBF.
	 * @param synonymsmmap2
	 * @param synonymsBF
	 */
	private static void readSynonymMapFromFile(Map<String, String> synonymsMap_, BufferedReader synonymsBF) {
		String line;
		try{
			while(null != (line = synonymsBF.readLine())){
				String[] synonymsAr = WordForms.getWhiteNonEmptySpacePattern().split(line);
				//take first word in array as representative of this synonyms set
				String wordRep = synonymsAr[0];
				for(int i = 1; i < synonymsAr.length; i++){
					synonymsMap_.put(synonymsAr[i], wordRep);
				}
				/*Set<String> synonymsSet = new HashSet<String>();
				for(String word : synonymsAr){
					synonymsSet.add(word);
				}			
				for(String word : synonymsAr){
					synonymsSet.remove(word);
					synonymsMMap_.putAll(word, synonymsSet);
					synonymsSet.add(word);
				}*/				
			}	
		}catch(IOException e){
			String msg = "WordForms.readSynonymMapFromFile : IOException when reading BufferedReader!";
			logger.error(msg);
			throw new IllegalStateException(msg);
		}
	}

	/**
	 * Returns the most likely singular form of the word, or
	 * original word if it doesn't end in s, es, or ies.
	 * Minimal length of word is 3 chars.
	 * @param word
	 * @return
	 */
	public static String getSingularForm(String word){
		//if word in dictionary, should not be processed. Eg "continuous"
		//System.out.println("WordForms- word "+word);
		if(getFreqWordsSet().contains(word) || word.length() < 4){ 
			return word;		
		}
		int wordLen0 = word.length();
		String lastTwoChars = word.substring(wordLen0-2);
		//radius, class, iris, basis, calculus, torus
		if(lastTwoChars.equals("is") || lastTwoChars.equals("us") || lastTwoChars.equals("ss")){
			return word;
		}
		String[] singFormsAr = getSingularForms(word);
		//singFormsAr successively replaces words ending in "s", "es", "ies"
		int k = 2;
		while(k > -1 && singFormsAr[k] == null){
			k--;
		}
		if(k != -1){
			word = singFormsAr[k];
			//System.out.println("singular form: "+ word);
		}
		//if letter before "es" is not "s", eg "kisses", or "ch", eg "catches",
		//add e back, to preserve terms that end in "e", eg "agrees"
		int wordLen = word.length();
		if(k==1 && wordLen > 1 && !IRREG_PLURAL_ENDINGS_PATTERN.matcher(String.valueOf(word.charAt(wordLen-1))).matches()){
			word = word+"e";
		}
		return word;
	}
	
	/**
	 * Remove word endings such as "ly".
	 * This does not de-singularize words. 
	 * @param word
	 * @return
	 */
	public static String removeWordEnding(String word){
		Set<String> freqWordsSet = getFreqWordsSet();
		
		if(freqWordsSet.contains(word)) return word;
		int wordlen = word.length();
		if(wordlen < 4) return word; //<--move this to below if more endings are checked in future
		
		String tempWord;
		if("ly".equals(word.substring(wordlen-2)) && freqWordsSet.contains((tempWord = word.substring(0, wordlen-2)))){
			word = tempWord;
			//System.out.println("**********************ly removed from " + word);
		}
		return word;
	}
	
	public static boolean isGerundForm(String word){
		if(null == word) return false;
		String s = getGerundForm(word);
		return word.equals(s) ? false : true;
	}
	
	/**
	 * Returns the most likely normal form of the word that end in
	 * e.g. "-ing". 
	 * @param word
	 * @return Need to return null
	 */
	private static String getGerundForm(String curWord){
		
		int wordlen = curWord.length();
		if(wordlen < 5) return curWord;
		
		Set<String> freqWordsSet = getFreqWordsSet();
		if(freqWordsSet.contains(curWord)) return curWord;
		
		String tempWord;		
		if(curWord.substring(wordlen - 3).equals("ing")){
			if(freqWordsSet.contains((tempWord = curWord.substring(0, wordlen - 3)))
							//&& posMMap.get(curWord.substring(0, wordlen - 3)).get(0).matches("verb|vbs")
							){
				return tempWord;
			}else if(freqWordsSet.contains((tempWord = curWord.substring(0, wordlen - 3) + 'e'))
							//&& posMMap.get(curWord.substring(0, wordlen - 3) + 'e').get(0).matches("verb|vbs")
							){
				return tempWord;
			}
		}
		return curWord;
	}
	
	/**
	 * Need to build up a map of words and their single representative in the 
	 * term-document matrix, using a Multimap that contains words and all their
	 * synonyms, created in WordForms.
	 * The representative is the first word in the synonym set that was encountered.
	 * Synonyms apply to singleton words (not n grams) only.
	 * Common method so synonyms only occupy one entry in the term-document matrix.
	 * This is used in both gathering word frequency data, forming the term document matrix,
	 * and searching.
	 * @param word
	 * @return the representative of the synonym words. null if no synonym rep found.
	 */
	public static String findSynonymRepInWordsDict(String word){
		return synonymRepMap.get(word);
	}
	
	/**
	 * Retrieves synonyms map.
	 */
	public static ImmutableMap<String, String> getSynonymsMap(){
		return synonymRepMap;
	}
	
	/**
	 * Get the singular forms of current word
	 * @param curWord
	 * @param wordlen
	 * @return Array of singular forms, of size 3.
	 */
	public static String[] getSingularForms(String curWord) {
		// primitive way to handle plural forms: if ends in "s"
		if(curWord.length() < 4) return new String[]{curWord, curWord, curWord};
		
		String[] singularForms = new String[3];
		int wordlen = curWord.length();
		
		if (wordlen > 0 && curWord.charAt(wordlen - 1) == 's') {
			//don't strip 's' if belongs to common endings with 's', e.g. "homogeneous", "basis"
			//e.g. ".*us$?|.*is$?|has".
			if(!NONSINGULAR_ENDING_PATTERN.matcher(curWord).matches()){
				singularForms[0] = curWord.substring(0, wordlen - 1);								
			}
		}

		if (wordlen > 2 && curWord.substring(wordlen - 2, wordlen).equals("es")) {
			singularForms[1] = curWord.substring(0, wordlen - 2);
		}

		if (wordlen > 3 && curWord.substring(wordlen - 3, wordlen).equals("ies")) {
			singularForms[2] = curWord.substring(0, wordlen - 3) + 'y';
		}
		return singularForms;
	}
	/**
	 * Canonicalize word. Used by all algorithms to pre-process words.
	 * Deliberately not combined with singularization, as sometimes need
	 * to be used separately.
	 * Does *NOT* check if the input word is already valid or not. Due to 
	 * initialization dependencies (would be cyclic).
	 * @param word
	 * @return
	 */
	public static String normalizeWordForm(String word){
		//remove ending such as "ly".  <--remove this or not??
		word = WordForms.removeWordEnding(word);
		
		//also remove -ing, 
		word = WordForms.getGerundForm(word);
		
		//if has synonym rep, use synonym rep instead 
		String rep = synonymRepMap.get(word);
		if(null != rep){
			word = rep;
		}
		return word;
	}
	
	public static class WordMapIndexPair{
		private static final int DEFAULT_MAP_INDEX = -1;
		//singleton placeholder instance.
		private static final WordMapIndexPair PLACEHOLDER_WORDMAPINDEXPAIR
			= new WordMapIndexPair("", DEFAULT_MAP_INDEX, false);
		private String word;
		private int mapIndex = DEFAULT_MAP_INDEX;
		private boolean wordChanged;
		
		public WordMapIndexPair(String word_, int index_, boolean changed_){
			this.word = word_;
			this.mapIndex = index_;
			this.wordChanged = changed_;
		}		
		public String word(){
			return this.word;
		}
		public int mapIndex(){
			return this.mapIndex;
		}
		public boolean wordChanged(){
			return this.wordChanged;
		}
		public static WordMapIndexPair placeholderWordMapIndexPair(){
			return PLACEHOLDER_WORDMAPINDEXPAIR;
		}
	}
	/**
	 * Singularize and normalize word, if necessary, and return its
	 * index in provided map.
	 * @param word
	 * @param map
	 * @return WordMapIndexPair
	 */
	public static WordMapIndexPair uniformizeWordAndGetIndex(String word, Map<String, Integer> map){
		String[] wordAr = WordForms.getWhiteEmptySpacePattern().split(word);
		int wordArLen = wordAr.length;
		if(wordArLen > 1){
			word = wordAr[wordArLen - 1];			
		}
		Integer index = map.get(word);
		if(null != index){
			return new WordMapIndexPair(word, index, false);
		}
		//this case should be more applicable than desingularization,
		//since many inputs from parse trees are already desingularized.
		String wordNormalized = normalizeWordForm(word);
		index = map.get(wordNormalized);
		if(null != index){
			return new WordMapIndexPair(wordNormalized, index, true);
		}
		String wordSingular = getSingularForm(word);
		index = map.get(wordSingular);
		if(null != index){
			return new WordMapIndexPair(wordSingular, index, true);
		}
		String singularNormalized = normalizeWordForm(wordSingular);
		index = map.get(singularNormalized);
		if(null != index){
			return new WordMapIndexPair(singularNormalized, index, true);
		}
		return WordMapIndexPair.PLACEHOLDER_WORDMAPINDEXPAIR;
	}
	
	/**
	 * Centralized method to normalize two grams. Right now
	 * only desingularize second word, without normalizing to word stem
	 * -July 2017.
	 * @param twoGram
	 */
	public static String normalizeTwoGram(String twoGram){
		String[] wordAr = WordForms.WHITE_NONEMPTY_SPACE_PATTERN.split(twoGram);		
		if(wordAr.length < 2){
			return twoGram;
		}
		String secondWord = getSingularForm(wordAr[1]);		
		return wordAr[0] + " " + secondWord;
	}
	
	/**
	 * Returns words that should be excluded from search.
	 */
	public static Set<String> getFluffSet(){
		return FLUFF_WORDS_SMALL_SET;
	}
	
	/**
	 * Returns split delimiters.
	 * @return
	 */
	public static String splitDelim(){
		return SPLIT_DELIM;
	}
	
	/**
	 * Returns split delimiters.
	 * @return
	 */
	public static Pattern splitDelimPattern(){
		return SPLIT_DELIM_PATTERN;
	}
	
	/**
	 * @param thm
	 * @return
	 */
	public static String[] splitThmIntoSearchWords(String thm){		
		return SPLIT_DELIM_PATTERN.split(thm);
	}
	
	/**
	 * Return brackets pattern: "\\[([^\\]]*)\\]".
	 * E.g. "...[...]..."
	 * @return
	 */
	public static Pattern BRACKETS_PATTERN(){
		return BRACKETS_PATTERN;
	}
	
	/**
	 * Pattern than begins and ends with dollar signs: \\$([^$]+)\\$.
	 * @return
	 */
	public static Pattern LATEX_PATTERN(){
		return LATEX_PATTERN;
	}
	
	public static enum TokenType{
		SINGLETON(1), TWOGRAM(2), THREEGRAM(3);
		int indexShift;
		static int TWO_GRAM_BONUS = 1;
		static int THREE_GRAM_BONUS = 1;
		
		TokenType(int shift){
			this.indexShift = shift;
		}
		
		public void addToMap(Multimap<Integer, Integer> thmWordSpanMMap, int thmIndex, int wordIndex){
			for(int i = wordIndex; i < wordIndex+indexShift; i++){
				thmWordSpanMMap.put(thmIndex, i);
			}
		}
		
		/**
		 * Adjusts the integral word score for N-grams, such that the N-gram's score
		 * is the sum of the constituent words' scores, plus some bonus.
		 * @param curScoreToAdd
		 * @param singletonScoresAr
		 * @param wordIndex
		 * @return
		 */
		public int adjustNGramScore(int curScoreToAdd, int[] singletonScoresAr, int wordIndex){
			switch(this){
				case SINGLETON:
					return curScoreToAdd;
				case TWOGRAM:
					return Math.max(curScoreToAdd, singletonScoresAr[wordIndex] 
							+ singletonScoresAr[wordIndex+1] + TWO_GRAM_BONUS);
				case THREEGRAM:
					return Math.max(curScoreToAdd, singletonScoresAr[wordIndex] + singletonScoresAr[wordIndex+1]
							+ singletonScoresAr[wordIndex+2] + THREE_GRAM_BONUS);
				default:
					return curScoreToAdd;
			}
		}
		
		/**
		 * Whether current word at wordIndex has been added to thm already under a previous 2,3-gram.
		 * @param thmWordSpanMMap
		 * @param thmIndex
		 * @param wordIndex
		 */
		public boolean ifAddedToMap(Multimap<Integer, Integer> thmWordSpanMMap, int thmIndex, int wordIndex){
			
			if(this.equals(THREEGRAM)) return false;
			
			if(this.equals(TWOGRAM)){ 
				return thmWordSpanMMap.containsEntry(thmIndex, wordIndex)
						&& thmWordSpanMMap.containsEntry(thmIndex, wordIndex+1);
			}
			
			if(this.equals(SINGLETON)){ 
				return thmWordSpanMMap.containsEntry(thmIndex, wordIndex);
			}			
			return false;
		}		
	}
	
	/**
	 * Whether name of struct is sufficiently similar to name.
	 * @param struct
	 * @param name
	 * @return
	 */
	public static boolean areNamesSimilar(Struct struct, String name){
		String structName;
		if(struct.isLatexStruct()){
			structName = ThmP1.LATEX_PLACEHOLDER_STR();
		}else{
			structName = struct.nameStr();
		}
		return areNamesSimilar(structName, name);
	}
	/**
	 * If two names slight variations of each other, i.e. one is plural form of the other.
	 * Currently used for determining token similarities after querying syntaxnet.
	 * @param name1
	 * @param name2
	 * @return
	 */
	public static boolean areNamesSimilar(String name1, String name2){
		if(name1.equals(name2)){
			return true;
		}
		int len1 = name1.length();
		int len2 = name2.length();
		int longerLen = len1 > len2 ? len1 : len2;
		int shorterLen = len1 + len2 - longerLen;
		
		//e.g. "deity" vs "deities"
		if(longerLen - shorterLen > 2){
			String longerWord;
			String shorterWord;
			if(longerLen == len1){
				longerWord = name1;
				shorterWord = name2;
			}else{
				longerWord = name2;
				shorterWord = name1;
			}
			String[] wordAr = WHITE_NONEMPTY_SPACE_PATTERN.split(longerWord);
			if(wordAr.length > 1){
				return areNamesSimilar(wordAr[wordAr.length-1], shorterWord);
				/*for(String word : wordAr){
					if(areNamesSimilar(word, shorterWord)){
						return true;
					}
				} */
			}else{
				return false;				
			}
		}
		if(shorterLen > 2){
			int commonLen = shorterLen-1;
			if(name1.substring(0, commonLen).equals(name2.substring(0, commonLen))){
				return true;
			}else{
				return false;
			}
		}
		return false;
	}
	
	/**
	 * Determine if input word is a cardinality, e.g. five-thirty-eight.
	 * @param word Word to determine if numeric quantity.
	 * @param inputStrAr Array of strings, whose members constitute the original input string.
	 * @param wordIndex index of word in inputStrAr.
	 * @return index to start the next token after current quantity ends, if current is quantity.
	 */
	public static int isCardinality(String word, String[] inputStrAr, int wordIndex, Pair emptyPair){
		
		int inputStrArLen = inputStrAr.length;
		int nextTokenStartIndex = wordIndex;
		StringBuilder sb = new StringBuilder(25);
		String[] ar = DASH_PATTERN.split(word);
		//	boolean dashBool = ar.length > 1;
		if(ar.length > 1){
			//String[] inputStrAr1 = word.split("-");
			int wordIndex1 = 0; 
			///Pair emptyPair1 = new Pair(null, null);
			int temp = isCardinality(ar[0], ar, wordIndex1, emptyPair);
			if(temp > wordIndex1){
				sb.append(word).append(" ");
				if(++nextTokenStartIndex >= inputStrArLen){
					return nextTokenStartIndex;
				}
				word = inputStrAr[nextTokenStartIndex];
			}
		}		
		if(NUMBER_PATTERN.matcher(word).matches()){
			nextTokenStartIndex++;
			boolean conjAppended = false;
			sb.append(word);
			while(nextTokenStartIndex < inputStrArLen){
				String nextWord = inputStrAr[nextTokenStartIndex];
				ar = DASH_PATTERN.split(nextWord);
				if(ar.length > 1){
					int wordIndex1 = 0; 
					int temp = isCardinality(ar[0], ar, wordIndex1, emptyPair);
					if(temp > wordIndex1){
						if(conjAppended){
							sb.append(" and");	
							conjAppended = false;
						}
						sb.append(" ").append(nextWord);
						if(++nextTokenStartIndex >= inputStrArLen){
							break;
						}
					}
				}					
				if(NUMBER_PATTERN.matcher(inputStrAr[nextTokenStartIndex]).matches()){
					if(conjAppended){
						sb.append(" and");	
						conjAppended = false;
					}
					sb.append(" ").append(inputStrAr[nextTokenStartIndex]);
					nextTokenStartIndex++;				
				}else if("and".equals(inputStrAr[nextTokenStartIndex])){
					conjAppended = true;
					nextTokenStartIndex++;
				}else{
					break;
				}
			}
			if(conjAppended){
				nextTokenStartIndex--;
			}
			emptyPair.set_word(sb.toString());
			emptyPair.set_pos(QUANTITY_POS);
			//System.out.println("wordIndex " + wordIndex+ "  nextTokenStartIndex " + nextTokenStartIndex);
			return nextTokenStartIndex;
		}				
		return nextTokenStartIndex;		
	}
	
	/**
	 * Heuristic for determining if two latex expressions are similar 
	 * enough, to warrant them being grouped together via conjunction or
	 * disjunction. E.g. let $S$, $T$ be rings.
	 * Parameters *can* be wrapped inside $$ signs.
	 * Marks of similarity: 
	 * -one is a tilde or prime of another.
	 * @param tex1 
	 * @param tex2 
	 */
	public static boolean areTexExprSimilar(String tex1, String tex2){
		//if(true) throw new IllegalStateException("tex1: " + tex1 + " tex2 " + tex2);
		Matcher tex1Matcher = LATEX_PATTERN.matcher(tex1);
		Matcher tex2Matcher = LATEX_PATTERN.matcher(tex2);
		//if(true) throw new IllegalStateException();
		//strip $ $ signs.
		if(tex1Matcher.find() && tex2Matcher.find()){
			tex1 = tex1Matcher.group(1);
			tex2 = tex2Matcher.group(1);
		}else{
			return false;
		}
		
		int tex1Len = tex1.length();
		int tex2Len = tex2.length();
		
		//if tex1 and tex2 have the same "form". E.g. S, T.
		if(tex1Len < 15 && (tex1Len - tex2Len < 4 || tex2Len - tex1Len < 4) ){
			return true;
		}
		
		//if tex2 is a variation of tex1
		//create regex from one to match the other. Expand this!
		//turn '\' into "\\\\" to create legal regex
		//tex1 = BACKSLASH_PATTERN.matcher(tex1).replaceAll("\\\\");
		/*
		tex1 = BRACES_PATTERN.matcher(tex1).replaceAll("\\$1");		
		tex1 = Matcher.quoteReplacement(tex1);
		//escape special characters such as 
		//tex1 = SPECIAL_CHARS_PATTERN.matcher("tex1").replaceAll("\\$1");
		//if tex1 is a hat or tilde of another. But this is mostly superceded by previous comparison.
		String tex1Regex = "\\\\hat\\{" + tex1 + "\\}|" + tex1 + "'";		
		Pattern tex1Pattern = null;
		try{
			tex1Pattern = Pattern.compile(tex1Regex);
		}catch(java.util.regex.PatternSyntaxException e){
			//if some special character that hadn't been escaped shows up.
			logger.error(e.getStackTrace());
			return false;
		}		
		if(tex1Pattern.matcher(tex2).find()){
			return true;
		}
		*/
		return false;
	}
	
	/**
	 * Retrieves set of first words in the keys of the given map. Used in e.g.
	 * NGram gathering.
	 * @param map
	 * @return
	 */
	public static Set<String> gatherKeyFirstWordSetFromMap(Map<String, Integer> map){
		Set<String> set = new HashSet<String>();
		for(String s : map.keySet()){
			//gathering algorithm should have ensured that keys cannot be white spaces
			String firstWord = WHITE_NONEMPTY_SPACE_PATTERN.split(s)[0];
			set.add(firstWord);
		}
		return set;
	}
	
	/**
	 * Word Stems to various word forms with that stem.  
	 * @return
	 */
	public static ImmutableMultimap<String, String> stemToWordsMMap(){
		return stemToWordsMMap;
	}
	
	/**
	 * E.g. ".*assume.*|.*denote.*|.*define.*"
	 * 
	 * @return
	 */
	public static Pattern get_HYP_PATTERN(){
		return HYP_PATTERN;
	}

	/**
	 * @return the whitespacePattern. Including the case of 
	 * empty pattern.
	 */
	public static Pattern getWhiteEmptySpacePattern() {
		return ALL_WHITE_EMPTY_SPACE_PATTERN;
	}
	
	/**
	 * @return the whitespacePattern. Excluding the case of 
	 * empty pattern.
	 */
	public static Pattern getWhiteNonEmptySpacePattern() {
		return ALL_WHITE_NONEMPTY_SPACE_PATTERN;
	}
	
	/**
	 * @return the whitespacePattern. Excluding the case of 
	 * empty pattern. Don't need to be whole String.
	 */
	public static Pattern getWhiteNonEmptySpaceNotAllPattern() {
		return WHITE_NONEMPTY_SPACE_PATTERN;
	}

	public static Pattern getSINGLE_LINE_SKIP_PATTERN(){
		return SINGLE_LINE_SKIP_PATTERN;
	}
	
	public static Pattern getSKIP_PATTERN(){
		return SKIP_PATTERN;
	}
	
	public static Pattern getEND_SKIP_PATTERN(){
		return END_SKIP_PATTERN;
	}
	/**
	 * Pattern for expressions being a latex expressions, i.e. starting/ending with $.
	 * @return
	 */
	public static Pattern LATEX_ASSERT_PATTERN(){
		return LATEX_ASSERT_PATTERN;
	}
	
	public static Pattern getSPECIAL_CHARS_AROUND_WORD_PATTERN(){
		return SPECIAL_CHARS_AROUND_WORD_PATTERN;
	}
	
	public static Pattern SPACES_AROUND_TEXT_PATTERN(){
		return SPACES_AROUND_TEXT_PATTERN;
	}
	
	/**
	 * Strip spaces around input.
	 * @param input
	 * @return
	 */
	public static String stripSurroundingWhiteSpace(String input){
		Matcher matcher = SPACES_AROUND_TEXT_PATTERN.matcher(input);
		if(matcher.matches()){
			return matcher.group(1);
		}else{
			return input;
		}
	}
	
	/**
	 * Get the part of speech corresponding to the pos tag/symbol.
	 * E.g. i -> "pre". Placed here instead of in subclass, so it can
	 * be used by WordFrequency.java as well.
	 * @param word
	 * @param wordPos
	 * @return
	 */
	public static String getPosFromTagger(String wordPos){
		String pos;
		switch (wordPos) {
		case "IN":
			//adverbs and adj are sometimes interchanged by the 
			//Stanford pos tagger, in my opinion.
			pos = "pre";
			break;
		case "PRP":
			pos = "pro";
			break;
		case "VB":
			pos = "verb";
			break;
		case "VBP":
			pos = "verb";
			break;
		case "VBZ":
			pos = "vbs";
			break;
		case "NN":
			pos = "ent";
			break;
		case "NNP":
			//"Radon" in "Radon measure"
			pos = "ent";
			break;
		case "NNS":
			pos = "ent";
			break;
		case "JJ":
			pos = "adj";
			break;
		case "RB":
			pos = "adverb";
			break;
		case "DT":
			// determiner, e.g. "every", put adj for now.
			//for better grammar integration.
			pos = "adj";
			//pos = "det";
			break;
		case "CD":
			//symbol or num both categorized to "CD"
			pos = "symb";
			break;
		default:
			pos = "";
			//System.out.println("default pos: "+ word + " "+ lineAr[2]);
			// defaultList.add(lineAr[2]);
		}
		return pos;
	}
	
	public static Pattern getTexCommandBeginPattern() {
		return COMMAND_BEGIN_PATTERN;
	}

	public static Pattern getTexCommandEndPattern() {
		return COMMAND_END_PATTERN;
	}
	
	/**
	 * Word may start with escape char (slash).
	 * @param word
	 * @return
	 */
	public static boolean isGreekAlpha(String word){
		int wordLen = word.length();
		if(wordLen < 1) {
			return false;
		}
		if(word.charAt(0) == '\\'){
			word = word.substring(1);
		}
		if(GREEK_ALPHA_SET.contains(word)){
			return true;
		}
		return false;
	}

	/**
	 * Set of Greek alphabets. No slash at beginning.
	 * @return
	 */
	public static Set<String> GREEK_ALPHA_SET_NoSlash(){
		return GREEK_ALPHA_SET;
	}
	
	/**
	 * Comparator for words based on their frequencies in text corpus.
	 * 
	 */
	public static class WordFreqComparator implements Comparator<String>{
		
		Map<String, Integer> wordFreqMap;
		public WordFreqComparator(Map<String, Integer> map){
			this.wordFreqMap = map;
		}
		
		/**
		 * Higher freq ranked lower, so prioritized in sorting later.
		 */
		@Override
		public int compare(String s1, String s2){
			Integer freq1 = wordFreqMap.get(s1);
			Integer freq2 = wordFreqMap.get(s2);
			
			if(null != freq1){
				if(null != freq2){
					return freq1 > freq2 ? -1 : 1;
				}else{
					return -1;
				}
			}else{
				return 1;
			}
		}
	}
	
}
