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
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WordTrie {

	private static final Pattern SPECIAL_CHARS_PATTERN = Pattern.compile(".*[-|\\\\|$].*");
	private static final Pattern WORD_EXTRACTION_PATTERN = Pattern.compile("(.*)\\s*");
	
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
	
	public WordTrie(){
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
	 * @return
	 */
	public List<String> getStemWords(){
		List<String> stemWordsList = new ArrayList<String>();
		getStemWords(stemWordsList, headNode);
		return stemWordsList;
	}
	
	private void getStemWords(List<String> stemWordsList, TrieNode node){
		//System.out.println("node.getCharCountsMap(): " + node.getCharCountsMap());
		if(node.word.equals("bound")){
			System.out.println(" for bound: " + node.totalChildrenWordsCount);
			System.out.println("nodeMap for bound: " + node.nodeMap);
		}
		List<Integer> countsList = new ArrayList<Integer>(node.getCharCountsMap().values());
		int countsListSz = countsList.size();
		//System.out.println("countsListSz: " + countsListSz);
		if(countsListSz > 2){
			Collections.sort(countsList);
			int totalChildrenWordCount = node.totalChildrenWordsCount;
			//System.out.println("node word: " + node.word);
			//heuristic for sufficient dispersion amongst the 26 letter nodes.
			if(countsList.get(countsListSz-1) < totalChildrenWordCount*2/3
					&& countsList.get(countsListSz-1) > totalChildrenWordCount/4
					&& countsList.get(countsListSz-2) < totalChildrenWordCount*6/12
					//|| or some words end here 					
					){
				stemWordsList.add(node.word);
			}		
		}
		
		for(TrieNode childNode : node.nodeMap.values()){			
 			getStemWords(stemWordsList, childNode);
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
			if (' ' == curChar) continue;
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

	public static void main(String[] args){
		//String fileStr = "src/thmp/data/";
		FileReader fileReader = null;
		try{
			fileReader = new FileReader("src/thmp/data/skipGramWordsList0.txt");
		}catch(FileNotFoundException e){
			throw new IllegalStateException(e);
		}
		WordTrie wordTrie = new WordTrie();
		BufferedReader br = new BufferedReader(fileReader);
		String line;
		/*String bound = "bound";
		for(int i = 0; i < bound.length(); i++){
			System.out.print((int)bound.charAt(i) + "\t ");
		}*/
		//File fileDir = new File("src/thmp/data/skipGramWordsList2.txt");
		File fileDir = new File("src/thmp/data/allThmWordsList.txt");
		
		InputStreamReader re = null ;
		try{
			re = new InputStreamReader(new FileInputStream(fileDir), "UTF-16");
			br= new BufferedReader(re);
		}catch(IOException e){
			
		}
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
				}else{
					wordTrie.insertWord(line);
					
				}
			}
		}catch(IOException e){
			throw new IllegalStateException(e);
		}finally{
			FileUtils.silentClose(br);
		}
		System.out.println(wordTrie.getStemWords());
	}
}
