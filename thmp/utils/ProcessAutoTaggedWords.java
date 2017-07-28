package thmp.utils;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;

/**
 * Process word-pos pairs that were automatically tagged 
 * during parsing, to add likely correct pairs to the lexicon.
 * Internal tool used for collecting pos, to be run locally only.
 * 
 * @author yihed
 *
 */
public class ProcessAutoTaggedWords {
	
	private static final Map<String, Multiset<String>> wordPosMSetMap;
	private static final String wordPosFilePath = "src/thmp/data/unknownWords2.txt";
	private static final Multimap<String, String> acceptedWordPosMMap;
	private static final Multimap<String, String> rejectedWordPosMMap;
	private static final int POS_COUNT_THRESHOLD = 12; //3
	
	static{
		wordPosMSetMap = new HashMap<String, Multiset<String>>();
		acceptedWordPosMMap = ArrayListMultimap.create();
		rejectedWordPosMMap = ArrayListMultimap.create();
	}
	
	public static void main(String[] args) throws FileNotFoundException{
		String filePath = wordPosFilePath;
		if(args.length > 0){
			filePath = args[0];
		}
		addWordPosPairsToMap(filePath);
		processWordPosMap();
		//System.out.println("acceptedWordPosMMap " + acceptedWordPosMMap);
		//System.out.println("rejectedWordPosMMap " + rejectedWordPosMMap);
	}

	//remove ones with special chars!!
	
	private static void processWordPosMap() throws FileNotFoundException {
		//file path to write pos to
		String destFilePath = "src/thmp/data/unknownWordsOutput.txt";
		FileOutputStream outStream = new FileOutputStream(destFilePath);
		BufferedOutputStream bOutStream = new BufferedOutputStream(outStream);
		OutputStreamWriter outWriter = new OutputStreamWriter(bOutStream);
		int i = 1000;
		for(Map.Entry<String, Multiset<String>> wordPosEntry : wordPosMSetMap.entrySet()){
			if(i-- < 0) break;
			String word = wordPosEntry.getKey();
			Multiset<String> posMSet = wordPosEntry.getValue();
			for(Multiset.Entry<String> msetEntry : posMSet.entrySet()){
				String pos = msetEntry.getElement();
				int posCount = msetEntry.getCount();
				int wordLen = word.length();
				//many false positive three-letter words.
				if(wordLen < 4){
					continue;
				}
				if(posCount > POS_COUNT_THRESHOLD || ((pos.equals("adj") || pos.equals("ent")) &&
						posCount > POS_COUNT_THRESHOLD*(2.0/3)) ){
					if((pos.equals("ent") || pos.equals("verb")) && word.charAt(wordLen-1) == 's'){
						word = WordForms.getSingularForm(word);
					}
					acceptedWordPosMMap.put(word, pos);
					try {
						outWriter.write(word + " " + pos + "\n");
					} catch (IOException e) {						
						e.printStackTrace();
						FileUtils.silentClose(outWriter);
						return;
					}
					//System.out.println(word + " " + pos);
				}else{
					rejectedWordPosMMap.put(word, pos);
				}
			}
		}
		FileUtils.silentClose(outWriter);
	}

	/**
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	private static void addWordPosPairsToMap(String filePath) {
		BufferedReader br;
		try {
			br = new BufferedReader(new FileReader(filePath));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return;
		}
		String line;
		int i = 6000;
		try {
			while((line=br.readLine()) != null){
				if(i-- < 0) break;
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
