package thmp.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import javax.servlet.ServletContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Set;
import java.util.TreeMap;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ListMultimap;

import thmp.DetectHypothesis.DefinitionListWithThm;
import thmp.Maps;
import thmp.ParseState.VariableDefinition;
import thmp.ParsedExpression;
import thmp.ProcessInput;
import thmp.ThmInput;
import thmp.search.SearchCombined.ThmHypPair;
import thmp.search.SearchWordPreprocess.WordWrapper;
import thmp.utils.WordForms.WordFreqComparator;
import thmp.utils.FileUtils;
import thmp.utils.GatherRelatedWords;
import thmp.utils.WordForms;
import thmp.utils.GatherRelatedWords.RelatedWords;
import thmp.utils.ResourceDeposit;

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
 */
public class CollectThm {
	
	private static final List<String> rawFileStrList = Arrays.asList(new String[]{
			//"src/thmp/data/testContextVector.txt", 
			//"src/thmp/data/collectThmTestSample.txt"
			"src/thmp/data/fieldsRawTex.txt",
			//"src/thmp/data/CommAlg5.txt", 
			//"src/thmp/data/multilinearAlgebra.txt",
			//"src/thmp/data/functionalAnalysis.txt",			
			//"src/thmp/data/topology.txt"
			});
	
	private static final Logger logger = LogManager.getLogger();
	//latex macros source file name src/thmp/data/CommAlg5.txt
	private static final String MACROS_SRC = "src/thmp/data/texMacros.txt";
	//private static final List<String> rawFileStrList = Arrays.asList(new String[]{"src/thmp/data/functional_analysis_operator_algebras/distributions.txt"});

	//There are intentionally *not* final.
	//private static volatile BufferedReader rawFileReader;
	//BufferedReader for context vectors.  <--should preferably not be global variables!
	private static volatile BufferedReader contextVecBR;
	//corresponding list of file readers
	//private static volatile List<BufferedReader> rawFileReaderList;
	//macros file
	private static volatile BufferedReader macrosDefReader;
	//InputStream for serialized parsed expressions list
	//private static volatile InputStream parsedExpressionListInputStream;
	//containing all serialized words from previous run.
	//private static volatile InputStream allThmWordsSerialInputStream;
	
	//wordFrequency.txt containing word frequencies and their part of speech (pos)
	//private static BufferedReader wordFrequencyBR;
	//servlet context if run from server
	private static ServletContext servletContext;
	
	/* Words that should be included as math words, but occur too frequently in math texts
	 * to be detected as non-fluff words. Put words here to be intentionally included in words map.
	 * Only singletons here. N-grams should be placed in N-gram files.
	 */
	private static final String[] SCORE1MATH_WORDS = new String[]{"ring", "field", "ideal", "finite", "series",
			"complex", "combination", "regular", "domain", "local", "smooth", "map", "definition", "standard", "prime", "every",
			"injective", "surjective"};
	//additional fluff words to add, that weren't listed previously
	private static final String[] ADDITIONAL_FLUFF_WORDS = new String[]{"tex", "is", "are", "an"};
	
	public static class FreqWordsSet{

		//Map of frequent words and their parts of speech (from words file data). Don't need the pos for now.
		private static final Set<String> freqWordsSet; 
		
		static{
			//only get the top N words
			freqWordsSet = WordFrequency.ComputeFrequencyData.trueFluffWordsSet();
		}
		
		public static Set<String> freqWordsSet(){
			return freqWordsSet;
		}
	}
	
	/**
	 * Set context vector BufferedReader.
	 * @param srcFileReader
	 */
	public static void setContextVecBF(BufferedReader contextVectorsBR) {
		contextVecBR = contextVectorsBR;		
	}
	
	public static BufferedReader contextVecBR(){
		return contextVecBR;
	}
	
	/**
	 * Set servlet context, if run from server.
	 * @param srcFileReader
	 */
	public static void setServletContext(ServletContext servletContext_) {
		servletContext = servletContext_;
	}
	
	public static ServletContext getServletContext() {
		return servletContext;
	}
	
	/**
	 * Set list of bufferedReaders, rawFileReaderList.
	 * Should just set servlet context instead of BufferedReaders!!
	 * @param srcFileReader
	 */
	public static void setResources(List<BufferedReader> srcFileReaderList, BufferedReader macrosReader,
			InputStream parsedExpressionListStream, InputStream allThmWordsSerialIStream) {
		//rawFileReaderList = srcFileReaderList;
		macrosDefReader = macrosReader;
		//parsedExpressionListInputStream = parsedExpressionListStream;
		//allThmWordsSerialInputStream = allThmWordsSerialIStream;
		//System.out.print("buffered readers first passed in: " + srcFileReaderList);		
	}
	
	/*public static void setWordFrequencyBR(BufferedReader freqWordsBR) {
		wordFrequencyBR = freqWordsBR;
	}*/
	
	/**
	 * The terms used by the SVD search, which are collected dynamically from the thms,
	 * are different than the ones used by context and relational vector search, whose
	 * terms also include the 2/3 grams and lexicon words (not just the ones that show up
	 * in the current set of thms), in addition to the terms used in the *previous* round
	 * of search (since these data were serialized).
	 */
	public static class ThmWordsMaps{
		//List of theorems, each of which
		//contains map of keywords and their frequencies in this theorem. 
		//the more frequent words in a thm should be weighed up, but the
		//ones that are frequent in the whole doc weighed down.
		//private static final ImmutableList<ImmutableMap<String, Integer>> thmWordsFreqList;
		
		//document-wide word frequency. Keys are words, values are counts in whole doc.
		//private static final ImmutableMap<String, Integer> docWordsFreqMap;
		
		private static final ImmutableMultimap<String, String> stemToWordsMMap;
		//file to read from. Thms already extracted, ready to be processed.
		//private static final File thmFile = new File("src/thmp/data/thmFile5.txt");
		//list of theorems, in order their keywords are added to thmWordsList
		//private static final ImmutableList<String> thmList;
		//Multimap of keywords and the theorems they are in, in particular their indices in thmList
		//private static final ImmutableMultimap<String, Integer> wordThmsIndexMMap;
		/**Versions without annotations***/
		//List of theorems, each of which
		//contains map of keywords and their frequencies in a particular theorem.
		private static final ImmutableList<ImmutableMap<String, Integer>> thmWordsFreqMapListNoAnno;
		/*words and their document-wide frequencies. These words are normalized, 
		e.g. "annihilator", "annihiate" all have the single entry "annihilat" */
		private static final ImmutableMap<String, Integer> docWordsFreqMapNoAnno;
		//entries are word and the indices of thms that contain that word.
		private static final ImmutableMultimap<String, Integer> wordThmsIndexMMapNoAnno;
		//map serialized for use during search, contains N-grams. Words and their frequencies.
		private static final ImmutableMap<String, Integer> contextVecWordsNextTimeMap;
		//to be used next time, words and their indices.
		private static final ImmutableMap<String, Integer> contextVecWordsIndexNextTimeMap;
		//this size depends on whether currently gathering data or performing search.
		private static final int CONTEXT_VEC_SIZE;
		
