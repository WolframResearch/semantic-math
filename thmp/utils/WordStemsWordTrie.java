package thmp.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class WordStemsWordTrie {

	private static final Pattern SPECIAL_CHARS_PATTERN = Pattern.compile(".*[-|\\\\|$|\\s].*");
	private static final Pattern WORD_EXTRACTION_PATTERN = Pattern.compile("(.*)\\s*");
	private static final Logger logger = LogManager.getLogger(WordStemsWordTrie.class);
	
	private static class TrieNode {

		// string of letters upstream up to this node
		String word;
		// frequency count of word in corpus.
		int wordCount;
		//total count of all children words, <= parentNode.wordCount, since
		//some words could have ended at parent node.
		int totalChildrenWordsCount;
		/* Counts of letters and integers, same as counts contained
		 * in children nodes. This is used to facilitate gathering stats,
		 * and avoid deleting and re-inserting node each time count is updated */
		private Map<Character, Integer> charCountsMap;

		//keys are letters
		private Map<Character, TrieNode> nodeMap;		

		/**
		 * @return the count map
		 */
		public Map<Character, Integer> getCharCountsMap() {
			return charCountsMap;
		}
		
		/**
		 * @return the word
		 */
		public String getWord() {
			return word;
		}

		/**
		 * @return the wordCount
		 */
		public int getWordCount() {
			return wordCount;
		}

		public TrieNode(String word_){
			this.word = word_;
			wordCount = 1;
			nodeMap = new HashMap<Character, TrieNode>();
			charCountsMap = new HashMap<Character, Integer>();
		}
		
		/**
		 * Increase the word count by 1.
		 */
		public void incrementWordCount(){
			wordCount++;
		}
		
		/**
		 * Increase the word count by 1.
		 */
		public void incrementTotalChildrenWordsCount(){
			totalChildrenWordsCount++;
		}
		
		@Override
		public String toString(){
			return this.word + " " + this.wordCount;
		}
	}

	/* Hash structure for tree heads */
	private TrieNode headNode;
	
	public WordStemsWordTrie(){
		headNode = new TrieNode("");
	}
	
	/**
	 * Get the stem words from this trie, which are
	 * determined if there is sufficient dispersion
	 * amongst its children nodes. This operation is 
	 * expensive! As it needs to sort through list at each node.
	 * This is better than sorting at insertion, because there are
	 * a lot of word insertions, so N * avg length * log 26
	 * number of comparisons.
	 * Cache result?! Should know to re-compute after trie changes!
	 * @param totalInsertedWords total number of words inserted
	 * @return
	 */
	public List<String> getStemWords(int totalInsertedWords,
			Map<String, String> longShortFormsMap){
		List<String> stemWordsList = new ArrayList<String>();
		getStemWords(totalInsertedWords, stemWordsList, this.headNode, longShortFormsMap);
		return stemWordsList;
	}
	
	/**
	 * 
	 * @param stemWordsList
	 * @param node
	 * @param longShortFormsMap map where keys are long forms, values are short forms, 
	 */
	private void getStemWords(int avgInsertedWordsCount, 
			List<String> stemWordsList, TrieNode node, Map<String, String> longShortFormsMap){
		//System.out.println("node.getCharCountsMap(): " + node.getCharCountsMap());
		/*if(node.word.equals("functorial")){
			System.out.println("node word count: " + node.wordCount +"totalChildrenWordsCount for functorial: " + node.totalChildrenWordsCount);
			System.out.println("nodeMap for functorial: " + node.nodeMap);
		}*/
		List<Integer> countsList = new ArrayList<Integer>(node.getCharCountsMap().values());
		int countsListSz = countsList.size();
		String nodeWord = node.word;
		//System.out.println("countsListSz: " + countsListSz);
		//if word has sufficient length
		if(countsListSz > 2 && nodeWord.length() > 4){			
			int totalChildrenWordCount = node.totalChildrenWordsCount;
			int nodeWordCount = node.wordCount;
			//System.out.println("node word: " + node.word);
			//heuristic for sufficient dispersion amongst the 26 letter nodes.
			//count the case where the word ends here as a child.
			if( (nodeWordCount - totalChildrenWordCount) < nodeWordCount*3/4					
					//&& countsList.get(countsListSz-2) > nodeWordCount/5.5
					//sufficiently frequent words
					//&& 					
					){
				//Collections.sort(countsList);
				//get max from this list of integers
				int maxChildWordCount = DataUtility.findMax(countsList);
					//above certain percentage, so there is no clear winner that's so much above all others.
					if(maxChildWordCount > nodeWordCount/5){
						stemWordsList.add(nodeWord);
						int ancestorNodeWordLen = node.word.length();
						//get the most frequent descendants, any child above 1/4 frequency count
						List<String> descendantList = new ArrayList<String>();
						getFrequentDescendants(avgInsertedWordsCount, node, ancestorNodeWordLen, descendantList);
						if(descendantList.size() > 1){
							for(String longForm : descendantList){
								longShortFormsMap.put(longForm, nodeWord);
							}
						}
					}
			}
		}		
		for(TrieNode childNode : node.nodeMap.values()){			
 			getStemWords(avgInsertedWordsCount, stemWordsList, childNode, longShortFormsMap);
		}
	}
	
	/**
	 * Get the most frequent descendants, any child above 1/4 total children count.
	 * Get the complete words, e.g. "bounded" and not "bounde"
	 * @param node
	 * @param ancestorNodeWordLen Length of ancestor word, only add if descendant & 
	 * ancestor word lengths do not vary too much. e.g. equid should not be the shortcut for
	 * both equidimensional and equidistant
	 * @param total number of siblings
	 * @return
	 */
	private void getFrequentDescendants(int avgInsertedWordsCount, 
			TrieNode node, int ancestorNodeWordLen, List<String> descendantList) {

		int nodeTotalChildrenWordsCount = node.totalChildrenWordsCount;
		int nodeWordCount = node.wordCount;
		//System.out.println(node.word +" node.wordCount*10/12 " + node.wordCount*10/12 + " nodeTotalChildrenWordsCount " + nodeTotalChildrenWordsCount);
		//this check along is not sufficient, as stem words often are not complete words, e.g. "associat" without the 'e'
		if(nodeWordCount*10/12 > nodeTotalChildrenWordsCount && nodeWordCount > avgInsertedWordsCount/15
				&& node.word.length() < ancestorNodeWordLen+6){
			descendantList.add(node.word);
		}
		
		for(TrieNode childNode : node.nodeMap.values()){
			if(childNode.wordCount > nodeTotalChildrenWordsCount/6){
				getFrequentDescendants(avgInsertedWordsCount, childNode, ancestorNodeWordLen, descendantList);
			}
		}
	}

	/**
	 * Reverse the natural integer ordering 
	 */
	private static class CountsComparator implements Comparator<Integer>{
		
		@Override
		public int compare(Integer int1, Integer int2){
			return int1 < int2 ? 1 : (int1 < int2 ? -1 : 0); 
		}
	}
	
	/**
	 * Inserts new word to the trie. This walks down the trie 
	 * and updates the word counts. Words should be pre-processed,
	 * any word containing any chracter other than the 26 alphabet 
	 * letters will not get added to the trie.
	 * @param str
	 */
	public void insertWord(String newWord){		
		if(SPECIAL_CHARS_PATTERN.matcher(newWord).matches()){
			return;
		}
		TrieNode curNode = this.headNode;
		StringBuilder wordThusFarSB = new StringBuilder();
		for(int i = 0; i < newWord.length(); i++){
			curNode.incrementTotalChildrenWordsCount();
			char curChar = newWord.charAt(i);
			//allow white space to get N grams.
			//if (' ' == curChar) continue;
			wordThusFarSB.append(curChar);
			/*if(wordThusFarSB.toString().equals("bound")){
				System.out.println("inserting bound: ");
			}*/
			//maps are likely to contain the key than not, so get the key
			Map<Character, TrieNode> curNodeMap = curNode.nodeMap;
			Map<Character, Integer> charCountsMap = curNode.charCountsMap;
			curNode = curNodeMap.get(curChar);
			if(null != curNode){
				curNode.incrementWordCount();
			}else{
				curNode = new TrieNode(wordThusFarSB.toString());
				curNodeMap.put(curChar, curNode);
			}			
			//System.out.println("curNodeMap: " + curNodeMap);
			int wordCount = charCountsMap.getOrDefault(curChar, 0);
			charCountsMap.put(curChar, wordCount+1);
			//System.out.println("charCountsMap: " + charCountsMap);
		}
		//insert \newline character to indicate end of word
		//char newLine = '\n';		
	}

	/**
	 * Generate a map with long forms of words as keys, and their abbreviations
	 * as values. Serializes map.
	 */
	public static void serializeStemWordsMap(String serializationFileStr, String serializationStrFileStr){

		WordStemsWordTrie wordTrie = new WordStemsWordTrie();
		Set<String> wordSet = new HashSet<String>();
		BufferedReader br = null;
		String line;
		/*String bound = "bound";
		for(int i = 0; i < bound.length(); i++){
			System.out.print((int)bound.charAt(i) + "\t ");
		}*/
		//File fileDir = new File("src/thmp/data/skipGramWordsList2.txt");
		//File fileDir = new File("src/thmp/data/allThmWordsList.txt");
		File wordsFile = new File("src/thmp/data/skipGramWordsList2.txt");
		InputStreamReader wordsFileReader = null ;
		try{
			wordsFileReader = new InputStreamReader(new FileInputStream(wordsFile), "UTF-16");			
		}catch(IOException e){
			String msg = "IOException while opening input stream! " + e.getMessage();
			logger.info(msg);
			throw new IllegalStateException(msg);
		}
		br= new BufferedReader(wordsFileReader);
		int totalInsertedWords = 0;
		try{
			while(null != (line = br.readLine())){
				//if("bound\n".equals(line)){
					//System.out.println("should be bound: " + line);					
				//}				
				/*System.out.println("line: " + line+ " " +line.matches("\\s*bound\\s*"));
				for(int i = 0; i < line.length(); i++){
					System.out.print((int)line.charAt(i) + "\t ");
				}*/
				Matcher m = WORD_EXTRACTION_PATTERN.matcher(line);
				if(m.matches()){				
					String word = m.group(1);					
					wordTrie.insertWord(word);
					wordSet.add(word);
				}else{
					wordTrie.insertWord(line);				
					wordSet.add(line);
				}
				totalInsertedWords++;
			}
		}catch(IOException e){
			throw new IllegalStateException(e);
		}finally{
			FileUtils.silentClose(br);
		}
		Map<String, String> longShortFormsMap = new HashMap<String, String>();
		int avgInsertedWordsCount = (int)(totalInsertedWords/wordSet.size());
		System.out.println("avgInsertedWordsCount: " + avgInsertedWordsCount);
		wordTrie.getStemWords(avgInsertedWordsCount, longShortFormsMap);
		
		FileUtils.writeToFile(longShortFormsMap, serializationStrFileStr);
		List<Map<String, String>> stemWordsMapList = new ArrayList<Map<String, String>>();		
		stemWordsMapList.add(longShortFormsMap);	
		FileUtils.serializeObjToFile(stemWordsMapList, serializationFileStr);
		System.out.println("Total number of words abbreviated: " + longShortFormsMap.size());
	}	
	
	public static void main(String[] args){
		
		boolean serializeBool = true;
		if(serializeBool){
			String serializationFileStr = "src/thmp/data/stemWordsMap.dat";
			String serializationStrFileStr = "src/thmp/data/stemWordsMap.txt";
			serializeStemWordsMap(serializationFileStr, serializationStrFileStr);
		}
		
		//System.out.println();
		//System.out.println("WordTrie - " + longShortFormsMap);		
	}
}
