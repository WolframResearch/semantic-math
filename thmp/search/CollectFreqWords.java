package thmp.search;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * Read in the most frequently-used words from file, 
 * along with their POS
 *
 */
public class CollectFreqWords {

	//Map of words and their pos.
	//entrySet of immutable maps preserve the order the entries are inserted,
	//so to get the top N words, just iterate the top N entries.
	private static final ImmutableMap<String, String> wordPosMap;
	//this list contains 5000 most frequent words, ordered by freq. Oftentimes we need fewer than those,
	//maybe only top 500, so words such as "ring" don't get screened out.
	private static File wordsFile = new File("src/thmp/data/wordFrequency.txt");
	private static final Path nonMathWordsFilePath = new File("src/thmp/data/wordFrequency.txt");
	
	static{
		Map<String, String> wordPosPreMap = new HashMap<String, String>();
		
		//pass premap into file
		try{
			readWords(wordPosPreMap);
		}catch(FileNotFoundException e){
			e.printStackTrace();
		}
		
		wordPosMap = ImmutableMap.copyOf(wordPosPreMap);
	}
	
	private static void readWords(Map<String, String> wordPosPreMap) throws FileNotFoundException{
		Scanner sc = new Scanner(wordsFile);
		//skip first line with header info
		sc.nextLine();
		List<String> defaultList = new ArrayList<String>();
		
		while(sc.hasNextLine()){
			String line = sc.nextLine();
			String[] lineAr = line.split("\\s+");
			//2nd is word, 3rd is pos
			String word = lineAr[1].trim();
			
			String pos;
			switch(lineAr[2].trim()){
			case "i":
				pos = "pre";
				break;
			case "p":
				pos = "pro";
				break;
			case "v":
				pos = "verb";
				break;
			case "n":
				pos = "noun";
				break;
			case "x":
				//not, no etc
				pos = "not";
				break;
			case "d":
				//determiner
				pos = "det";
				break;
			case "j":
				pos = "adj";
				break;
			case "r":
				pos = "adverb";
				break;
			case "e":
				//"existential there"
				pos = "det";
				break;
			case "a":
				//article, eg the, every, a.
				//classify as adj because of the rules for
				//fusing adj and ent's
				pos = "adj";
				break;
			case "m":
				pos = "num";
				break;
			case "u":
				//interjection, eg oh, yes, um.
				pos = "intj";
				break;
			case "c":
				//conjunctions, eg before, until, although
				//and/or should be parsed as conj/disj, will
				//be overwritten in Maps.java
				pos = "con";				
				break;
			default:
				pos = word;
				System.out.println("default pos: " + lineAr[2]);
				defaultList.add(lineAr[2]);
			}
			
			wordPosPreMap.put(word, pos);
			//System.out.println(word + " " + pos);			
		}
		
		//System.out.println("LIST: " + defaultList);
		sc.close();
	}
	
	public static ImmutableMap<String, String> get_wordPosMap(){
		return wordPosMap;
	}
	
	
	/**
	 * Gets only the non math words.
	 * @return
	 */
	private static ImmutableSet<String> write_nonMathWords(){
		
		ImmutableMap<String, Integer> docWordsFreqMap = CollectThm.get_docWordsFreqMapNoAnno();
		
		for(){
			
		}
		
	}
	
	/**
	 * Returns the top most frequent words.
	 * @param K is number of most frequent words to use.
	 * @return 
	 */
	public static ImmutableMap<String, String> getTopFreqWords(int K){
		Map<String, String> freqWordsMap = new HashMap<String, String>();
		int counter = K;
		for(Entry<String, String> wordEntry : wordPosMap.entrySet()){
			if(counter == 0) break;
			freqWordsMap.put(wordEntry.getKey(), wordEntry.getValue());
			counter--;
		}
		return ImmutableMap.copyOf(freqWordsMap);
	}
	
	/**
	 * Tests the methods here.
	 * 
	 * @param args
	 */
	public static void main(String[] args){
		
	}
}