		private static final Map<String, Integer> twoGramsMap = NGramsMap.get_twoGramsMap();
		private static final Map<String, Integer> threeGramsMap = NGramsMap.get_threeGramsMap();	
		private static final List<String> skipGramWordsList;
		//set that contains the first word of the two and three grams of twoGramsMap and threeGramsMap		
		//so the n-grams have a chance of being called.
		private static final Set<String> nGramFirstWordsSet = new HashSet<String>();
		private static final int averageSingletonWordFrequency;
		
		/* Words and their indices.
		 * Deserialize the words used to form context and relation vectors. Note that this is a 
		 * separate* list from the words used in term document matrix.
		 * Absolute frequencies don't matter for forming context or relational vectors.
		 * List is ordered with respect to relative frequency, more frequent words come first,
		 * to optimize relation vector formation with BigIntegers.
		 * These words *alone* are used throughout all search algorithms by all maps, to guarantee consistency. 
		 */
		private static final Map<String, Integer> CONTEXT_VEC_WORDS_INDEX_MAP;
		private static final ImmutableMap<String, Integer> CONTEXT_VEC_WORDS_FREQ_MAP;
		
		/** Map of (annotated with "hyp" etc) keywords and their scores in document, the higher freq in doc, the lower 
		 * score, say 1/(log freq + 1) since log 1 = 0.  */
		//wordsScoreMap should get deprecated! Should use scores of words without annotations.
		//private static final ImmutableMap<String, Integer> wordsScoreMap;	
		private static final ImmutableMap<String, Integer> wordsScoreMapNoAnno;	
		//The number of frequent words to take
		private static final int NUM_FREQ_WORDS = 500;
		//multiplication factors to deflate the frequencies of 2-grams and 3-grams to weigh
		//them more
		private static final double THREE_GRAM_FREQ_REDUCTION_FACTOR = 3.0/5;
		private static final double TWO_GRAM_FREQ_REDUCTION_FACTOR = 2.0/3;
		
		private static final Pattern SPECIAL_CHARACTER_PATTERN = 
				Pattern.compile(".*[\\\\=$\\{\\}\\[\\]()^_+%&\\./,\"\\d\\/@><*|`�].*");
				
		private static final boolean GATHER_SKIP_GRAM_WORDS = ThmList.gather_skip_gram_words();
		//private static final boolean GATHER_SKIP_GRAM_WORDS = true;
		/* Related words scraped from wiktionary, etc. 
		 * Related words are *only* used
		 * to process queries, not the corpus; applied to all search algorithms. Therefore
		 * intentionally *not* final .
		 */
		private static Map<String, GatherRelatedWords.RelatedWords> relatedWordsMap;
		
		static{	
			/*map of words and their representatives, e.g. "annihilate", "annihilator", etc all map to "annihilat"
			i.e. word of maps to their stems. */
			//synonymRepMap = WordForms.getSynonymsMap();
			stemToWordsMMap = WordForms.stemToWordsMMap();
			//pass builder into a reader function. For each thm, builds immutable list of keywords, 
			//put that list into the thm list. The integer indicates the word frequencies.
			
			/**Versions with no annotation, eg "hyp"/"stm" **/
			ImmutableList.Builder<ImmutableMap<String, Integer>> thmWordsListBuilderNoAnno = ImmutableList.builder();
			/* *Only* used in data-gathering mode*/
			Map<String, Integer> docWordsFreqPreMapNoAnno = new HashMap<String, Integer>();
			ImmutableSetMultimap.Builder<String, Integer> wordThmsMMapBuilderNoAnno = ImmutableSetMultimap.builder();
			
			// name of n gram data file containing n-grams that should be included. These don't have
			//frequencies associated with them.
			//String NGRAM_DATA_FILESTR = "src/thmp/data/NGramData.txt";
			//read in n-grams from file named NGRAM_DATA_FILESTR and put in appropriate maps, 
			//either twogrammap or threegrammap
			//readAdditionalNGrams(NGRAM_DATA_FILESTR);
			
			nGramFirstWordsSet.addAll(NGramSearch.get_2GramFirstWordsSet());
			nGramFirstWordsSet.addAll(ThreeGramSearch.get_3GramFirstWordsSet());
			skipGramWordsList = new ArrayList<String>();
			//List<String> extractedThms = ThmList.get_thmList();
			//the third true means to extract words from latex symbols, eg oplus->direct sum.
			//last boolean is whether to replace macros, 
			//List<String> processedThmList = ThmList.get_processedThmList();		
			/* This list is smaller when in gathering data mode, and consists of a representative set 
			 * of theorems. Much larger in search mode.*/
			List<String> processedThmList = ThmList.allThmsWithHypList;
			
			//System.out.println("After processing: "+thmList);
			
				//this is commented out in Jan 2017, since the annotated version is no longer used.
				//readThm(thmWordsListBuilder, docWordsFreqPreMap, wordThmsMMapBuilder, processedThmList);
				//same as readThm, just buid maps without annotation
				//These all contain the *same* set of words.
			buildMapsNoAnno(thmWordsListBuilderNoAnno, docWordsFreqPreMapNoAnno, wordThmsMMapBuilderNoAnno, 
						processedThmList, skipGramWordsList);
			
			/*use stemToWordsMMap to re-adjust frequency of word stems that came from multiple forms, 
			 as these are much more likely to be math words, so don't want to scale down too much */
			adjustWordFreqMapWithStemMultiplicity(docWordsFreqPreMapNoAnno, stemToWordsMMap);
			
			//thmWordsFreqList = thmWordsListBuilder.build();	
			
			//docWordsFreqMap = ImmutableMap.copyOf(docWordsFreqPreMap); 		
			//wordThmsIndexMMap = wordThmsMMapBuilder.build();
			//non-annotated version
			thmWordsFreqMapListNoAnno = thmWordsListBuilderNoAnno.build();	
			
			//builds scoresMap based on frequency map obtained from CollectThm.
			//ImmutableMap.Builder<String, Integer> wordsScoreMapBuilder = ImmutableMap.builder();		
			//buildScoreMap(wordsScoreMapBuilder);
			//wordsScoreMap = wordsScoreMapBuilder.build();		
			
			//first compute the average word frequencies for singleton words
			averageSingletonWordFrequency = computeSingletonWordsFrequency(docWordsFreqPreMapNoAnno);			
			//add lexicon words to docWordsFreqMapNoAnno, which only contains collected words from thm corpus,
			//collected based on frequnency, right now. These words do not have corresponding thm indices.
			addLexiconWordsToContextKeywordDict(docWordsFreqPreMapNoAnno, averageSingletonWordFrequency);
			
			Map<String, Integer> wordsScorePreMap = new HashMap<String, Integer>();
			
			/*deserialize the word frequency map from file, as gathered from last time the data were generated.*/
			//Map<String, Integer> wordFreqMapFromFile = extractWordFreqMap();
			CONTEXT_VEC_WORDS_FREQ_MAP = extractWordFreqMap();
			
			//the values are just the words' indices in wordsList.
			//this orders the list as well. INDEX map. Can rely on order as map is immutable.
			
			//System.out.println("------++++++++-------CONTEXT_VEC_WORDS_MAP.size " + CONTEXT_VEC_WORDS_MAP.size());
			
			if(Searcher.SearchMetaData.gatheringDataBool()){				
				buildScoreMapNoAnno(wordsScorePreMap, docWordsFreqPreMapNoAnno);				
				Map<String, Integer> keyWordFreqTreeMap = reorderDocWordsFreqMap(docWordsFreqPreMapNoAnno);					
				docWordsFreqMapNoAnno = ImmutableMap.copyOf(keyWordFreqTreeMap);
				CONTEXT_VEC_WORDS_INDEX_MAP = null;
			}else{				
				buildScoreMapNoAnno(wordsScorePreMap, CONTEXT_VEC_WORDS_FREQ_MAP);
				/*Do *not* re-order map based on frequency, since need to be consistent with word row
				 * indices in term document matrix. Also should already be ordered. */
				docWordsFreqMapNoAnno = CONTEXT_VEC_WORDS_FREQ_MAP;	
				relatedWordsMap = deserializeAndProcessRelatedWordsMapFromFile(docWordsFreqMapNoAnno);
				CONTEXT_VEC_WORDS_INDEX_MAP = createContextKeywordIndexDict(CONTEXT_VEC_WORDS_FREQ_MAP);
			}
			//this is ok, since from previous set of serialized data.
			wordsScoreMapNoAnno = ImmutableMap.copyOf(wordsScorePreMap);
			System.out.println("*********wordsScoreMapNoAnno.size(): " + wordsScoreMapNoAnno.size());
			
			wordThmsIndexMMapNoAnno = wordThmsMMapBuilderNoAnno.build();
			CONTEXT_VEC_SIZE = docWordsFreqMapNoAnno.size();
			/***This is where the set of words used for SVD search and search based on context and relational vectors
			 * diverge. The latter contains additional words (N-grams) added below. Note these words
			 * are used for NGram formation NEXT run (generating ParsedExpressionList)***/ //<--actually now they are the same
			
			//Must add the 2 and 3 grams to docWordsFreqPreMapNoAnno. The N-grams that actually occur in 
			//this corpus of theorems have already been added to docWordsFreqPreMapNoAnno during buildMaps.
			//docWordsFreqPreMapNoAnno.putAll(twoGramsMap);
			//docWordsFreqPreMapNoAnno.putAll(threeGramsMap);
			//map to be serialized, and used for forming context vectors in next run.
			contextVecWordsNextTimeMap = docWordsFreqMapNoAnno;
			//shouldn't need this, since can reconstruct index from immutableMap 
			contextVecWordsIndexNextTimeMap = ImmutableMap.copyOf(createContextKeywordIndexDict(contextVecWordsNextTimeMap));
			//deserialize words from allThmWordsList.dat, which were serialized from previous run.
			//List<String> wordsList = extractWordsList();	 <--superceded by wordsMap
			//write skipGramWordsList to file
			if(GATHER_SKIP_GRAM_WORDS){
				String skipGramWordsListFileStr = "src/thmp/data/skipGramWordsList.txt";
				FileUtils.writeToFile(skipGramWordsList, skipGramWordsListFileStr);
			}
		}

