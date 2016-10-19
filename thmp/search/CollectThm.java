package thmp.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;

import thmp.Maps;
import thmp.ProcessInput;
import thmp.ThmInput;
import thmp.search.SearchWordPreprocess.WordWrapper;
import thmp.utils.WordForms;

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
	
	//raw original file
	//private static final File rawFile = new File("src/thmp/data/commAlg5.txt");
	private static final String rawFileStr = "src/thmp/data/CommAlg5.txt";
	//read in from list of files streams instead of just one
	private static final List<String> rawFileStrList = Arrays.asList(new String[]{
			//"src/thmp/data/testContextVector.txt", 
			//"src/thmp/data/fieldsRawTex.txt",
			//"src/thmp/data/CommAlg5.txt", 
			//"src/thmp/data/multilinearAlgebra.txt",
			"src/thmp/data/functionalAnalysis.txt"
			});
	
	//latex macros source file name src/thmp/data/CommAlg5.txt
	private static final String MACROS_SRC = "src/thmp/data/texMacros.txt";
	//private static final List<String> rawFileStrList = Arrays.asList(new String[]{"src/thmp/data/functional_analysis_operator_algebras/distributions.txt"});

	//intentionally not final.
	private static volatile BufferedReader rawFileReader;
	//corresponding list of file readers
	private static volatile List<BufferedReader> rawFileReaderList;
	//macros file
	private static volatile BufferedReader macrosDefReader;
	
	//words that should be included as math words, but occur too frequently in math texts
	//to be detected as non-fluff words.
	private static final String[] SCORE1MATH_WORDS = new String[]{"ring", "field", "ideal", "finite", "series",
			"complex", "combination", "regular", "domain", "local", "smooth", "map", "definition", "standard", "prime"};
	//additional fluff words to add, that weren't listed in 
	private static final String[] ADDITIONAL_FLUFF_WORDS = new String[]{"tex", "is", "are", "an"};
	//file to be changed
	//private static File e = null;
	
	//private static final ImmutableMap<String, Integer> twoGramsMap;
	
	public static class FreqWordsSet{

		//Map of frequent words and their parts of speech (from words file data). Don't need the pos for now.
		private static final Set<String> freqWordsSet; 
		
		static{
			//only get the top N words
			//freqWordsSet = CollectFreqWords.GetFreqWords.get_nonMathFluffWordsSet2();
			freqWordsSet = WordFrequency.trueFluffWordsSet();
		}
		
		public static Set<String> freqWordsSet(){
			return freqWordsSet;
		}
	}
	
	/**
	 * Initialize class with reader, same as in static initializer. This needs
	 * to be called before initializing GetFreqWords subclass, if the maps are
	 * to be built from a particular bufferedReader.
	 * 
	 * @param wordsFileReader
	 */
	public static void setResources(BufferedReader srcFileReader) {
		rawFileReader = srcFileReader;
		System.out.print("first passed in: " +srcFileReader);
		
	}
	
	/**
	 * Set list of bufferedReaders, rawFileReaderList
	 * @param srcFileReader
	 */
	public static void setResources(List<BufferedReader> srcFileReaderList, BufferedReader macrosReader) {
		rawFileReaderList = srcFileReaderList;
		macrosDefReader = macrosReader;
		System.out.print("buffered readers first passed in: " +srcFileReaderList);
		
	}
	
	public static class ThmWordsMaps{
		//initialized in static block. List of theorems, each of which
		//contains map of keywords and their frequencies in this theorem. 
		//the more frequent words in a thm should be weighed up, but the
		//ones that are frequent in the whole doc weighed down.
		private static final ImmutableList<ImmutableMap<String, Integer>> thmWordsList;
		
		//document-wide word frequency. Keys are words, values are counts in whole doc.
		private static final ImmutableMap<String, Integer> docWordsFreqMap;
		
		//file to read from. Thms already extracted, ready to be processed.
		//private static final File thmFile = new File("src/thmp/data/thmFile5.txt");
		//list of theorems, in order their keywords are added to thmWordsList
		//private static final ImmutableList<String> thmList;
		//Multimap of keywords and the theorems they are in, in particular their indices in thmList
		private static final ImmutableMultimap<String, Integer> wordThmsMMap;
		/**Versions without annotations***/
		private static final ImmutableList<ImmutableMap<String, Integer>> thmWordsListNoAnno;
		private static final ImmutableMap<String, Integer> docWordsFreqMapNoAnno;
		private static final ImmutableMultimap<String, Integer> wordThmsMMapNoAnno;
		
		private static final Map<String, Integer> twoGramsMap = NGramsMap.get_twoGramsMap();
		private static final Map<String, Integer> threeGramsMap = NGramsMap.get_threeGramsMap();	
		//set that contains the first word of the two and three grams of twoGramsMap and threeGramsMap		
		//so the n-grams have a chance of being called.
		private static final Set<String> nGramFirstWordsSet = new HashSet<String>();
		
		/**
		 * Map of (annotated with "hyp" etc) keywords and their scores in document, the higher freq in doc, the lower 
		 * score, say 1/(log freq + 1) since log 1 = 0. 
		 */
		//wordsScoreMap should get deprecated! Should use scores of words without annotations.
		private static final ImmutableMap<String, Integer> wordsScoreMap;	
		private static final ImmutableMap<String, Integer> wordsScoreMapNoAnno;	
		//The number of frequent words to take
		private static final int NUM_FREQ_WORDS = 500;
		//multiplication factors to deflate the frequencies of 2-grams and 3-grams to weigh
		//them more
		private static final double THREE_GRAM_FREQ_REDUCTION_FACTOR = 4.0/5;
		private static final double TWO_GRAM_FREQ_REDUCTION_FACTOR = 5.0/6;
		
		static{
			
			//freqWordsMap = CollectFreqWords.getTopFreqWords(NUM_FREQ_WORDS);
			//pass builder into a reader function. For each thm, builds immutable list of keywords, 
			//put that list into the thm list.
			ImmutableList.Builder<ImmutableMap<String, Integer>> thmWordsListBuilder = ImmutableList.builder();
			Map<String, Integer> docWordsFreqPreMap = new HashMap<String, Integer>();
			ImmutableSetMultimap.Builder<String, Integer> wordThmsMMapBuilder = ImmutableSetMultimap.builder();
			
			/**Versions with no annotation, eg "hyp"/"stm" **/
			ImmutableList.Builder<ImmutableMap<String, Integer>> thmWordsListBuilderNoAnno = ImmutableList.builder();
			//ImmutableList.Builder<String> thmListBuilderNoAnno = ImmutableList.builder();
			Map<String, Integer> docWordsFreqPreMapNoAnno = new HashMap<String, Integer>();
			ImmutableSetMultimap.Builder<String, Integer> wordThmsMMapBuilderNoAnno = ImmutableSetMultimap.builder();
			
			// name of n gram data file containing n-grams that should be included. These don't have
			//frequencies associated with them.
			String NGRAM_DATA_FILESTR = "src/thmp/data/NGramData.txt";
			//read in n-grams from file named NGRAM_DATA_FILESTR and put in appropriate maps, 
			//either twogrammap or threegrammap
			readAdditionalNGrams(NGRAM_DATA_FILESTR);
			
			nGramFirstWordsSet.addAll(NGramSearch.get_2GramFirstWordsSet());
			nGramFirstWordsSet.addAll(ThreeGramSearch.get_3GramFirstWordsSet());
			
			//List<String> extractedThms = ThmList.get_thmList();
			//the third true means to extract words from latex symbols, eg oplus->direct sum.
			//last boolean is whether to replace macros, 
			//List<String> processedThmList = ProcessInput.processInput(extractedThms, true, true, true);
			List<String> processedThmList = ThmList.get_processedThmList();
			//System.out.println("After processing: "+thmList);
			try {
				readThm(thmWordsListBuilder, docWordsFreqPreMap, wordThmsMMapBuilder, processedThmList);
				//same as readThm, just buid maps without annocation
				buildMapsNoAnno(thmWordsListBuilderNoAnno, docWordsFreqPreMapNoAnno, wordThmsMMapBuilderNoAnno, processedThmList);				
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			thmWordsList = thmWordsListBuilder.build();	
			//thmList = thmListBuilder.build();
			
			docWordsFreqMap = ImmutableMap.copyOf(docWordsFreqPreMap); 		
			wordThmsMMap = wordThmsMMapBuilder.build();
			//non-annotated version
			thmWordsListNoAnno = thmWordsListBuilderNoAnno.build();	
			docWordsFreqMapNoAnno = ImmutableMap.copyOf(docWordsFreqPreMapNoAnno); 
			//System.out.println(docWordsFreqMapNoAnno);
			
			wordThmsMMapNoAnno = wordThmsMMapBuilderNoAnno.build();
						
			//builds scoresMap based on frequency map obtained from CollectThm.
			ImmutableMap.Builder<String, Integer> wordsScoreMapBuilder = ImmutableMap.builder();		
			buildScoreMap(wordsScoreMapBuilder);
			wordsScoreMap = wordsScoreMapBuilder.build();		
			
			//ImmutableMap.Builder<String, Integer> wordsScoreMapBuilderNoAnno = ImmutableMap.builder();
			Map<String, Integer> wordsScorePreMap = new HashMap<String, Integer>();
			buildScoreMapNoAnno(wordsScorePreMap);
			wordsScoreMapNoAnno = ImmutableMap.copyOf(wordsScorePreMap);
		}
		
		/**
		 * Adds additional words scraped from resources.
		 * @param NGRAM_DATA_FILESTR name of file containing additional n-grams
		 * and words scraped from the web.
		 */
		private static void readAdditionalNGrams(String NGRAM_DATA_FILESTR){
			BufferedReader fileBufferedReader;
			try{
				fileBufferedReader = new BufferedReader(new FileReader(NGRAM_DATA_FILESTR));
			}catch(FileNotFoundException e){
				e.printStackTrace();
				return;
			}
			String word;
			try{
				while((word = fileBufferedReader.readLine()) != null){
					word = word.toLowerCase();					
					//determine if 2 or 3 gram
					int wordLen = word.split("\\s+|-").length;
					if(wordLen == 2){
							//put in average two-gram frequency
						//average word frequency is low, should compute
						//but on the other hand, it probably did not show up often
						//in text either, so probably shouldn't count for much.
						if(!twoGramsMap.containsKey(word)){
							twoGramsMap.put(word, NGramSearch.averageTwoGramFreqCount());
						}
					}else if(wordLen == 3){
						if(!threeGramsMap.containsKey(word)){
							threeGramsMap.put(word, ThreeGramSearch.averageThreeGramFreqCount());
						}
					}//single word, need to be careful about initialization sequence with Maps!
						/*else if(wordLen == 1){
							Maps.putWordToPosMap(word, "ent");
						}*/
						
					
				}
			}catch(IOException e){
				e.printStackTrace();
			}
			
		}
		
		/**
		 * the frequency of bare words, without annocation such as H or C attached, is 
		 * equal to the sum of the frequencies of all the annotated forms, ie words with
		 * either H or C attached. eg freq(word) = freq(Cword)+freq(Hword). But when searching,
		 * annotated versions (with H/C) will get score bonus.
		 * Actually, should not put duplicates, all occurring words should have annotation, if one
		 * does not fit, try other annotations, just without the bonus points.
		 * ThmList should already have the singular forms of words.
		 * @param thmWordsListBuilder
		 * @param thmListBuilder
		 * @param docWordsFreqPreMap
		 * @param wordThmsMMapBuilder
		 * @throws IOException
		 * @throws FileNotFoundException
		 */
		private static void readThm(ImmutableList.Builder<ImmutableMap<String, Integer>> thmWordsListBuilder,
				Map<String, Integer> docWordsFreqPreMap,
				ImmutableSetMultimap.Builder<String, Integer> wordThmsMMapBuilder, List<String> thmList)
				throws IOException, FileNotFoundException{
			
			//Map<String, Integer> twoGramsMap = NGramsMap.get_twoGramsMap();
			//Map<String, Integer> threeGramsMap = NGramsMap.get_threeGramsMap();
			
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
				
				/*
				 * for(int j = 0; j < thmAr.length; j++){				
					String word = thmAr[j];	
					addWordToMaps(word, i, thmWordsMap, thmWordsListBuilder, docWordsFreqPreMap, wordThmsMMapBuilder);
					//check the following word
					if(j < thmAr.length-1){
						String nextWordCombined = word + " " + thmAr[j+1];
						if(twoGramsMap.containsKey(nextWordCombined)){
							addWordToMaps(nextWordCombined, i, thmWordsMap, thmWordsListBuilder, docWordsFreqPreMap,
									wordThmsMMapBuilder);
						}
					}
				}*/			
				for(int j = 0; j < wordWrapperList.size(); j++){
					WordWrapper curWrapper = wordWrapperList.get(j);
					String word = curWrapper.word();				
					//the two annotated frequencies are now kept separate!
					
					//get singular forms if plural, put singular form in map
					//Note, some words shouldn't need to be converted to singular form!
					//like "has", should use pos data to determine
					word = WordForms.getSingularForm(word);
					String wordLong = curWrapper.hashToString(word);
					
					//only keep words with lengths > 2
					//System.out.println(word);
					//words that should be de-fluffed as first word of n-grams need to be added to WordForms.FLUFF_WORDS_SMALL.
					if(word.length() < 3 || (FreqWordsSet.freqWordsSet.contains(word) && !nGramFirstWordsSet.contains(word))){ 						
						continue;
					}
					//if(word.matches("between")) System.out.println("********let Between go through!");
					addWordToMaps(wordLong, i, thmWordsMap, thmWordsListBuilder, docWordsFreqPreMap, wordThmsMMapBuilder);
					
					//check the following word for potential 2 grams. Only first word has hyp/stm annotation.
					if(j < wordWrapperList.size()-1){
						String nextWord = wordWrapperList.get(j+1).word();
						String nextWordCombined = word + " " + nextWord;
						String nextWordCombinedLong = wordLong + " " + nextWord;
						if(twoGramsMap.containsKey(nextWordCombined)){							
							addWordToMaps(nextWordCombinedLong, i, thmWordsMap, thmWordsListBuilder, docWordsFreqPreMap,
									wordThmsMMapBuilder);
						}
						
						if(j < wordWrapperList.size()-2){
							String thirdWord = wordWrapperList.get(j+2).word();
							String threeWordsCombined = nextWordCombined + " " + thirdWord;
							if(threeGramsMap.containsKey(threeWordsCombined)){
								String threeWordsCombinedLong = nextWordCombinedLong + " " + nextWord;
								addWordToMaps(threeWordsCombinedLong, i, thmWordsMap, thmWordsListBuilder, docWordsFreqPreMap,
										wordThmsMMapBuilder);
							}
						}
					}
					
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
				ImmutableSetMultimap.Builder<String, Integer> wordThmsMMapBuilder, List<String> thmList)
				throws IOException, FileNotFoundException{
			Map<String, Integer> twoGramsMap = NGramsMap.get_twoGramsMap();
			Map<String, Integer> threeGramsMap = NGramsMap.get_threeGramsMap();			
			
			//use method in ProcessInput to process in thms. Like turn $blah$ -> $tex$
			//adds original thms without latex replaced, should be in same order as above
			/*List<String> extractedThms = ThmInput.readThm(rawFile);
			thmListBuilder.addAll(extractedThms);
			List<String> thmList = ProcessInput.processInput(extractedThms, true);
			System.out.println(thmList.size()); */
			
			//System.out.println("nGramFirstWordsSet contains between? " + nGramFirstWordsSet.contains("between"));
			//System.out.println("FreqWordsSet contains between? " + FreqWordsSet.freqWordsSet.contains("between"));
			
			//processes the theorems, select the words
			for(int i = 0; i < thmList.size(); i++){
				String thm = thmList.get(i);
				
				//String[] thmAr = thm.toLowerCase().split("\\s+|\'|\\(|\\)|\\{|\\}|\\[|\\]|\\.|\\;|\\,|:");
				String[] thmAr = thm.toLowerCase().split(WordForms.splitDelim());
				
				Map<String, Integer> thmWordsMap = new HashMap<String, Integer>();				
				
				for(int j = 0; j < thmAr.length; j++){
					
					String word = thmAr[j];	
					//only keep words with lengths > 2
					//System.out.println(word);
					if(word.length() < 3) continue;
					
					//get singular forms if plural, put singular form in map
					//Note, some words shouldn't need to be converted to singular form!
					word = WordForms.getSingularForm(word);	
				
					if(FreqWordsSet.freqWordsSet.contains(word) && !nGramFirstWordsSet.contains(word)) continue;					
					
					addWordToMaps(word, i, thmWordsMap, thmWordsListBuilder, docWordsFreqPreMap, wordThmsMMapBuilder);
					//check the following word
					if(j < thmAr.length-1){
						String nextWordCombined = word + " " + thmAr[j+1];
						Integer twoGramFreq = twoGramsMap.get(nextWordCombined);
						if(twoGramFreq != null){
							int freq = (int)(twoGramFreq*TWO_GRAM_FREQ_REDUCTION_FACTOR);
							twoGramFreq = freq == 0 ? 1 : freq;
							addNGramToMaps(nextWordCombined, i, thmWordsMap, thmWordsListBuilder, docWordsFreqPreMap,
									wordThmsMMapBuilder, twoGramFreq);
						}
						//try to see if these three words form a valid 3-gram
						if(j < thmAr.length-2){
							String threeWordsCombined = nextWordCombined + " " + thmAr[j+2];
							Integer threeGramFreq = threeGramsMap.get(threeWordsCombined);
							if(threeGramFreq != null){
								//reduce frequency so 3-grams weigh more 
								int freq = (int)(threeGramFreq*THREE_GRAM_FREQ_REDUCTION_FACTOR);
								threeGramFreq = freq == 0 ? 1 : freq;
								addNGramToMaps(threeWordsCombined, i, thmWordsMap, thmWordsListBuilder, docWordsFreqPreMap,
										wordThmsMMapBuilder, threeGramFreq);
							}
						}
					}
				}
				thmWordsListBuilder.add(ImmutableMap.copyOf(thmWordsMap));
				//System.out.println("++THM: " + thmWordsMap);
			}
		}
		
		/**
		 * Auxiliary method for building word frequency maps. Just like addWordToMaps, 
		 * but add 2 and 3 Grams. 
		 * @param word Can be singleton or n-gram.
		 * @param curThmIndex
		 * @param thmWordsMap ThmWordsMap for current thm
		 * @param thmWordsListBuilder
		 * @param docWordsFreqPreMap
		 * @param wordThmsMMapBuilder
		 * @param wordTotalFreq is total frequency of word in corpus. This 
		 * was found when collecting 2 and 3 grams
		 */
		private static void addNGramToMaps(String word, int curThmIndex, Map<String, Integer> thmWordsMap,
				ImmutableList.Builder<ImmutableMap<String, Integer>> thmWordsListBuilder,
				Map<String, Integer> docWordsFreqPreMap,
				ImmutableSetMultimap.Builder<String, Integer> wordThmsMMapBuilder,
				int wordTotalFreq){			
			
			int wordFreq = thmWordsMap.containsKey(word) ? thmWordsMap.get(word) : 0;
			//int wordLongFreq = thmWordsMap.containsKey(wordLong) ? thmWordsMap.get(wordLong) : 0;
			thmWordsMap.put(word, wordFreq + 1);
			//thmWordsMap.put(wordLong, wordLongFreq + 1);
			
			//only add word freq to global doc word frequency if not already done so.
			if(!docWordsFreqPreMap.containsKey(word)){
				docWordsFreqPreMap.put(word, wordTotalFreq);
			}
			
			wordThmsMMapBuilder.put(word, curThmIndex);
			//wordThmsMMapBuilder.put(wordLong, i);
		}
		
		/**
		 * Auxiliary method for building word frequency maps. 
		 * @param word Can be singleton or n-gram.
		 * @param curThmIndex
		 * @param thmWordsMap
		 * @param thmWordsListBuilder
		 * @param docWordsFreqPreMap
		 * @param wordThmsMMapBuilder
		 */
		private static void addWordToMaps(String word, int curThmIndex, Map<String, Integer> thmWordsMap,
				ImmutableList.Builder<ImmutableMap<String, Integer>> thmWordsListBuilder,
				Map<String, Integer> docWordsFreqPreMap,
				ImmutableSetMultimap.Builder<String, Integer> wordThmsMMapBuilder){			
			
			//if(word.matches("(^.)linear")) System.out.println("************"  + "Got linear " + word );
			int wordFreq = thmWordsMap.containsKey(word) ? thmWordsMap.get(word) : 0;
			//int wordLongFreq = thmWordsMap.containsKey(wordLong) ? thmWordsMap.get(wordLong) : 0;
			thmWordsMap.put(word, wordFreq + 1);
			//thmWordsMap.put(wordLong, wordLongFreq + 1);
			
			int docWordFreq = docWordsFreqPreMap.containsKey(word) ? docWordsFreqPreMap.get(word) : 0;
			//int docWordLongFreq = docWordsFreqPreMap.containsKey(wordLong) ? docWordsFreqPreMap.get(wordLong) : 0;				
			//increase freq of word by 1
			docWordsFreqPreMap.put(word, docWordFreq + 1);
			//System.out.print(word + " " + docWordFreq+ " ");
			//docWordsFreqPreMap.put(wordLong, docWordLongFreq + 1);
			
			//put both original and long form.
			wordThmsMMapBuilder.put(word, curThmIndex);
			//wordThmsMMapBuilder.put(wordLong, i);
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
				//wordsScoreMapBuilder.put(entry.getKey(), (int)Math.round(1/Math.log(entry.getValue()+1)*5) );
				//wordsScoreMapBuilder.put(entry.getKey(), (int)Math.round(1/Math.pow(entry.getValue(), 1.25)*200) );
				String word = entry.getKey();
				//if(word.equals("tex")) continue;
				int wordFreq = entry.getValue();
				//300 (for search in this file)
				//int score = wordFreq < 110 ? (int)Math.round(10 - wordFreq/4) : wordFreq < 300 ? 1 : 0;
				int score = wordFreq < 40 ? (int)Math.round(10 - wordFreq/3) : (wordFreq < 180 ? (int)Math.round(15 - wordFreq/3) : (wordFreq < 450 ? 1 : 0));	
				
				//frequently occurring words, should not score too low since they are mostly math words.
				score = score <= 0 ? 3 : score;
				wordsScoreMapBuilder.put(word, score);
				//System.out.print(entry.getValue() + " ");
				//System.out.print("word: "+word +" score: "+score + " freq "+ wordFreq + "$   ");
			}
		}

		/**
		 * Fills up wordsScoreMapBuilder
		 * @param wordsScoreMapBuilder
		 */
		private static void buildScoreMapNoAnno(Map<String, Integer> wordsScorePreMap){		
			
			addWordScoresFromMap(wordsScorePreMap, docWordsFreqMapNoAnno);
			//System.out.println("docWordsFreqMapNoAnno "+docWordsFreqMapNoAnno);
			//put 2 grams in, freq map should already contain 2 grams
			//addWordScoresFromMap(wordsScorePreMap, twoGramsMap);
			
			//put 1 for math words that occur more frequently than the cutoff, but should still be counted, like "ring"	
			for(String word : SCORE1MATH_WORDS){
				wordsScorePreMap.put(word, 1);
			}
		}
		
		/**
		 * Auxiliary method for buildScoreMapNoAnno. Adds word scores from the desired map.
		 * @param wordsScorePreMap
		 */
		private static void addWordScoresFromMap(Map<String, Integer> wordsScorePreMap, Map<String, Integer> mapFrom) {
			for(Entry<String, Integer> entry : mapFrom.entrySet()){
				//+1 so not to divide by 0.
				//wordsScoreMapBuilderNoAnno.put(entry.getKey(), (int)Math.round(1/Math.log(entry.getValue()+1)*10) );
				//wordsScoreMapBuilderNoAnno.put(entry.getKey(), (int)Math.round(1/Math.pow(entry.getValue(), 1.25)*200) );
				String word = entry.getKey();
				//if(word.equals("tex")) continue;
				int wordFreq = entry.getValue();
				//*Keep* these comments. Experimenting with scoring parameters.
				//int score = wordFreq < 100 ? (int)Math.round(10 - wordFreq/8) : wordFreq < 300 ? 1 : 0;	
				//for 1200 thms, CommAlg5 + distributions:
				//int score = wordFreq < 110 ? (int)Math.round(10 - wordFreq/4) : wordFreq < 300 ? 1 : 0;	
				//int score = wordFreq < 180 ? (int)Math.round(15 - wordFreq/4) : wordFreq < 450 ? 1 : 0;
				int score = wordFreq < 40 ? (int)Math.round(10 - wordFreq/3) : (wordFreq < 180 ? (int)Math.round(15 - wordFreq/3) : (wordFreq < 450 ? 2 : 0));	
				//frequently occurring words, should not score too low since they are mostly math words.
				score = score <= 0 ? 3 : score;
				wordsScorePreMap.put(word, score);
				//System.out.print("word: "+word +" score: "+score + " freq "+ wordFreq + "$   ");
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
		 * Retrieves map of words with their document-wide frequencies.
		 * @return
		 */
		public static ImmutableMap<String, Integer> get_docWordsFreqMap(){
			return docWordsFreqMap;
		}
		
		public static ImmutableMap<String, Integer> get_docWordsFreqMapNoAnno(){
			return docWordsFreqMapNoAnno;
		}

		/**
		 * Retrieves ImmutableListMultimap of words and the theorems's indices in thmList
		 *  they appear in. Indices of thms are 0-based.
		 * @return
		 */
		public static ImmutableMultimap<String, Integer> get_wordThmsMMap(){
			return wordThmsMMap;
		}
		
		public static ImmutableMultimap<String, Integer> get_wordThmsMMapNoAnno(){
			return wordThmsMMapNoAnno;
		}
		
		
	}
	/////////////////////////End of prev class
	/**
	 * Static nested classes that accomodates lazy initialization (so to avoid circular 
	 * dependency), but also gives benefit of final (cause singleton), immutable (make it so).
	 */
	public static class NGramsMap{
		//private static final Map<String, Integer> twoGramsMap = ImmutableMap.copyOf(NGramSearch.get2GramsMap());
		private static final Map<String, Integer> twoGramsMap = NGramSearch.get2GramsMap();
		
		private static final Map<String, Integer> threeGramsMap = ThreeGramSearch.get3GramsMap();
		
		public static Map<String, Integer> get_twoGramsMap(){
			return twoGramsMap;
		}
		
		public static Map<String, Integer> get_threeGramsMap(){
			return threeGramsMap;
		}
	}
	
	/**
	 * Static nested classes that accomodates lazy initialization (so to avoid circular 
	 * dependency), but also gives benefit of final (cause singleton), immutable (make it so).
	 */
	public static class ThmList{
		private static final ImmutableList<String> thmList;
		//processed with options to replace tex with latex, and expand macros with their definitions
		private static final ImmutableList<String> processedThmList;
		//list of theorems for web display, without \label{} or \index{} etc
		private static final ImmutableList<String> webDisplayThmList;	
		//list of bare theorems, without label content 
		private static final ImmutableList<String> bareThmList;	
		//thm list with just macros replaced
		private static final ImmutableList<String> macroReplacedThmList;
		//whether to replace latex symbols with the word "tex"
		private static final boolean REPLACE_TEX = true;
		//whether to extract words from latex symbols, eg oplus->direct sum.
		private static final boolean TEX_TO_WORDS = true;
		//whether to expand macros to their definitions
		private static final boolean REPLACE_MACROS = true;
		
		static{
			ImmutableList.Builder<String> thmListBuilder = ImmutableList.builder();
			List<String> extractedThms = new ArrayList<String>();
			List<String> processedThms = new ArrayList<String>();
			List<String> macroReplacedThms = new ArrayList<String>();
			List<String> webDisplayThms = new ArrayList<String>();
			List<String> bareThms = new ArrayList<String>();
			//System.out.print("rawFileReader: " + rawFileReader);
			//extractedThms = ThmList.get_thmList();
			try {
				if(rawFileReaderList == null){
					for(String fileStr : rawFileStrList){
						//FileReader rawFileReader = new FileReader(rawFileStr);
						FileReader rawFileReader = new FileReader(fileStr);
						BufferedReader rawFileBReader = new BufferedReader(rawFileReader);
						//System.out.println("rawFileReader is null ");
						extractedThms.addAll(ThmInput.readThm(rawFileBReader, webDisplayThms, bareThms));							
						//System.out.print("Should be extracting theorems here: " + extractedThms);
					}
					//the third true means to extract words from latex symbols, eg oplus->direct sum.
					//last boolean is whether to replace macros, 
					FileReader macrosReader = new FileReader(MACROS_SRC);
					BufferedReader macrosBReader = new BufferedReader(macrosReader);
					bareThms = ProcessInput.processInput(bareThms, false, false, false);
					processedThms = ProcessInput.processInput(extractedThms, macrosBReader, REPLACE_TEX, TEX_TO_WORDS, REPLACE_MACROS);					
					macroReplacedThms = ProcessInput.get_macroReplacedThmList();
				}else{
					//System.out.println("read from rawFileReader");
					//System.out.print("ready for processing: " +rawFileReader);
					
					/*System.out.println(rawFileReader);
					String line;
					while((line=rawFileReader.readLine()) != null){
						System.out.println(line);
					}*/ 
					for(BufferedReader fileReader : rawFileReaderList){
						extractedThms.addAll(ThmInput.readThm(fileReader, webDisplayThms, bareThms));
					}
					//for parsing
					bareThms = ProcessInput.processInput(bareThms, true, false, false);
					processedThms = ProcessInput.processInput(extractedThms, macrosDefReader, REPLACE_TEX, TEX_TO_WORDS, REPLACE_MACROS);
					//the BufferedStream containing macros is set when rawFileReaderList is set.
					macroReplacedThms = ProcessInput.get_macroReplacedThmList();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			thmListBuilder.addAll(extractedThms);
			thmList = thmListBuilder.build();
			webDisplayThmList = ImmutableList.copyOf(webDisplayThms);
			bareThmList = ImmutableList.copyOf(bareThms);
			processedThmList = ImmutableList.copyOf(processedThms);
			macroReplacedThmList = ImmutableList.copyOf(macroReplacedThms);
		}
		
		/**
		 * Get thmList. Macros are expanded to their full forms by default.
		 * @return
		 */
		public static ImmutableList<String> get_thmList(){
			return thmList;
		}
		
		/**
		 * Get thmList. List of theorems for web display, without \label{} or \index{} etc.
		 * @return
		 */
		public static ImmutableList<String> get_webDisplayThmList(){
			return webDisplayThmList;
		}
		
		/**
		 * List of theorems for web parsing, without \label{} or \index{}, or label content etc.
		 * @return
		 */
		public static ImmutableList<String> get_bareThmList(){
			//System.out.println("bare thms " + bareThmList);
			return bareThmList;
		}
		
		public static ImmutableList<String> get_processedThmList(){
			return processedThmList;
		}
		
		/**
		 * Get original list expanding macros to their full forms.
		 * @return
		 */
		public static ImmutableList<String> get_macroReplacedThmList(){
			return macroReplacedThmList;
		}		
	}
	
	/**
	 * Retrieves list of thms.
	 * @return
	 */
	public static ImmutableList<String> get_thmList(){
		return ThmList.get_thmList();
	}
	
	/**
	 * Math words that should be included, but have been 
	 * marked as fluff due to their common occurance in English.
	 * Eg "ring".
	 * @return
	 */
	public static String[] score1MathWords(){
		return SCORE1MATH_WORDS;
	}
	
	/**
	 * Fluff words that are not included in the downloaded usual
	 * English fluff words list. Eg "tex"
	 * @return
	 */
	public static String[] additionalFluffWords(){
		return ADDITIONAL_FLUFF_WORDS;
	}
	
}
