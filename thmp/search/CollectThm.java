package thmp.search;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;

import thmp.ProcessInput;
import thmp.ThmInput;

/**
 * Collects thms by reading in thms from Latex files. Gather
 * keywords from each thm. 
 * Keep map of common English words that should not be used
 * in mathObjMx. 
 * Maintains a Multimap of keywords and the theorems they are in, 
 * in particular their indices in thmList.
 * Build a structure that TriggerMathThm2 can use to build its
 * mathObjMx.
 * 
 * @author yihed
 *
 */
public class CollectThm {

	//initialized in static block. List of theorems, each of which
	//contains map of keywords and their frequencies in this theorem. 
	//the more frequent words in a thm should be weighed up, but the
	//ones that are frequent in the whole doc weighed down.
	private static final ImmutableList<ImmutableMap<String, Integer>> thmWordsList;
	//Map of frequent words and their parts of speech (from words file data). Don't need the pos for now.
	private static final ImmutableMap<String, String> freqWordsMap; 
	//document-wide word frequency. Keys are words, values are counts in whole doc.
	private static final ImmutableMap<String, Integer> docWordsFreqMap;
	//raw original file
	private static final File rawFile = new File("src/thmp/data/commAlg4.txt");
	//file to read from. Thms already extracted, ready to be processed.
	private static File thmFile = new File("src/thmp/data/thmFile4.txt");
	//list of theorems, in order their keywords are added to thmWordsList
	private static final ImmutableList<String> thmList;
	//Multimap of keywords and the theorems they are in, in particular their indices in thmList
	private static final ImmutableMultimap<String, Integer> wordThmsMMap;
	
	static{
		//should only get the top 100 words! *******
		freqWordsMap = CollectFreqWords.get_wordPosMap();
		//pass builder into a reader function. For each thm, builds immutable list of keywords, 
		//put that list into the thm list.
		ImmutableList.Builder<ImmutableMap<String, Integer>> thmWordsListBuilder = ImmutableList.builder();
		ImmutableList.Builder<String> thmListBuilder = ImmutableList.builder();
		Map<String, Integer> docWordsFreqPreMap = new HashMap<String, Integer>();
		ImmutableListMultimap.Builder<String, Integer> wordThmsMMapBuilder = ImmutableListMultimap.builder();
		
		try{
			readThm(thmWordsListBuilder, thmListBuilder, docWordsFreqPreMap, wordThmsMMapBuilder);
		}catch(IOException e){
			e.printStackTrace();
		}
		
		thmWordsList = thmWordsListBuilder.build();	
		thmList = thmListBuilder.build();
		docWordsFreqMap = ImmutableMap.copyOf(docWordsFreqPreMap); 
		wordThmsMMap = wordThmsMMapBuilder.build();
	}
	
	private static void readThm(ImmutableList.Builder<ImmutableMap<String, Integer>> thmWordsListBuilder,
			ImmutableList.Builder<String> thmListBuilder, Map<String, Integer> docWordsFreqPreMap,
			ImmutableListMultimap.Builder<String, Integer> wordThmsMMapBuilder)
			throws IOException, FileNotFoundException{
		
		//use method in ProcessInput to process in thms. 
		List<String> thmList = ProcessInput.processInput(thmFile, true);
		//adds original thms without latex replaced, should be in same order as above
		thmListBuilder.addAll(ThmInput.readThm(rawFile));
		
		//processes the theorems, select the words
		for(int i = 0; i < thmList.size(); i++){
			String thm = thmList.get(i);
			//System.out.println("++THM: " + thm);
			Map<String, Integer> thmWordsMap = new HashMap<String, Integer>();
			//trim chars such as , : { etc.  
			String[] thmAr = thm.toLowerCase().split("\\s+|\'|\\(|\\)|\\{|\\}|\\[|\\]|\\.|\\;|\\,|:");
			
			for(String word : thmAr){
				//only keep words with lengths > 2
				if(word.length() < 3 || freqWordsMap.containsKey(word)) continue;
				int wordFreq = thmWordsMap.containsKey(word) ? thmWordsMap.get(word) : 0;
				thmWordsMap.put(word, wordFreq + 1);
				int docWordFreq = docWordsFreqPreMap.containsKey(word) ? thmWordsMap.get(word) : 0;
				docWordsFreqPreMap.put(word, docWordFreq + 1);				
				wordThmsMMapBuilder.put(word, i);
			}
			thmWordsListBuilder.add(ImmutableMap.copyOf(thmWordsMap));
			//System.out.println("++THM: " + thmWordsMap);
		}
	}
	
	public static ImmutableList<ImmutableMap<String, Integer>> get_thmWordsList(){
		return thmWordsList;
	}
	
	/**
	 * Retrieves list of thms.
	 * @return
	 */
	public static ImmutableList<String> get_thmList(){
		return thmList;
	}
	
	/**
	 * Retrieves ImmutableListMultimap of words and the theorems's indices in thmList
	 *  they appear in.
	 * @return
	 */
	public static ImmutableMultimap<String, Integer> get_wordThmsMMap(){
		return wordThmsMMap;
	}
	
	/**
	 * Retrieves map of words with their document-wide frequencies.
	 * @return
	 */
	public static ImmutableMap<String, Integer> get_docWordsFreqMap(){
		return docWordsFreqMap;
	}
	
	/**
	 * Tests the methods here.
	 * 
	 * @param args
	 */
	public static void main(String[] args){
		
	}
}