		/**
		 * Deserializes and processes, normalize the words. Related words are only used
		 * to process queries, not the corpus; applied to all search algorithms.
		 * Process here rather than at map formation, since synonymsMap need to pertain 
		 * to current corpus.
		 * 		
		/* Word and its synonymous representative in the term document matrix, if such 
		 * a synonym has been added to the map already. If not, add the rep. This is for
		 * words that are interchangeable, not similar not non-interchangeable words.
		 * Create only one entry in term-document matrix for each synonym group. 
		 * @param docWordsFreqMapNoAnno 
		 * @return
		 */
		@SuppressWarnings("unchecked")
		private static Map<String, RelatedWords> deserializeAndProcessRelatedWordsMapFromFile(Map<String, Integer> docWordsFreqMapNoAnno) {
			String relatedWordsMapFileStr = FileUtils.getRELATED_WORDS_MAP_SERIAL_FILE_STR();
			Map<String, RelatedWords> relatedWordsMap;
			if(null != servletContext){
				InputStream relatedWordsMapInputStream = servletContext.getResourceAsStream(relatedWordsMapFileStr);				
				List<Map<String, RelatedWords>> list 
					= (List<Map<String, RelatedWords>>)FileUtils.deserializeListFromInputStream(relatedWordsMapInputStream);
				FileUtils.silentClose(relatedWordsMapInputStream);
				relatedWordsMap = list.get(0);
			}else{		
				List<Map<String, RelatedWords>> list 
					= (List<Map<String, RelatedWords>>)FileUtils.deserializeListFromFile(relatedWordsMapFileStr);
				relatedWordsMap = list.get(0);
			}
			
			int relatedWordsUsedCounter = 0;
			Set<Entry<String, RelatedWords>> relatedWordsEntrySet = relatedWordsMap.entrySet();
			Iterator<Entry<String, RelatedWords>> relatedWordsEntrySetIter = relatedWordsEntrySet.iterator();
			
			Map<String, RelatedWords> relatedWordsTempMap = new HashMap<String, RelatedWords>();
			while(relatedWordsEntrySetIter.hasNext()){
				Entry<String, RelatedWords> relatedWordsEntry = relatedWordsEntrySetIter.next();
				String word = relatedWordsEntry.getKey();
				if(!docWordsFreqMapNoAnno.containsKey(word)){
					word = WordForms.normalizeWordForm(word);
				}
				if(!docWordsFreqMapNoAnno.containsKey(word)){
					continue;
				}				
				relatedWordsEntrySetIter.remove();
				RelatedWords normalizedRelatedWords 
					= relatedWordsEntry.getValue().normalizeFromValidWordSet(docWordsFreqMapNoAnno.keySet());
				
				relatedWordsTempMap.put(word, normalizedRelatedWords);
				relatedWordsUsedCounter++;
			}
			relatedWordsMap.putAll(relatedWordsTempMap);
			
			System.out.println("CollectThm.ThmWordsMap - Total number of related words entries adapted: " + relatedWordsUsedCounter);
			return relatedWordsMap;
		}
		
