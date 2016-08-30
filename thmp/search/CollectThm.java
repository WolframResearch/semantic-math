package thmp.search;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;

import thmp.ProcessInput;
import thmp.ThmInput;
import thmp.search.SearchWordPreprocess.WordWrapper;

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
	//private static final File thmFile = new File("src/thmp/data/thmFile5.txt");
	//list of theorems, in order their keywords are added to thmWordsList
	private static final ImmutableList<String> thmList;
	//Multimap of keywords and the theorems they are in, in particular their indices in thmList
	private static final ImmutableMultimap<String, Integer> wordThmsMMap;
	/**Versions without annotations***/
	private static final ImmutableList<ImmutableMap<String, Integer>> thmWordsListNoAnno;
	private static final ImmutableMap<String, Integer> docWordsFreqMapNoAnno;
	private static final ImmutableMultimap<String, Integer> wordThmsMMapNoAnno;

	/**
	 * Map of (annotated with "hyp" etc) keywords and their scores in document, the higher freq in doc, the lower 
	 * score, say 1/(log freq + 1) since log 1 = 0. 
	 */
	private static final ImmutableMap<String, Integer> wordsScoreMap;	
	private static final ImmutableMap<String, Integer> wordsScoreMapNoAnno;	
	//The number of frequent words to take
	private static final int NUM_FREQ_WORDS = 300;
	
	static{
		//only get the top N words
		//freqWordsMap = CollectFreqWords.get_wordPosMap();
		freqWordsMap = CollectFreqWords.getTopFreqWords(NUM_FREQ_WORDS);
		//pass builder into a reader function. For each thm, builds immutable list of keywords, 
		//put that list into the thm list.
		ImmutableList.Builder<ImmutableMap<String, Integer>> thmWordsListBuilder = ImmutableList.builder();
		ImmutableList.Builder<String> thmListBuilder = ImmutableList.builder();
		Map<String, Integer> docWordsFreqPreMap = new HashMap<String, Integer>();
		ImmutableListMultimap.Builder<String, Integer> wordThmsMMapBuilder = ImmutableListMultimap.builder();
		
		/**Versions with no annotation, eg "hyp"/"stm" **/
		ImmutableList.Builder<ImmutableMap<String, Integer>> thmWordsListBuilderNoAnno = ImmutableList.builder();
		//ImmutableList.Builder<String> thmListBuilderNoAnno = ImmutableList.builder();
		Map<String, Integer> docWordsFreqPreMapNoAnno = new HashMap<String, Integer>();
		ImmutableListMultimap.Builder<String, Integer> wordThmsMMapBuilderNoAnno = ImmutableListMultimap.builder();
		
		try{
			List<String> extractedThms = ThmInput.readThm(rawFile);
			thmListBuilder.addAll(extractedThms);
			
			List<String> thmList = ProcessInput.processInput(extractedThms, true);
			
			readThm(thmWordsListBuilder, docWordsFreqPreMap, wordThmsMMapBuilder, thmList);
			//buid maps without annocation
			buildMapsNoAnno(thmWordsListBuilderNoAnno, docWordsFreqPreMapNoAnno, wordThmsMMapBuilderNoAnno, thmList);
		}catch(IOException e){
			e.printStackTrace();
		}
		
		thmWordsList = thmWordsListBuilder.build();	
		thmList = thmListBuilder.build();
		docWordsFreqMap = ImmutableMap.copyOf(docWordsFreqPreMap); 
		wordThmsMMap = wordThmsMMapBuilder.build();
		
		thmWordsListNoAnno = thmWordsListBuilder.build();	
		docWordsFreqMapNoAnno = ImmutableMap.copyOf(docWordsFreqPreMap); 
		wordThmsMMapNoAnno = wordThmsMMapBuilder.build();
		
		System.out.println(thmList.size());
		
		//builds scoresMap based on frequency map obtained from CollectThm.
		ImmutableMap.Builder<String, Integer> wordsScoreMapBuilder = ImmutableMap.builder();		
		buildScoreMap(wordsScoreMapBuilder);
		wordsScoreMap = wordsScoreMapBuilder.build();
		
		ImmutableMap.Builder<String, Integer> wordsScoreMapBuilderNoAnno = ImmutableMap.builder();
		buildScoreMapNoAnno(wordsScoreMapBuilderNoAnno);
		wordsScoreMapNoAnno = wordsScoreMapBuilderNoAnno.build();
	}
	
	/**
	 * the frequency of bare words, without annocation such as H or C attached, is 
	 * equal to the sum of the frequencies of all the annotated forms, ie words with
	 * either H or C attached. eg freq(word) = freq(Cword)+freq(Hword). But when searching,
	 * annotated versions (with H/C) will get score bonus.
	 * Actually, should not put duplicates, all occurring words should have annotation, if one
	 * does not fit, try other annotations, just without the bonus points.
	 * @param thmWordsListBuilder
	 * @param thmListBuilder
	 * @param docWordsFreqPreMap
	 * @param wordThmsMMapBuilder
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	private static void readThm(ImmutableList.Builder<ImmutableMap<String, Integer>> thmWordsListBuilder,
			Map<String, Integer> docWordsFreqPreMap,
			ImmutableListMultimap.Builder<String, Integer> wordThmsMMapBuilder, List<String> thmList)
			throws IOException, FileNotFoundException{
		
		//processes the theorems, select the words
		for(int i = 0; i < thmList.size(); i++){
			String thm = thmList.get(i);
			
			if(thm.matches("\\s*")) continue;
			
			//System.out.println("++THM: " + thm);
			Map<String, Integer> thmWordsMap = new HashMap<String, Integer>();
			//trim chars such as , : { etc.  
			//String[] thmAr = thm.toLowerCase().split("\\s+|\'|\\(|\\)|\\{|\\}|\\[|\\]|\\.|\\;|\\,|:");
			//process words and give them WordWrappers, taking into account context.
			
			List<WordWrapper> wordWrapperList = SearchWordPreprocess.sortWordsType(thm);			
			//System.out.println(wordWrapperList);
			
			for(int j = 0; j < wordWrapperList.size(); j++){
				WordWrapper curWrapper = wordWrapperList.get(j);
				String word = curWrapper.word();
				String wordLong = curWrapper.hashToString();
				//the two frequencies are now kept separate!
				
				//only keep words with lengths > 2
				//System.out.println(word);
				if(word.length() < 3 || freqWordsMap.containsKey(word)) continue;
				
				//int wordFreq = thmWordsMap.containsKey(word) ? thmWordsMap.get(word) : 0;
				int wordLongFreq = thmWordsMap.containsKey(wordLong) ? thmWordsMap.get(wordLong) : 0;
				//thmWordsMap.put(word, wordFreq + 1);
				thmWordsMap.put(wordLong, wordLongFreq + 1);
				
				//int docWordFreq = docWordsFreqPreMap.containsKey(word) ? docWordsFreqPreMap.get(word) : 0;
				int docWordLongFreq = docWordsFreqPreMap.containsKey(wordLong) ? docWordsFreqPreMap.get(wordLong) : 0;				
				//increase freq of word by 1
				//docWordsFreqPreMap.put(word, docWordFreq + 1);
				docWordsFreqPreMap.put(wordLong, docWordLongFreq + 1);
				
				//put both original and long form.
				//wordThmsMMapBuilder.put(word, i);
				wordThmsMMapBuilder.put(wordLong, i);
			}
			thmWordsListBuilder.add(ImmutableMap.copyOf(thmWordsMap));
			//System.out.println("++THM: " + thmWordsMap);
		}
	}
	
	/**
	 * Same as readThm, except without hyp/concl wrappers.
	 * @param thmWordsListBuilder
	 * @param thmListBuilder
	 * @param docWordsFreqPreMap
	 * @param wordThmsMMapBuilder
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	private static void buildMapsNoAnno(ImmutableList.Builder<ImmutableMap<String, Integer>> thmWordsListBuilder,
			Map<String, Integer> docWordsFreqPreMap,
			ImmutableListMultimap.Builder<String, Integer> wordThmsMMapBuilder, List<String> thmList)
			throws IOException, FileNotFoundException{
		
		//use method in ProcessInput to process in thms. Like turn $blah$ -> $tex$
		//adds original thms without latex replaced, should be in same order as above
		/*List<String> extractedThms = ThmInput.readThm(rawFile);
		thmListBuilder.addAll(extractedThms);
		List<String> thmList = ProcessInput.processInput(extractedThms, true);
		System.out.println(thmList.size()); */
		
		//processes the theorems, select the words
		for(int i = 0; i < thmList.size(); i++){
			String thm = thmList.get(i);
			
			if(thm.matches("\\s*")) continue;
			
			String[] thmAr = thm.toLowerCase().split("\\s+|\'|\\(|\\)|\\{|\\}|\\[|\\]|\\.|\\;|\\,|:");
			
			Map<String, Integer> thmWordsMap = new HashMap<String, Integer>();
			
			//List<WordWrapper> wordWrapperList = SearchWordPreprocess.sortWordsType(thm);			
			
			for(String word : thmAr){
				
				//the two frequencies are now kept separate!
				
				//only keep words with lengths > 2
				//System.out.println(word);
				if(word.length() < 3 || freqWordsMap.containsKey(word)) continue;
				
				int wordFreq = thmWordsMap.containsKey(word) ? thmWordsMap.get(word) : 0;
				//int wordLongFreq = thmWordsMap.containsKey(wordLong) ? thmWordsMap.get(wordLong) : 0;
				thmWordsMap.put(word, wordFreq + 1);
				//thmWordsMap.put(wordLong, wordLongFreq + 1);
				
				int docWordFreq = docWordsFreqPreMap.containsKey(word) ? docWordsFreqPreMap.get(word) : 0;
				//int docWordLongFreq = docWordsFreqPreMap.containsKey(wordLong) ? docWordsFreqPreMap.get(wordLong) : 0;				
				//increase freq of word by 1
				docWordsFreqPreMap.put(word, docWordFreq + 1);
				//docWordsFreqPreMap.put(wordLong, docWordLongFreq + 1);
				
				//put both original and long form.
				wordThmsMMapBuilder.put(word, i);
				//wordThmsMMapBuilder.put(wordLong, i);
			}
			thmWordsListBuilder.add(ImmutableMap.copyOf(thmWordsMap));
			//System.out.println("++THM: " + thmWordsMap);
		}
	}
	
	public static ImmutableList<ImmutableMap<String, Integer>> get_thmWordsList(){
		return thmWordsList;
	}
	public static ImmutableList<ImmutableMap<String, Integer>> get_thmWordsListNoAnno(){
		return thmWordsListNoAnno;
	}
	/**
	 * Fills up wordsScoreMapBuilder
	 * @param wordsScoreMapBuilder
	 */
	private static void buildScoreMap(ImmutableMap.Builder<String, Integer> wordsScoreMapBuilder){
		
		for(Entry<String, Integer> entry : docWordsFreqMap.entrySet()){
			//wordsScoreMapBuilder.put(entry.getKey(), 1/(Math.log(entry.getValue() + 1)));
			//integer works better as keys to maps than doubles, so round/cast 2nd arg to int
			//+1 to avoid log(0)
			wordsScoreMapBuilder.put(entry.getKey(), (int)Math.round(1/Math.log(entry.getValue()+1)*10) );
		}
	}

	/**
	 * Fills up wordsScoreMapBuilder
	 * @param wordsScoreMapBuilder
	 */
	private static void buildScoreMapNoAnno(ImmutableMap.Builder<String, Integer> wordsScoreMapBuilderNoAnno){		
		for(Entry<String, Integer> entry : docWordsFreqMapNoAnno.entrySet()){
			//+1 so not to divide by 0.
			wordsScoreMapBuilderNoAnno.put(entry.getKey(), (int)Math.round(1/Math.log(entry.getValue()+1)*10) );
		}
	}
	
	/**
	 * Retrieves scores corresponding to words
	 * @return
	 */
	public static ImmutableMap<String, Integer> get_wordsScoreMap(){
		return wordsScoreMap;
	}
	public static ImmutableMap<String, Integer> get_wordsScoreMapNoAnno(){
		return wordsScoreMapNoAnno;
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
	
	public static ImmutableMultimap<String, Integer> get_wordThmsMMapNoAnno(){
		return wordThmsMMapNoAnno;
	}
	
	/**
	 * Retrieves map of words with their document-wide frequencies.
	 * @return
	 */
	public static ImmutableMap<String, Integer> get_docWordsFreqMap(){
		return docWordsFreqMap;
	}
	
	public static ImmutableMap<String, Integer> get_docWordsFreqMapNoAnno(){
		return docWordsFreqMapNoAnno;
	}
	
}
