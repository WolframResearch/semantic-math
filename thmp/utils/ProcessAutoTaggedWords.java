package thmp.utils;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;

/**
 * Process word-pos pairs that were automatically tagged 
 * during parsing, to add likely correct pairs to the lexicon.
 * 
 * @author yihed
 *
 */
public class ProcessAutoTaggedWords {
	
	private static final Map<String, Multiset<String>> wordPosMSetMap;
	private static final String wordPosFilePath = "src/thmp/data/unknownWords2.txt";
	private static final Multimap<String, String> acceptedWordPosMMap;
	private static final Multimap<String, String> rejectedWordPosMMap;
	private static final int POS_COUNT_THRESHOLD = 3;
	
	static{
		wordPosMSetMap = new HashMap<String, Multiset<String>>();
		acceptedWordPosMMap = ArrayListMultimap.create();
		rejectedWordPosMMap = ArrayListMultimap.create();
	}
	
	public static void main(String[] args){
		
		addWordPosPairsToMap();
		processWordPosMap();
		System.out.println("acceptedWordPosMMap " + acceptedWordPosMMap);
		System.out.println("rejectedWordPosMMap " + rejectedWordPosMMap);
	}

	private static void processWordPosMap() {
		for(Map.Entry<String, Multiset<String>> wordPosEntry : wordPosMSetMap.entrySet()){
			String word = wordPosEntry.getKey();
			Multiset<String> posMSet = wordPosEntry.getValue();
			for(Multiset.Entry<String> msetEntry : posMSet.entrySet()){
				String pos = msetEntry.getElement();
				int posCount = msetEntry.getCount();
				if(posCount > POS_COUNT_THRESHOLD || pos.equals("adj") || pos.equals("ent") ){
					
					int wordLen = word.length();
					if((pos.equals("ent") || pos.equals("verb")) && word.charAt(wordLen-1) == 's'){
						word = word.substring(0, wordLen-1);
					}
					acceptedWordPosMMap.put(word, pos);
					System.out.println(word + " " + pos);
				}else{
					rejectedWordPosMMap.put(word, pos);
				}
			}
		}
	}

	/**
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	private static void addWordPosPairsToMap() {
		BufferedReader br;
		try {
			br = new BufferedReader(new FileReader(wordPosFilePath));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return;
		}
		String line;
		try {
			while((line=br.readLine()) != null){
				String[] lineAr = WordForms.getWhiteNonEmptySpaceNotAllPattern().split(line);
				int lineArLen = lineAr.length;
				if(lineArLen != 2){
					continue;
				}
				String word = lineAr[0];
				if(word.length() < 3){
					continue;
				}
				if(WordForms.SPECIAL_CHARS_PATTERN.matcher(word).matches()){
					continue;
				}							
				String pos = lineAr[1];
				Multiset<String> wordPosSet = wordPosMSetMap.get(word);
				if(null != wordPosSet){
					wordPosSet.add(pos);
				}else{
					wordPosSet = HashMultiset.create();
					wordPosSet.add(pos);
					wordPosMSetMap.put(word, wordPosSet);
				}				
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
}