		/**
		 * Use stemToWordsMMap to re-adjust frequency of word stems that came from multiple forms, 
			as these are much more likely to be math words, so don't want to scale down too much.
		 * @param docWordsFreqPreMapNoAnno
		 * @param stemtowordsmmap2
		 */
		private static void adjustWordFreqMapWithStemMultiplicity(Map<String, Integer> docWordsFreqPreMapNoAnno,
				ImmutableMultimap<String, String> stemToWordsMMap_) {
			double freqAdjustmentFactor = 3.0/4;
			Map<String, Integer> modifiedWordFreqMap = new HashMap<String, Integer>();			
			for(Map.Entry<String, Integer> entry : docWordsFreqPreMapNoAnno.entrySet()){
				String wordStem = entry.getKey();
				if(stemToWordsMMap_.containsKey(wordStem)){
					int formsCount = (int)(stemToWordsMMap_.get(wordStem).size()*freqAdjustmentFactor);
					//pre-processing should have eliminated stems with freq 1
					if(formsCount < 2) continue;
					int adjustedFreq = entry.getValue()/formsCount;
					adjustedFreq = adjustedFreq > 0 ? adjustedFreq : 1;
					modifiedWordFreqMap.put(wordStem, adjustedFreq);
				}
			}
			docWordsFreqPreMapNoAnno.putAll(modifiedWordFreqMap);
		}
		
		/**
		 * deserialize words list used to form context and relation vectors, which were
		 * formed while parsing through the papers in e.g. DetectHypothesis.java. This is
		 * so we don't parse everything again at every server initialization.
		 * Map of words and their frequencies.
		 * @return
		 */
		@SuppressWarnings("unchecked")		
		private static ImmutableMap<String, Integer> extractWordFreqMap() {	
			//It is "src/thmp/data/allThmWordsMap.dat";
			String allThmWordsSerialFileStr = thmp.DetectHypothesis.allThmWordsMapSerialFileStr;
			if(null != servletContext){
				InputStream allThmWordsSerialInputStream = servletContext.getResourceAsStream(allThmWordsSerialFileStr);
				Map<String, Integer> map 
					= ((List<Map<String, Integer>>)FileUtils.deserializeListFromInputStream(allThmWordsSerialInputStream)).get(0);
				FileUtils.silentClose(allThmWordsSerialInputStream);
				return ImmutableMap.copyOf(map);
			}else{				
				Map<String, Integer> map 
					= ((List<Map<String, Integer>>)FileUtils.deserializeListFromFile(allThmWordsSerialFileStr)).get(0);
				return ImmutableMap.copyOf(map);
			}
		}
		
		/**
		 * Creates a map, ordered by frequency, with keys words and words their indices in map.
		 * @param wordsList
		 * @return Map of words and their indices in wordsList.
		 */
		public static Map<String, Integer> createContextKeywordIndexDict(Map<String, Integer> docWordsFreqPreMapNoAnno){
			Map<String, Integer> contextKeywordIndexDict = new HashMap<String, Integer>();
			//these are ordered based on frequency, more frequent words occur earlier.
			//List<String> wordsList = CollectThm.ThmWordsMaps.getCONTEXT_VEC_WORDS_LIST();	
			//Should already been ordered from previous run! 
			//Map<String, Integer> keyWordFreqTreeMap = reorderDocWordsFreqMap(docWordsFreqPreMapNoAnno);			
			int counter = 0;
			for(Map.Entry<String, Integer> entry : docWordsFreqPreMapNoAnno.entrySet()){				
				contextKeywordIndexDict.put(entry.getKey(), counter++);
			}
			//System.out.println("********CollectThm - contextKeywordIndexDict : " + contextKeywordIndexDict);
			return contextKeywordIndexDict;
		}

		/**
		 * @param docWordsFreqPreMapNoAnno
		 * @return
		 */
		public static Map<String, Integer> reorderDocWordsFreqMap(Map<String, Integer> docWordsFreqPreMapNoAnno) {
			//re-order the list so the most frequent words appear first, as optimization
			//so that search words can match the most frequently-occurring words.
			WordFreqComparator comp = new WordFreqComparator(docWordsFreqPreMapNoAnno);
			//words and their frequencies in wordDoc matrix.
			Map<String, Integer> keyWordFreqTreeMap = new TreeMap<String, Integer>(comp);
			keyWordFreqTreeMap.putAll(docWordsFreqPreMapNoAnno);
			return keyWordFreqTreeMap;
		}
		
		/**
		 * Map of words in  and their indices.
		 * Words used to form context and relation vectors. Note that this is a 
		 * separate* list from the words used in term document matrix.
		 * Absolute frequencies don't matter for forming context or relational vectors.
		 * List is ordered with respect to relative frequency, more frequent words come first,
		 * to optimize relation vector formation with BigIntegers.
		 */
		public static Map<String, Integer> get_CONTEXT_VEC_WORDS_INDEX_MAP(){
			return CONTEXT_VEC_WORDS_INDEX_MAP;
		}
		
		public static int get_CONTEXT_VEC_SIZE(){
			return CONTEXT_VEC_SIZE;
		}
		
		public static ImmutableMap<String, Integer> get_CONTEXT_VEC_WORDS_FREQ_MAP_fromData(){
			return CONTEXT_VEC_WORDS_FREQ_MAP;
		}
		
		public static Map<String, RelatedWords> getRelatedWordsMap(){
			return relatedWordsMap;
		}
		
		/**
		 * Add lexicon words to docWordsFreqMapNoAnno, which only contains collected words from thm corpus,
		 * collected based on frequnency, right now. This is curated list of words, so don't need much normalization.
		 * e..g. already singular.
		 */
		private static void addLexiconWordsToContextKeywordDict(Map<String, Integer> docWordsFreqMapNoAnno,
				int averageSingletonWordFrequency){
			
			ListMultimap<String, String> posMMap = Maps.essentialPosMMap();
			int avgWordFreq = averageSingletonWordFrequency;
			//add avg frequency based on int
			for(Map.Entry<String, String> entry : posMMap.entries()){
				String pos = entry.getValue();
				if(pos.equals("ent") || pos.equals("adj")){
					String word = entry.getKey();
					word = WordForms.normalizeWordForm(word);
					if(!docWordsFreqMapNoAnno.containsKey(word)){
						docWordsFreqMapNoAnno.put(word, avgWordFreq);
					}
				}
			}			
		}
		
		/**
		 * Computes the averageSingletonWordFrequency.
		 * @param docWordsFreqPreMapNoAnno
		 * @return
		 */
		private static int computeSingletonWordsFrequency(Map<String, Integer> docWordsFreqPreMapNoAnno) {
			int freqSum = 0;
			int count = 0;
			for(int freq : docWordsFreqPreMapNoAnno.values()){
				freqSum += freq;
				count++;
			}
			if (0 == count) return 1;
			return freqSum/count;
		}

		/**
		 * Returns average word frequency for singleton words.
		 * @return
		 */
		public static int singletonWordsFrequency(){
			return averageSingletonWordFrequency;
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
			
			//processes the theorems, select the words
			for(int i = 0; i < thmList.size(); i++){
				String thm = thmList.get(i);
				
				if(thm.matches("\\s*")) continue;
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
					addWordToMaps(wordLong, i, thmWordsMap, //thmWordsListBuilder, 
							docWordsFreqPreMap, wordThmsMMapBuilder);
					
					//check the following word for potential 2 grams. Only first word has hyp/stm annotation.
					if(j < wordWrapperList.size()-1){
						String nextWord = wordWrapperList.get(j+1).word();
						String nextWordCombined = word + " " + nextWord;
						String nextWordCombinedLong = wordLong + " " + nextWord;
						if(twoGramsMap.containsKey(nextWordCombined)){							
							addWordToMaps(nextWordCombinedLong, i, thmWordsMap, //thmWordsListBuilder, 
									docWordsFreqPreMap, wordThmsMMapBuilder);
						}
						
						if(j < wordWrapperList.size()-2){
							String thirdWord = wordWrapperList.get(j+2).word();
							String threeWordsCombined = nextWordCombined + " " + thirdWord;
							if(threeGramsMap.containsKey(threeWordsCombined)){
								String threeWordsCombinedLong = nextWordCombinedLong + " " + nextWord;
								addWordToMaps(threeWordsCombinedLong, i, thmWordsMap, //thmWordsListBuilder, 
										docWordsFreqPreMap,
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
		 * Used for building skip gram word list.
		 * @param thmList
		 * @param skipGramWordList_
		 * @throws IOException
		 * @throws FileNotFoundException
		 */
		public static void createSkipGramWordList(List<String> thmList,
				List<String> skipGramWordList_){
			System.out.print("Inside createSkipGramWordList!");
			ImmutableList.Builder<ImmutableMap<String, Integer>> thmWordsFreqListBuilder 
				= new ImmutableList.Builder<ImmutableMap<String, Integer>>();
			Map<String, Integer> docWordsFreqPreMap = new HashMap<String, Integer>();
			ImmutableSetMultimap.Builder<String, Integer> wordThmsMMapBuilder 
				= new ImmutableSetMultimap.Builder<String, Integer>();			
			buildMapsNoAnno(thmWordsFreqListBuilder, docWordsFreqPreMap, wordThmsMMapBuilder, thmList, skipGramWordList_);						
		}
		
		/**
		 * Same as readThm, except without hyp/concl wrappers.
		 * Maps contain the same set of words.
		 * @param thmWordsFreqListBuilder 
		 * 		List of maps, each of which is a map of word Strings and their frequencies. Used for SVD search.
		 * @param thmListBuilder
		 * @param docWordsFreqPreMap
		 * @param wordThmsMMapBuilder
		 * @throws IOException
		 * @throws FileNotFoundException
		 */
		private static void buildMapsNoAnno(ImmutableList.Builder<ImmutableMap<String, Integer>> thmWordsFreqListBuilder,
				Map<String, Integer> docWordsFreqPreMap,
				ImmutableSetMultimap.Builder<String, Integer> wordThmsMMapBuilder, List<String> thmList,
				List<String> skipGramWordList_){
			
			Map<String, Integer> twoGramsMap = NGramsMap.get_twoGramsMap();
			Map<String, Integer> threeGramsMap = NGramsMap.get_threeGramsMap();			
			ListMultimap<String, String> posMMap = Maps.posMMap();
			
			//use method in ProcessInput to process in thms. Like turn $blah$ -> $tex$
			//adds original thms without latex replaced, should be in same order as above
			/*List<String> extractedThms = ThmInput.readThm(rawFile);
			thmListBuilder.addAll(extractedThms);
			List<String> thmList = ProcessInput.processInput(extractedThms, true);
			System.out.println(thmList.size()); */
			
			//System.out.println("nGramFirstWordsSet contains between? " + nGramFirstWordsSet.contains("between"));
			//System.out.println("FreqWordsSet contains between? " + FreqWordsSet.freqWordsSet.contains("between"));
			//int counter=0;
			//System.out.println("collectThm - thmList.size " + thmList.size());
			//processes the theorems, select the words
			for(int i = 0; i < thmList.size(); i++){
				//System.out.println(counter++);
				String thm = thmList.get(i);
				//number of words to skip if an n gram has been added.
				int numFutureWordsToSkip = 0;
				//split along e.g. "\\s+|\'|\\(|\\)|\\{|\\}|\\[|\\]|\\.|\\;|\\,|:"
				String[] thmAr = WordForms.splitThmIntoSearchWords(thm.toLowerCase());
				
				//words and their frequencies.
				Map<String, Integer> thmWordsFreqMap = new HashMap<String, Integer>();				
				
				for(int j = 0; j < thmAr.length; j++){
					
					String singletonWordAdded = null;
					String twoGramAdded = null;
					String threeGramAdded = null;					
					String word = thmAr[j];	
					//only keep words with lengths > 2
					//System.out.println(word);
					int lengthCap = GATHER_SKIP_GRAM_WORDS ? 3 : 3;
					//word length could change, so no assignment to variable.
					if(word.length() < lengthCap){
						continue;
					}
					
					//get singular forms if plural, put singular form in map
					//Should be more careful on some words that shouldn't be singular-ized!					
					word = WordForms.getSingularForm(word);	
					
					//also don't skip if word is contained in lexicon										
					boolean skipWordBasedOnPos = true;
					if(GATHER_SKIP_GRAM_WORDS){
						skipWordBasedOnPos = false;
					}else{ 
						if(WordForms.getFluffSet().contains(word)) continue;
						List<String> wordPosList = posMMap.get(word);
						if(!wordPosList.isEmpty()){
							String wordPos = wordPosList.get(0);
							skipWordBasedOnPos = !wordPos.equals("ent") && !wordPos.equals("adj"); 
							
							//wordPos.equals("verb") <--should have custom verb list
							//so don't keep irrelevant verbs such as "are", "take"
						}
					}
					if(FreqWordsSet.freqWordsSet.contains(word) && !nGramFirstWordsSet.contains(word)
							&& skipWordBasedOnPos) continue;					
					
					//check the following word
					if(j < thmAr.length-1){
						String nextWordCombined = word + " " + thmAr[j+1];
						Integer twoGramFreq = twoGramsMap.get(nextWordCombined);
						if(twoGramFreq != null){
							int freq = (int)(twoGramFreq*TWO_GRAM_FREQ_REDUCTION_FACTOR);
							twoGramFreq = freq == 0 ? 1 : freq;
							twoGramAdded = addNGramToMaps(nextWordCombined, i, thmWordsFreqMap, //thmWordsFreqListBuilder, 
									docWordsFreqPreMap, wordThmsMMapBuilder, twoGramFreq);
						}
						//try to see if these three words form a valid 3-gram
						if(j < thmAr.length-2){
							String threeWordsCombined = nextWordCombined + " " + thmAr[j+2];
							Integer threeGramFreq = threeGramsMap.get(threeWordsCombined);
							if(threeGramFreq != null){
								//reduce frequency so 3-grams weigh more 
								int freq = (int)(threeGramFreq*THREE_GRAM_FREQ_REDUCTION_FACTOR);
								threeGramFreq = freq == 0 ? 1 : freq;
								threeGramAdded = addNGramToMaps(threeWordsCombined, i, thmWordsFreqMap, //thmWordsFreqListBuilder, 
										docWordsFreqPreMap,	wordThmsMMapBuilder, threeGramFreq);
							}
						}
					}					
					if(!GATHER_SKIP_GRAM_WORDS){
						//removes endings such as -ing, and uses synonym rep.
						//e.g. "annihilate", "annihilator", etc all map to "annihilat"
						word = WordForms.normalizeWordForm(word);
					}
					singletonWordAdded = addWordToMaps(word, i, thmWordsFreqMap, //thmWordsFreqListBuilder, 
							docWordsFreqPreMap, wordThmsMMapBuilder);
					if(GATHER_SKIP_GRAM_WORDS){
						//gather list of relevant words used in this thm
						if(numFutureWordsToSkip > 0){
							numFutureWordsToSkip--;
						}else if(null != threeGramAdded){
							skipGramWordList_.add(threeGramAdded);
							numFutureWordsToSkip = 2;
						}else if(null != twoGramAdded){
							skipGramWordList_.add(twoGramAdded);
							numFutureWordsToSkip = 1;
						}else if(null != singletonWordAdded){
							skipGramWordList_.add(singletonWordAdded);
						}
					}
				}//done iterating through this thm				 
				thmWordsFreqListBuilder.add(ImmutableMap.copyOf(thmWordsFreqMap));
				//System.out.println("++THM: " + thmWordsMap);
			}
		}
		
		/**
		 * Auxiliary method for building word frequency maps. Analogous to addWordToMaps(), 
		 * but add 2 and 3 Grams. 
		 * @param word Can be singleton or n-gram.
		 * @param curThmIndex
		 * @param thmWordsMap ThmWordsMap for current thm
		 * @param thmWordsListBuilder
		 * @param docWordsFreqPreMap global document word frequency
		 * @param wordThmsMMapBuilder
		 * @param wordTotalFreq is total frequency of word in corpus. This 
		 * was found when collecting 2 and 3 grams
		 * @return whether the n gram was added.
		 */
		private static String addNGramToMaps(String word, int curThmIndex, Map<String, Integer> thmWordsMap,
				//ImmutableList.Builder<ImmutableMap<String, Integer>> thmWordsListBuilder,
				Map<String, Integer> docWordsFreqPreMap,
				ImmutableSetMultimap.Builder<String, Integer> wordThmsMMapBuilder,
				int wordTotalFreq){			
			
			if(SPECIAL_CHARACTER_PATTERN.matcher(word).find()){
				return null;
			}
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
			return word;
		}
		
		/**
		 * Auxiliary method for building word frequency maps. 
		 * @param word Can be singleton or n-gram.
		 * @param curThmIndex
		 * @param thmWordsFreqMap 
		 * @param thmWordsListBuilder
		 * @param docWordsFreqPreMap
		 * @param wordThmsMMapBuilder  Multimap of words and the indices of theorems they occur in.
		 * @return whether word was added
		 */
		private static String addWordToMaps(String word, int curThmIndex, Map<String, Integer> thmWordsFreqMap,
				Map<String, Integer> docWordsFreqPreMap,
				ImmutableSetMultimap.Builder<String, Integer> wordThmsMMapBuilder){			
			
			//screen the word for special characters, e.g. "/", don't put these words into map.
			if(SPECIAL_CHARACTER_PATTERN.matcher(word).find()){
				return null;
			}
			
			int wordFreq = thmWordsFreqMap.containsKey(word) ? thmWordsFreqMap.get(word) : 0;
			thmWordsFreqMap.put(word, wordFreq + 1);
			
			int docWordFreq = docWordsFreqPreMap.containsKey(word) ? docWordsFreqPreMap.get(word) : 0;
							
			//increase freq of word by 1
			docWordsFreqPreMap.put(word, docWordFreq + 1);
			
			//put both original and long form.
			wordThmsMMapBuilder.put(word, curThmIndex);
			return word;
		}
		
		/*public static ImmutableList<ImmutableMap<String, Integer>> get_thmWordsFreqList(){
			return thmWordsFreqList;
		}*/
		
		/**
		 * Contains map of keywords and their frequencies in a particular theorem.
		 * @return
		 */
		public static ImmutableList<ImmutableMap<String, Integer>> get_thmWordsFreqListNoAnno(){
			return thmWordsFreqMapListNoAnno;
		}

		/**
		 * Fills up wordsScorePreMap
		 * @param wordsScorePreMap empty map to be filled.
		 * @param docWordsFreqPreMapNoAnno Map of words and their document-wide frequencies.
		 */
		public static void buildScoreMapNoAnno(Map<String, Integer> wordsScorePreMap,
				Map<String, Integer> docWordsFreqPreMapNoAnno){		
			
			addWordScoresFromMap(wordsScorePreMap, docWordsFreqPreMapNoAnno);
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
		 * Retrieves map of scores corresponding to words
		 * @return
		 */
		/*public static ImmutableMap<String, Integer> get_wordsScoreMap(){
			return wordsScoreMap;
		} HERE*/
		
		/**
		 * Retrieves map of scores corresponding to words
		 * @return
		 */
		public static ImmutableMap<String, Integer> get_wordsScoreMapNoAnno(){
			return wordsScoreMapNoAnno;
		}
		
		/**
		 * Retrieves map of words with their document-wide frequencies.
		 * @return
		 */
		/*public static ImmutableMap<String, Integer> get_docWordsFreqMap(){
			return docWordsFreqMap;
		}*/
		
		/**
		 * Retrieves map of words with their document-wide frequencies.
		 * @return
		 */
		public static ImmutableMap<String, Integer> get_docWordsFreqMapNoAnno(){
			return docWordsFreqMapNoAnno; 
		}

		/**
		 * Retrieves map of words with their document-wide frequencies.
		 * @return
		 */
		public static ImmutableMap<String, Integer> get_contextVecWordsNextTimeMap(){
			return contextVecWordsNextTimeMap; 
		}
		
		/**
		 * Retrieves map of words with their indices in contextVecWordsNextTimeMap.
		 * @return
		 */
		public static ImmutableMap<String, Integer> get_contextVecWordsIndexNextTimeMap(){
			return contextVecWordsIndexNextTimeMap; 
		}
		
		/**
		 * Retrieves ImmutableListMultimap of words and the theorems's indices in thmList
		 *  they appear in. Indices of thms are 0-based.
		 * @return
		 */
		/*public static ImmutableMultimap<String, Integer> get_wordThmsMMap(){
			return wordThmsIndexMMap;
		}*/
		
		public static ImmutableMultimap<String, Integer> get_wordThmsMMapNoAnno(){
			return wordThmsIndexMMapNoAnno;
		}
	}
	//***********End of prev class
	/**
	 * Static nested classes that accomodates lazy initialization (so to avoid circular 
	 * dependency), but also gives benefit of final (cause singleton), immutable (make it so).
	 */
	public static class NGramsMap{
		//private static final Map<String, Integer> twoGramsMap = ImmutableMap.copyOf(NGramSearch.get2GramsMap());
		//map of two grams and their frequencies.
		private static final Map<String, Integer> twoGramsMap = NGramSearch.get2GramsMap();		
		private static final Map<String, Integer> threeGramsMap = ThreeGramSearch.get3GramsMap();
		
		/**
		 * Map of two grams and their frequencies.
		 * @return
		 */
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
	 * This class is initialized BEFORE the static subclass ThmWordsMaps
	 */
	public static class ThmList{
		
		private static final ImmutableList<String> allThmsWithHypList;
		//just thm. same order as in allThmsWithHypList.
		private static final ImmutableList<String> allThmsNoHypList;
		//just hyp. same order as in allThmsWithHypList.
		private static final ImmutableList<String> allHypList;
		private static final ImmutableList<String> allThmSrcFileList;
		private static final ImmutableList<ThmHypPair> allThmHypPairList;
		
		private static final ImmutableList<BigInteger> allThmsRelationVecList;
		private static final ImmutableList<String> allThmsContextVecList;
		
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
		//Whether in skip gram gathering mode. Used by CollectThm.ThmWordsMaps.
		private static boolean gather_skip_gram_words;
		
		static{	
			//instead of getting thmList from ThmList, need to get it from serialized data.
			List<ParsedExpression> parsedExpressionsList;
			/* Deserialize objects in parsedExpressionOutputFileStr, so we don't 
			 * need to read and parse through all papers on every server initialization.
			 * Can just read from serialized data. */
			
			parsedExpressionsList = extractParsedExpressionList();
			
			List<String> allThmsWithHypPreList = new ArrayList<String>();
			List<String> allThmsNoHypPreList = new ArrayList<String>();
			List<String> allHypPreList = new ArrayList<String>();
			List<String> allThmSrcFilePreList = new ArrayList<String>();
			List<BigInteger> relationVecPreList = new ArrayList<BigInteger>();
			List<String> contextVecPreList = new ArrayList<String>();
			fillListsFromParsedExpressions(parsedExpressionsList, allThmsWithHypPreList, contextVecPreList, relationVecPreList,
					allThmsNoHypPreList, allHypPreList, allThmSrcFilePreList);			
			
			allThmsWithHypList = ImmutableList.copyOf(allThmsWithHypPreList);
			allThmsNoHypList = ImmutableList.copyOf(allThmsNoHypPreList);
			allHypList = ImmutableList.copyOf(allHypPreList);
			allThmSrcFileList = ImmutableList.copyOf(allThmSrcFilePreList);
			allThmHypPairList = createdThmHypPairListFromLists(allThmsNoHypList, allHypList, allThmSrcFileList);
			
			allThmsContextVecList = ImmutableList.copyOf(contextVecPreList);
			allThmsRelationVecList = ImmutableList.copyOf(relationVecPreList);
			
			ImmutableList.Builder<String> thmListBuilder = ImmutableList.builder();
			List<String> extractedThmsList = new ArrayList<String>();
			List<String> processedThmsList = new ArrayList<String>();
			List<String> macroReplacedThmsList = new ArrayList<String>();
			List<String> webDisplayThmsList = new ArrayList<String>();
			List<String> bareThmsList = new ArrayList<String>();
			//System.out.print("rawFileReader: " + rawFileReader);
			//extractedThms = ThmList.get_thmList();
			try {
				if(null == servletContext){
					//this is the case when resources have not been set by servlet, so not on server.
					for(String fileStr : rawFileStrList){
						//FileReader rawFileReader = new FileReader(rawFileStr);
						FileReader rawFileReader = new FileReader(fileStr);
						BufferedReader rawFileBReader = new BufferedReader(rawFileReader);
						//System.out.println("rawFileReader is null ");
						extractedThmsList.addAll(ThmInput.readThm(rawFileBReader, webDisplayThmsList, bareThmsList));							
						//System.out.print("Should be extracting theorems here: " + extractedThms);
					}
					//the third true means to extract words from latex symbols, eg oplus->direct sum.
					//last boolean is whether to replace macros, 
					FileReader macrosReader = new FileReader(MACROS_SRC);
					BufferedReader macrosBReader = new BufferedReader(macrosReader);
					bareThmsList = ProcessInput.processInput(bareThmsList, false, false, false);
					processedThmsList = ProcessInput.processInput(extractedThmsList, macrosBReader, REPLACE_TEX, TEX_TO_WORDS, REPLACE_MACROS);					
					macroReplacedThmsList = ProcessInput.get_macroReplacedThmList();
					macrosBReader.close();
				}else{
					//System.out.println("read from rawFileReader");
					//System.out.print("ready for processing: " +rawFileReader);
					
					/*System.out.println(rawFileReader);
					String line;
					while((line=rawFileReader.readLine()) != null){
						System.out.println(line);
					}*/ 
					for(String fileStr : rawFileStrList){
						InputStream inputStream = servletContext.getResourceAsStream(fileStr);
						BufferedReader rawFileBReader = new BufferedReader(new InputStreamReader(inputStream));						
						/*FileReader rawFileReader = new FileReader(fileStr);
						BufferedReader rawFileBReader = new BufferedReader(rawFileReader);*/
						
						extractedThmsList.addAll(ThmInput.readThm(rawFileBReader, webDisplayThmsList, bareThmsList));							
						inputStream.close();
						rawFileBReader.close();						
					}
					
					/*for(BufferedReader fileReader : rawFileReaderList){
						extractedThmsList.addAll(ThmInput.readThm(fileReader, webDisplayThmsList, bareThmsList));
					}*/
					//to be used for parsing. Booleans specify options such as whether to
					//convert tex symbols to words, replace macros, etc.
					bareThmsList = ProcessInput.processInput(bareThmsList, true, false, false);
					processedThmsList = ProcessInput.processInput(extractedThmsList, macrosDefReader, REPLACE_TEX, TEX_TO_WORDS, REPLACE_MACROS);
					//the BufferedStream containing macros is set when rawFileReaderList is set.
					macroReplacedThmsList = ProcessInput.get_macroReplacedThmList();
				}
			} catch (IOException e) {
				e.printStackTrace();
				throw new RuntimeException("Processing input via ProcessInput failed!\n", e);
			}
			thmListBuilder.addAll(extractedThmsList);
			thmList = thmListBuilder.build();
			//webDisplayThmList = ImmutableList.copyOf(webDisplayThmsList);
			webDisplayThmList = allThmsWithHypList;
			bareThmList = ImmutableList.copyOf(bareThmsList);
			processedThmList = ImmutableList.copyOf(processedThmsList);
			macroReplacedThmList = ImmutableList.copyOf(macroReplacedThmsList);
		}

		public static void set_gather_skip_gram_words_toTrue(){
			gather_skip_gram_words = true;
		}
		
		private static ImmutableList<ThmHypPair> createdThmHypPairListFromLists(ImmutableList<String> allThmsNoHypList_,
				ImmutableList<String> allHypList_, ImmutableList<String> allThmSrcFileList_) {
			
			List<ThmHypPair> thmpHypPairList = new ArrayList<ThmHypPair>();
			int allHypListSz = allHypList_.size();
			assert allThmsNoHypList_.size() == allHypListSz && allHypListSz == allThmSrcFileList_.size();
			
			for(int i = 0; i < allHypListSz; i++){
				ThmHypPair thmHypPair = new ThmHypPair(allThmsNoHypList_.get(i), allHypList_.get(i), allThmSrcFileList_.get(i));
				thmpHypPairList.add(thmHypPair);
			}			
			return ImmutableList.copyOf(thmpHypPairList);
		}

		public static boolean gather_skip_gram_words(){
			return gather_skip_gram_words;
		}
		
		/**
		 * Extracts parsedExressionList from serialized data.
		 * @return
		 */
		@SuppressWarnings("unchecked")
		private static List<ParsedExpression> extractParsedExpressionList() {
			
			List<ParsedExpression> peList = ResourceDeposit.getParsedExpressionList();
			if(null != peList){
				return peList;
			}
			//List<ParsedExpression> parsedExpressionsList;
			//String parsedExpressionSerialFileStr = "src/thmp/data/parsedExpressionList.dat";
			//String parsedExpressionSerialFileStr = "src/thmp/data/parsedExpressionList.dat";
			String parsedExpressionSerialFileStr = "src/thmp/data/parsedExpressionListTemplate.dat";
			
			if(null != servletContext){
				if(!Searcher.SearchMetaData.gatheringDataBool()){
					parsedExpressionSerialFileStr = ThmSearch.getSystemCombinedParsedExpressionListFilePath();
				}else{
					parsedExpressionSerialFileStr = "src/thmp/data/parsedExpressionList.dat";
				}
				InputStream parsedExpressionListInputStream = servletContext.getResourceAsStream(parsedExpressionSerialFileStr);
				return (List<ParsedExpression>)thmp.utils.FileUtils
						.deserializeListFromInputStream(parsedExpressionListInputStream);	
			}else{
				//when processing on byblis
				if(!System.getProperty("os.name").equals("Mac OS X")){
					if(!Searcher.SearchMetaData.gatheringDataBool()){
						parsedExpressionSerialFileStr = ThmSearch.getSystemCombinedParsedExpressionListFilePath();
					}else{
						parsedExpressionSerialFileStr = "src/thmp/data/parsedExpressionList.dat";
					}
				}
				return (List<ParsedExpression>)thmp.utils.FileUtils
						.deserializeListFromFile(parsedExpressionSerialFileStr);
			}
		}
		
		/**
		 * Fill up thmList, contextvectors, and relational vectors from parsed expressions list
		 * extracted from serialized data.
		 * @param parsedExpressionsList
		 * @param allThmsWithHypList
		 * @param contextVecList
		 * @param relationVecList
		 */
		private static void fillListsFromParsedExpressions(List<ParsedExpression> parsedExpressionsList, 
				List<String> allThmsWithHypList, List<String> contextVecList, List<BigInteger> relationVecList,
				List<String> allThmsNoHypPreList, List<String> allHypPreList, List<String> allThmSrcFilePreList
				){
			//System.out.println("Should be list: " + parsedExpressionsList);
			for(ParsedExpression parsedExpr : parsedExpressionsList){
				DefinitionListWithThm defListWithThm = parsedExpr.getDefListWithThm();
				//get original thm and list of definitions separately, for displaying them separately on the web.
				allThmsNoHypPreList.add(defListWithThm.getThmStr());
				/* Build the definition string here, could be earlier in DetectHypothesis.java,
				 * but in future may want to do different things with the list elements, so better to keep list form.*/
				StringBuilder defListSB = new StringBuilder(200);
				for(VariableDefinition def : defListWithThm.getDefinitionList()){
					defListSB.append(def.getOriginalDefinitionSentence()).append('\n');
				}
				allHypPreList.add(defListSB.toString());
				allThmsWithHypList.add(defListWithThm.getThmWithDefStr());
				
				String fileName = defListWithThm.getSrcFileName();
				if(null != fileName){
					allThmSrcFilePreList.add(fileName);
				}else{
					allThmSrcFilePreList.add("");
				}				
				//allThmSrcFilePreList.add("");
				contextVecList.add(parsedExpr.contextVecStr());
				relationVecList.add(parsedExpr.getRelationVec());
			}
		}		
		
		/**
		 * List of relation vectors for all thms, as extracted from deserialized 
		 * ParsedExpressions.
		 * @return
		 */
		public static ImmutableList<BigInteger> allThmsRelationVecList(){
			return allThmsRelationVecList;
		}

		/**
		 * List of context vectors for all thms, as extracted from deserialized 
		 * ParsedExpressions.
		 * @return
		 */
		public static ImmutableList<String> allThmsContextVecList(){
			return allThmsContextVecList;
		}

		/**
		 * Get list of theorems with their hypotheses and assumptions attached,
		 * as collected by DetectHypothesis.java. As extracted from deserialized 
		 * ParsedExpressions.
		 * @return an immutable list
		 */
		public static ImmutableList<String> allThmsWithHypList(){
			return allThmsWithHypList;
		}
		
		/**
		 * Get list of hypotheses and assumptions,
		 * as collected by DetectHypothesis.java. As extracted from deserialized 
		 * ParsedExpressions.
		 * @return an immutable list
		 */
		public static ImmutableList<String> allThmsNoHypList(){
			return allThmsNoHypList;
		}
		
		/**
		 * Get list of hypotheses and assumptions,
		 * as collected by DetectHypothesis.java. As extracted from deserialized 
		 * ParsedExpressions.
		 * @return an immutable list
		 */
		public static ImmutableList<String> allHypList(){
			return allHypList;
		}
		
		/**
		 * Get source file names.
		 * @return an immutable list
		 */
		public static ImmutableList<String> allThmSrcFileList(){
			return allThmSrcFileList;
		}
		
		/**
		 * Get source file names.
		 * @return an immutable list
		 */
		public static ImmutableList<ThmHypPair> allThmHypPairList(){
			return allThmHypPairList;
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
	
	public static void main(String[] args){
		int a = ThmWordsMaps.CONTEXT_VEC_SIZE;
	}
}
