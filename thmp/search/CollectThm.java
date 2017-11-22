package thmp.search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;

import thmp.parse.Maps;
import thmp.parse.ParsedExpression;
import thmp.parse.DetectHypothesis.DefinitionListWithThm;
import thmp.search.SearchCombined.ThmHypPair;
import thmp.search.Searcher.SearchMetaData;
import thmp.utils.WordForms.WordFreqComparator;
import thmp.utils.FileUtils;
import thmp.utils.GatherRelatedWords;
import thmp.utils.WordForms;
import thmp.utils.GatherRelatedWords.RelatedWords;

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
	
	/* Commented out June 2017.
	 * private static final List<String> rawFileStrList = Arrays.asList(new String[]{
			//"src/thmp/data/testContextVector.txt", 
			//"src/thmp/data/collectThmTestSample.txt"
			"src/thmp/data/fieldsRawTex.txt",
			//"src/thmp/data/CommAlg5.txt", 
			//"src/thmp/data/multilinearAlgebra.txt",
			//"src/thmp/data/functionalAnalysis.txt",			
			//"src/thmp/data/topology.txt"
			});*/
	
	private static final Logger logger = LogManager.getLogger();
	//latex macros source file name src/thmp/data/CommAlg5.txt
	private static final String MACROS_SRC = "src/thmp/data/texMacros.txt";
	
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
	private static final String[] SCORE_AVG_MATH_WORDS = new String[]{"ring", "field", "ideal", "finite", "series",
			"complex", "combination", "regular", "domain", "local", "smooth", "definition", "map", "standard", "prime",
			"injective", "surjective", "commut", "word", "act", "second", "every", "fppf"};
	//could be included, if already included, adjust the score to 1. If not, don't add.
	private static final String[] SCORE1MATH_WORDS = new String[]{"show","have","any", "many", "suppose","end","psl",
			"is"
	};
	//don't use this to affect building words map, since need entry for vital terms such as "is" (despite its insignificance
	//in terms of score), for forming contextual vectors, i.e need "is" in the words index map.
	private static final String[] SCORE0MATH_WORDS = new String[]{"such","say","will", "following","goodwillie","send", "iii",
			"ii","i","both"
	};
	//additional fluff words to add, that weren't listed previously
	private static final String[] ADDITIONAL_FLUFF_WORDS = new String[]{"tex", "is", "are", "an"};
	
	public static class FreqWordsSet{

		//Map of frequent words and their parts of speech (from words file data). Don't need the pos for now.
		private static final Set<String> commonEnglishNonMathWordsSet; 
		
		static{
			//only get the top N words
			commonEnglishNonMathWordsSet = WordFrequency.ComputeFrequencyData.trueFluffWordsSet();
		}
		
		public static Set<String> commonEnglishNonMathWordsSet(){
			return commonEnglishNonMathWordsSet;
		}
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
	/*Commented out June 2017
	 * public static void setResources(//List<BufferedReader> srcFileReaderList, 
			BufferedReader macrosReader
			//InputStream parsedExpressionListStream, InputStream allThmWordsSerialIStream
			) {
		//rawFileReaderList = srcFileReaderList;
		macrosDefReader = macrosReader;
		//parsedExpressionListInputStream = parsedExpressionListStream;
		//allThmWordsSerialInputStream = allThmWordsSerialIStream;
		//System.out.print("buffered readers first passed in: " + srcFileReaderList);		
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
		/*words and their document-wide frequencies. These words are normalized, 
		e.g. "annihilator", "annihiate" all have the single entry "annihilat" */
		private static final ImmutableMap<String, Integer> docWordsFreqMapNoAnno;
		//entries are word and the indices of thms that contain that word.
		private static final ImmutableMultimap<String, Integer> wordThmsIndexMMapNoAnno;
		//map serialized for use during search, contains N-grams. Words and their frequencies.
		/*private static final ImmutableMap<String, Integer> contextVecWordsNextTimeMap;*/
		//to be used next time, words and their indices.
		/*private static final ImmutableMap<String, Integer> contextVecWordsIndexNextTimeMap;*/
		//this size depends on whether currently gathering data or performing search.
		private static final int CONTEXT_VEC_SIZE;
		
		private static final String NAMED_THMS_FILE_STR = "src/thmp/data/thmNames.txt";
		private static final Set<String> FLUFF_WORDS_SET = WordForms.getFluffSet();
		private static final Map<String, Integer> twoGramsMap = NGramsMap.get_twoGramsMap();
		private static final Map<String, Integer> threeGramsMap = NGramsMap.get_threeGramsMap();	
		private static final List<String> skipGramWordsList;
		//set that contains the first word of the two and three grams of twoGramsMap and threeGramsMap		
		//so the n-grams have a chance of being called.
		private static final Set<String> nGramFirstWordsSet = new HashSet<String>();
		private static final int averageSingletonWordFrequency;
		//strip "theorem" away from words, to further reduce number of words
		private static final Pattern THEOREM_END_PATTERN = Pattern.compile("(.+) theorem");
		public static final Pattern THEORY_END_PATTERN = Pattern.compile("(.+) theory");
		
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
		//private static final int NUM_FREQ_WORDS = 500;
		//multiplication factors to deflate the frequencies of 2-grams and 3-grams to weigh
		//them more
		private static final double THREE_GRAM_FREQ_REDUCTION_FACTOR = 3.8/5;
		private static final double TWO_GRAM_FREQ_REDUCTION_FACTOR = 2.3/3;
		
		// \ufffd is unicode representation for the replacement char.
		private static final Pattern SPECIAL_CHARACTER_PATTERN = 
				Pattern.compile(".*[\\\\=$\\{\\}\\[\\]()^_+%&\\./,\"\\d\\/@><*|`�\ufffd].*");
				
		private static final boolean GATHER_SKIP_GRAM_WORDS = ThmList.gather_skip_gram_words();
		//private static final boolean GATHER_SKIP_GRAM_WORDS = true;
		/* Related words scraped from wiktionary, etc. 
		 * Related words are *only* used
		 * to process queries, not the corpus; applied to all search algorithms. Therefore
		 * intentionally *not* final.
		 * Keys to relatedWordsMap are not necessarily normalized, only normalized if key not 
		 * already contained in docWordsFreqMapNoAnno
		 */
		private static final Map<String, GatherRelatedWords.RelatedWords> relatedWordsMap;
		
		static{	
			/*map of words and their representatives, e.g. "annihilate", "annihilator", etc all map to "annihilat"
			i.e. word of maps to their stems. */
			//synonymRepMap = WordForms.getSynonymsMap();
			stemToWordsMMap = WordForms.stemToWordsMMap();
			
			/**Versions with no annotation, eg "hyp"/"stm" **/
			//ImmutableList.Builder<ImmutableMap<String, Integer>> thmWordsListBuilderNoAnno = ImmutableList.builder();
			/* *Only* used in data-gathering mode*/
			/** June 2017 Map<String, Integer> docWordsFreqPreMapNoAnno = new HashMap<String, Integer>();
			ImmutableSetMultimap.Builder<String, Integer> wordThmsMMapBuilderNoAnno = ImmutableSetMultimap.builder();*/
			
			//read in n-grams from file named NGRAM_DATA_FILESTR and put in appropriate maps, 
			//either twogrammap or threegrammap
			//readAdditionalNGrams(NGRAM_DATA_FILESTR);
			
			nGramFirstWordsSet.addAll(NGramSearch.get_2GramFirstWordsSet());
			nGramFirstWordsSet.addAll(ThreeGramSearch.get_3GramFirstWordsSet());
			skipGramWordsList = new ArrayList<String>();

			/** This list is smaller when in gathering data mode, and consists of a representative set 
			 * of theorems. Much larger in search mode.*/
			////.....List<String> processedThmList = ThmList.allThmsWithHypList; 
				
			Map<String, Integer> wordsScorePreMap = new HashMap<String, Integer>();
			
			/*deserialize the word frequency map from file, as gathered from last time the data were generated.*/
			//CONTEXT_VEC_WORDS_FREQ_MAP = extractWordFreqMap();
			
			//the values are just the words' indices in wordsList.
			//this orders the list as well. INDEX map. Can rely on order as map is immutable.
			
			//System.out.println("------++++++++-------CONTEXT_VEC_WORDS_MAP.size " + CONTEXT_VEC_WORDS_MAP.size());
			
			if(!FileUtils.isOSX()) {
				//should not build if not searching
				String wordThmIndexMMapPath = FileUtils.getPathIfOnServlet(SearchMetaData.wordThmIndexMMapSerialFilePath());				
				@SuppressWarnings("unchecked")
				Multimap<String, Integer> wordThmsIndexMultimap = ((List<Multimap<String, Integer>>)
						FileUtils.deserializeListFromFile(wordThmIndexMMapPath)).get(0);
				wordThmsIndexMMapNoAnno = ImmutableMultimap.copyOf(wordThmsIndexMultimap);
			}else {
				wordThmsIndexMMapNoAnno = null;
			}				
				 
				String docWordsFreqMapNoAnnoPath = FileUtils.getPathIfOnServlet(SearchMetaData.wordDocFreqMapPath());
				@SuppressWarnings("unchecked")
				Map<String, Integer> docWordsFreqPreMap = ((List<Map<String, Integer>>)
						FileUtils.deserializeListFromFile(docWordsFreqMapNoAnnoPath)).get(0);
				docWordsFreqMapNoAnno = ImmutableMap.copyOf(docWordsFreqPreMap);
				
				//compute the average word frequencies for singleton words
				averageSingletonWordFrequency = computeSingletonWordsFrequency(docWordsFreqPreMap);			
				//add lexicon words to docWordsFreqMapNoAnno, which only contains collected words from thm corpus,
				//collected based on frequnency, right now. These words do not have corresponding thm indices.
				//***addLexiconWordsToContextKeywordDict(docWordsFreqPreMap, averageSingletonWordFrequency); //<--but these should have been adjusted already!!
				/*use stemToWordsMMap to re-adjust frequency of word stems that came from multiple forms, 
				 as these are much more likely to be math words, so don't want to scale down too much */
				//***adjustWordFreqMapWithStemMultiplicity(docWordsFreqPreMap, stemToWordsMMap);				
				
				buildScoreMapNoAnno(wordsScorePreMap, docWordsFreqMapNoAnno);	

				//***docWordsFreqMapNoAnno = ImmutableMap.copyOf(keyWordFreqTreeMap); //<--previous one
				/* RelatedWordsMap is only used during search, not data gathering! 
				 * Keys to relatedWordsMap are not necessarily normalized, only normalized if key not 
				 * already contained in docWordsFreqMapNoAnno.*/
				relatedWordsMap = deserializeAndProcessRelatedWordsMapFromFile(docWordsFreqMapNoAnno);
				CONTEXT_VEC_WORDS_FREQ_MAP = docWordsFreqMapNoAnno;
				//The underlying map is a tree map, don't create immutable map from it for now.
				CONTEXT_VEC_WORDS_INDEX_MAP = createContextKeywordIndexDict(docWordsFreqMapNoAnno);
			/*}else{				
				buildScoreMapNoAnno(wordsScorePreMap, CONTEXT_VEC_WORDS_FREQ_MAP);
				/*Do *not* re-order map based on frequency, since need to be consistent with word row
				 * indices in term document matrix. Also should already be ordered. */
				/*Commented out June 2017.
				 * docWordsFreqMapNoAnno = CONTEXT_VEC_WORDS_FREQ_MAP;	
				relatedWordsMap = deserializeAndProcessRelatedWordsMapFromFile(docWordsFreqMapNoAnno);
				CONTEXT_VEC_WORDS_INDEX_MAP = createContextKeywordIndexDict(CONTEXT_VEC_WORDS_FREQ_MAP);
			}*/
			
			wordsScoreMapNoAnno = ImmutableMap.copyOf(wordsScorePreMap);
			System.out.println("*********wordsScoreMapNoAnno.size(): " + wordsScoreMapNoAnno.size());
			//should be built separately, and combined at end, 
			//wordThmsIndexMMapNoAnno = wordThmsMMapBuilderNoAnno.build();
			CONTEXT_VEC_SIZE = docWordsFreqMapNoAnno.size();
			/***This is where the set of words used for SVD search and search based on context and relational vectors
			 * diverge. The latter contains additional words (N-grams) added below. Note these words
			 * are used for NGram formation NEXT run (generating ParsedExpressionList)***/ //<--actually now they are the same
			
			//Must add the 2 and 3 grams to docWordsFreqPreMapNoAnno. The N-grams that actually occur in 
			//this corpus of theorems have already been added to docWordsFreqPreMapNoAnno during buildMaps.
			//docWordsFreqPreMapNoAnno.putAll(twoGramsMap);
			//docWordsFreqPreMapNoAnno.putAll(threeGramsMap);
			//map to be serialized, and used for forming context vectors in next run.
			/* Commented out June 2017.
			 * contextVecWordsNextTimeMap = docWordsFreqMapNoAnno;
			//shouldn't need this, since can reconstruct index from immutableMap next time
			contextVecWordsIndexNextTimeMap = ImmutableMap.copyOf(createContextKeywordIndexDict(contextVecWordsNextTimeMap));*/
			//write skipGramWordsList to file
			if(GATHER_SKIP_GRAM_WORDS){
				String skipGramWordsListFileStr = "src/thmp/data/skipGramWordsList.txt";
				FileUtils.writeToFile(skipGramWordsList, skipGramWordsListFileStr);
			}
		}
		
		/**
		 * Builds the relevant maps. E.g. to gather a comprehensive and representative
		 * set of word frequency maps.
		 * @param docWordsFreqPreMapNoAnno
		 * @param wordThmsMMapBuilderNoAnno
		 * @param wordsScorePreMap Ordered w.r.t. frequency, so to optimize 
		 * forming relation search vecs, which are BigInteger's.
		 */
		public static Map<String, Integer> buildDocWordsFreqMap(List<String> thmList) {
			
			//individual words used in two-grams, since they are math two grams with high prob,
			//the component words are very likely to be math words (verified with observation).
			Set<String> twoGramComponentWordsSingularSet = new HashSet<String>();
			Map<String, Integer> docWordsFreqPreMap = buildDocWordsFreqMap2(thmList, twoGramComponentWordsSingularSet);
			
			//first compute the average word frequencies for singleton words
			int avgSingletonWordFreq = computeSingletonWordsFrequency(docWordsFreqPreMap);	
			
			//add the singleton words in named theorems, e.g. Ax-Grothendieck , mostly mathematicians' names
			addNamedThmsToMap(docWordsFreqPreMap, avgSingletonWordFreq);
			
			for(String word : twoGramComponentWordsSingularSet){
				//singleton words are all normalized
				word = WordForms.normalizeWordForm(word);
				if(!docWordsFreqPreMap.containsKey(word)){
					docWordsFreqPreMap.put(word, avgSingletonWordFreq);
				}
			}			
			//add lexicon words to docWordsFreqMapNoAnno, which only contains collected words from thm corpus,
			//collected based on frequnency, right now. These words do not have corresponding thm indices.
			addLexiconWordsToContextKeywordDict(docWordsFreqPreMap, avgSingletonWordFreq);
			/*use stemToWordsMMap to re-adjust frequency of word stems that came from multiple forms, 
			 as these are much more likely to be math words, so don't want to scale down too much */
			adjustWordFreqMapWithStemMultiplicity(docWordsFreqPreMap, stemToWordsMMap);		
			
			removeLowFreqWords(docWordsFreqPreMap);			

			//ReorderDocWordsFreqMap uses a TreeMap to reorder. Used to optimize 
			//forming relation search vecs, which are BigInteger's.
			//Wrap in HashMap, since the comparator for the TreeMap depends on a frequency map, which can be fragile.
			return new HashMap<String, Integer>(reorderDocWordsFreqMap(docWordsFreqPreMap));			
		}
		
		/**
		 * Add the singleton words in named theorems, e.g. Ax-Grothendieck , mostly mathematicians' names
		 * @param docWordsFreqPreMap
		 */
		private static void addNamedThmsToMap(Map<String, Integer> docWordsFreqPreMap,
				int avgSingletonWordFreq) {
			String namedThmsFileStr = FileUtils.getPathIfOnServlet(NAMED_THMS_FILE_STR);
			try{
				BufferedReader bReader = new BufferedReader(new FileReader(namedThmsFileStr));
				try{
					String line;
					while((line = bReader.readLine()) != null){
						//split line into tokens
						String lineNoDiacritics = WordForms.removeDiacritics(line.toLowerCase());
						List<String> lineAr = WordForms.splitThmIntoSearchWords(lineNoDiacritics);
						for(String word : lineAr){
							if(word.length() < 3){
								//e.g. "of", but include e.g. "lie"
								continue;
							}
							//System.out.println("CollectThm word - " + word);
							if(!docWordsFreqPreMap.containsKey(word)){
								docWordsFreqPreMap.put(word, avgSingletonWordFreq);															
							}
						}
					}
				}finally{
					FileUtils.silentClose(bReader);
				}
			}catch(FileNotFoundException e){
				//same treatment as IOException for now
				throw new IllegalStateException("FileNotFoundException when adding Named theorems!", e);
			}catch(IOException e){
				throw new IllegalStateException("IOException when adding Named theorems!", e);
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
		 *	E.g. annihil is the stem for both annihilator and annihilate, don't want to score annihil
		 *	based on the combined frequency of annihilator and annihilate.
		 * @param docWordsFreqPreMapNoAnno
		 * @param stemtowordsmmap2
		 */
		private static void adjustWordFreqMapWithStemMultiplicity(Map<String, Integer> docWordsFreqPreMapNoAnno,
				ImmutableMultimap<String, String> stemToWordsMMap_) {
			double freqAdjustmentFactor = 3.0/4;
			Map<String, Integer> stockFrequencyMap = WordFrequency.ComputeFrequencyData.englishStockFreqMap();
			Map<String, Integer> modifiedWordFreqMap = new HashMap<String, Integer>();
			Iterator<Map.Entry<String, Integer>> freqMapIter = docWordsFreqPreMapNoAnno.entrySet().iterator();
			while(freqMapIter.hasNext()){
				Map.Entry<String, Integer> entry = freqMapIter.next();				
				String wordStem = entry.getKey();
				if(stemToWordsMMap_.containsKey(wordStem)){
					int formsCount = (int)(stemToWordsMMap_.get(wordStem).size()*freqAdjustmentFactor);
					//pre-processing should have eliminated stems with freq 1
					if(formsCount < 2) continue;
					int adjustedFreq = entry.getValue()/formsCount;
					adjustedFreq = adjustedFreq > 0 ? adjustedFreq : 1;
					modifiedWordFreqMap.put(wordStem, adjustedFreq);
				}
				//eliminate duplicates, if a word and its singleton forms are both included,
				//e.g graph, graphs
				if(stockFrequencyMap.containsKey(wordStem)){
					//valid word, don't need singularize
					continue;
				}
				String wordSingular = WordForms.getSingularForm(wordStem);
				Integer singularWordFreq = docWordsFreqPreMapNoAnno.get(wordSingular);
				if(null != singularWordFreq && !wordStem.equals(wordSingular)){
					modifiedWordFreqMap.put(wordSingular, (int)((singularWordFreq + entry.getValue())*3./4));
					freqMapIter.remove();
					//logger.info(wordStem + " removed in favor of " + wordSingular);
				}
				//if(FreqWordsSet.commonEnglishNonMathWordsSet.contains(word)
				Matcher m;
				
				if((m=THEOREM_END_PATTERN.matcher(wordStem)).matches()){
					freqMapIter.remove();
					String word = m.group(1);
					//eliminate e.g. "main".
					if(!FreqWordsSet.commonEnglishNonMathWordsSet.contains(word)){						
						modifiedWordFreqMap.put(word, entry.getValue());						
					}
				}
			}
			docWordsFreqPreMapNoAnno.putAll(modifiedWordFreqMap);
		}
		
		/**
		 * deserialize words map used to form context and relation vectors, which were
		 * formed while parsing through the papers in e.g. DetectHypothesis.java. This is
		 * so we don't parse everything again at every server initialization.
		 * 
		 * @return Map of words and their frequencies.
		 */
		@SuppressWarnings("unchecked")
		private static ImmutableMap<String, Integer> extractWordFreqMap() {	
			//It is "src/thmp/data/allThmWordsMap.dat";			
			String pathToPrevDocWordFreqMaps = Searcher.SearchMetaData.previousWordDocFreqMapsPath();
			String allThmWordsSerialFileStr = (null == pathToPrevDocWordFreqMaps 
					? thmp.parse.DetectHypothesis.allThmWordsMapSerialFileStr : pathToPrevDocWordFreqMaps);
			
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
			//Should already been ordered from previous run! 
			int counter = 0;
			for(Map.Entry<String, Integer> entry : docWordsFreqPreMapNoAnno.entrySet()){				
				contextKeywordIndexDict.put(entry.getKey(), counter++);
			}
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
		 * Remove the low-freq words, to reduce number of words.
		 * Right now remove words with freq 1 and 2.
		 * @param docWordsFreqPreMapNoAnno
		 */
		private static void removeLowFreqWords(Map<String, Integer> docWordsFreqPreMapNoAnno) {
			Iterator<Entry<String, Integer>> entrySetIter = docWordsFreqPreMapNoAnno.entrySet().iterator() ;
			while(entrySetIter.hasNext()){
				int freq = entrySetIter.next().getValue();
				//remove the low-freq words, to reduce number of words
				if(freq < 3){
					entrySetIter.remove();
					//continue;
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
			
			Iterator<Entry<String, Integer>> entrySetIter = docWordsFreqPreMapNoAnno.entrySet().iterator() ;
			while(entrySetIter.hasNext()){
				int freq = entrySetIter.next().getValue();
				//don't count the low-freq words, they will be removed to reduce number of words
				if(freq < 2){
					continue;
				}
				freqSum += freq;
				count++;
			}
			
			/*for(int freq : docWordsFreqPreMapNoAnno.values()){
				freqSum += freq;
				count++;
			}*/
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
		 * Used for building skip gram word list.
		 * @param thmList
		 * @param skipGramWordList_
		 * @throws IOException
		 * @throws FileNotFoundException
		 */
		public static void createSkipGramWordList(List<String> thmList,
				List<String> skipGramWordList_){
			System.out.print("Inside createSkipGramWordList!");
			Map<String, Integer> docWordsFreqPreMap = new HashMap<String, Integer>();
			ImmutableSetMultimap.Builder<String, Integer> wordThmsMMapBuilder 
				= new ImmutableSetMultimap.Builder<String, Integer>();			
			buildMapsNoAnno(//thmWordsFreqListBuilder, 
					docWordsFreqPreMap, wordThmsMMapBuilder, thmList, skipGramWordList_);						
		}
		
		/**
		 * Add words in a given theorem to word-thm-index MMap that will 
		 * be used for intersection search. 
		 * @param wordThmsMMapBuilder
		 * @param thm
		 * @param thmIndex
		 */
		public static void addToWordThmIndexMap(Multimap<String, Integer> wordThmsMMap,
				String thm, int thmIndex){			
			Map<String, Integer> twoGramsMap = NGramsMap.get_twoGramsMap();
			Map<String, Integer> threeGramsMap = NGramsMap.get_threeGramsMap();			
			//ListMultimap<String, String> posMMap = Maps.posMMap();
			
			//for(int i = 0; i < thmList.size(); i++){
				//System.out.println(counter++);
				//String thm = thmList.get(i);
				//number of words to skip if an n gram has been added.
			//int numFutureWordsToSkip = 0;
				//split along e.g. "\\s+|\'|\\(|\\)|\\{|\\}|\\[|\\]|\\.|\\;|\\,|:"
			List<String> thmAr = WordForms.splitThmIntoSearchWords(thm.toLowerCase());				
				//words and their frequencies.
				//Map<String, Integer> thmWordsFreqMap = new HashMap<String, Integer>();				
			int thmArSz = thmAr.size();
			for(int j = 0; j < thmArSz; j++){
				String word = thmAr.get(j);	
					//only keep words with lengths > 2
					//System.out.println(word);
					int lengthCap = 3;
					//word length could change, so no assignment to variable.
					if(word.length() < lengthCap){
						continue;
					}					
					//get singular forms if plural, put singular form in map
					//Should be more careful on some words that shouldn't be singular-ized!					
					word = WordForms.getSingularForm(word);						
					//also don't skip if word is contained in lexicon					
					if(FLUFF_WORDS_SET.contains(word)){ 
						continue;
					}					
					if(FreqWordsSet.commonEnglishNonMathWordsSet.contains(word) && !nGramFirstWordsSet.contains(word)){ 
						continue;					
					}
					//check the following word
					if(j < thmArSz-1){
						String nextWordCombined = word + " " + thmAr.get(j+1);
						nextWordCombined = WordForms.normalizeTwoGram(nextWordCombined);
						Integer twoGramFreq = twoGramsMap.get(nextWordCombined);
						if(twoGramFreq != null){
							if(!SPECIAL_CHARACTER_PATTERN.matcher(nextWordCombined).find()){
								wordThmsMMap.put(nextWordCombined, thmIndex);
							}
						}
						//try to see if these three words form a valid 3-gram
						if(j < thmArSz-2){
							String threeWordsCombined = nextWordCombined + " " + thmAr.get(j+2);
							Integer threeGramFreq = threeGramsMap.get(threeWordsCombined);
							if(threeGramFreq != null){
								if(!SPECIAL_CHARACTER_PATTERN.matcher(threeWordsCombined).find()){
									wordThmsMMap.put(threeWordsCombined, thmIndex);
								}
							}
						}
					}					
					//removes endings such as -ing, and uses synonym rep.
					//e.g. "annihilate", "annihilator", etc all map to "annihilat"
					word = WordForms.normalizeWordForm(word);					
					if(!SPECIAL_CHARACTER_PATTERN.matcher(word).find()){
						wordThmsMMap.put(word, thmIndex);
					}
				}
		}
		
		/**
		 * Builds map when supplied externally with list of theorems.
		 * @param docWordsFreqPreMap
		 * @param wordThmsMMapBuilder
		 * @param thmList
		 */
		private static Map<String, Integer> buildDocWordsFreqMap2(List<String> thmList, 
				Set<String> twoGramComponentWordsSingularSet){	
			
			Map<String, Integer> docWordsFreqPreMap = new HashMap<String, Integer>();
			Map<String, Integer> twoGramsMap = NGramsMap.get_twoGramsMap();
			Map<String, Integer> threeGramsMap = NGramsMap.get_threeGramsMap();	
			
			//add all N-grams at once, instead of based on current theorem set.
			////docWordsFreqPreMap.putAll(twoGramsMap);
			//gather individual word tokens that are part of two grams.
			for(Map.Entry<String, Integer> twoGramEntry : twoGramsMap.entrySet()){
				String twoGram = WordForms.normalizeTwoGram(twoGramEntry.getKey());
				int freq = (int)(twoGramEntry.getValue()*TWO_GRAM_FREQ_REDUCTION_FACTOR);
				if(freq < 2){
					//now skip word, since these are unlikely to be valid n-grams due to 
					//their low frequency (observation-based).
					continue;
				}
				/////don't delete yet freq = freq == 0 ? 1 : freq;				
				docWordsFreqPreMap.put(twoGram, freq);
				
				String[] twoGramAr = WordForms.getWhiteNonEmptySpaceNotAllPattern().split(twoGram);
				twoGramComponentWordsSingularSet.add(WordForms.getSingularForm(twoGramAr[0]));
				//second word should have been singularized already.
				twoGramComponentWordsSingularSet.add(WordForms.getSingularForm(twoGramAr[1]));
			}
			for(Map.Entry<String, Integer> threeGramEntry : threeGramsMap.entrySet()){
				int freq = (int)(threeGramEntry.getValue()*THREE_GRAM_FREQ_REDUCTION_FACTOR);
				if(freq < 2){
					//now skip word, since these are unlikely to be valid n-grams due to 
					//their low frequency (observation-based).
					continue;
				}
				/////freq = freq == 0 ? 1 : freq;
				docWordsFreqPreMap.put(threeGramEntry.getKey(), freq);
			}			
			for(int i = 0; i < thmList.size(); i++){
				//System.out.println(counter++);
				String thm = thmList.get(i);				
				//split along e.g. "\\s+|\'|\\(|\\)|\\{|\\}|\\[|\\]|\\.|\\;|\\,|:"
				List<String> thmAr = WordForms.splitThmIntoSearchWords(thm.toLowerCase());
				int thmArSz = thmAr.size();
				for(int j = 0; j < thmArSz; j++){					
					/*String singletonWordAdded = null;
					String twoGramAdded = null;
					String threeGramAdded = null;*/				
					String word = thmAr.get(j);	
					//only want lower alphabetical words for now, to reduce num of words - July 2017
					if(!NGramSearch.ALPHA_PATTERN.matcher(word).matches()){
						continue;
					}
					//skip words that contain special characters
					if(WordForms.SPECIAL_CHARS_PATTERN.matcher(word).matches()){
						continue;
					}					
					//only keep words with lengths > 2
					//System.out.println(word);
					int lengthCap = 3;
					//word length could change, so no assignment to variable.
					if(word.length() < lengthCap){
						continue;
					}		
					//get singular forms if plural, put singular form in map
					//Should be more careful on some words that shouldn't be singular-ized!					
					word = WordForms.getSingularForm(word);	
					
					//also don't skip if word is contained in lexicon
					if(FLUFF_WORDS_SET.contains(word)){ 
						continue;
					}					
					if(FreqWordsSet.commonEnglishNonMathWordsSet.contains(word) && !nGramFirstWordsSet.contains(word)){ 
						continue;					
					}
					//addNGramsToMap(docWordsFreqPreMap, twoGramsMap, threeGramsMap, i, thmAr, j, word);					
					//removes endings such as -ing, and uses synonym rep.
					//e.g. "annihilate", "annihilator", etc all map to "annihilat"
					word = WordForms.normalizeWordForm(word);
					addWordToMaps(word, i, //thmWordsFreqMap, //thmWordsFreqListBuilder, 
							docWordsFreqPreMap);
				}//done iterating through this thm
			}
			return docWordsFreqPreMap;
		}
		/**
		 * @param docWordsFreqPreMap
		 * @param twoGramsMap
		 * @param threeGramsMap
		 * @param i
		 * @param thmAr
		 * @param thmWordsFreqMap
		 * @param j
		 * @param word
		 * @deprecated July 2017. Wait one month and delete.
		 */
		private static void addNGramsToMap(Map<String, Integer> docWordsFreqPreMap, Map<String, Integer> twoGramsMap,
				Map<String, Integer> threeGramsMap, int i, String[] thmAr, //Map<String, Integer> thmWordsFreqMap, 
				int j,
				String word) {
			//check the following word
			if(j < thmAr.length-1){
				String nextWordCombined = word + " " + thmAr[j+1];
				nextWordCombined = WordForms.normalizeTwoGram(nextWordCombined);
				Integer twoGramFreq = twoGramsMap.get(nextWordCombined);
				if(twoGramFreq != null){
					int freq = (int)(twoGramFreq*TWO_GRAM_FREQ_REDUCTION_FACTOR);
					twoGramFreq = freq == 0 ? 1 : freq;
					addNGramToMaps(nextWordCombined, i, //thmWordsFreqMap, //thmWordsFreqListBuilder, 
							docWordsFreqPreMap, twoGramFreq);
				}
				//try to see if these three words form a valid 3-gram
				if(j < thmAr.length-2){
					String threeWordsCombined = nextWordCombined + " " + thmAr[j+2];
					Integer threeGramFreq = threeGramsMap.get(threeWordsCombined);
					if(threeGramFreq != null){
						//reduce frequency so 3-grams weigh more 
						int freq = (int)(threeGramFreq*THREE_GRAM_FREQ_REDUCTION_FACTOR);
						threeGramFreq = freq == 0 ? 1 : freq;
						addNGramToMaps(threeWordsCombined, i, //thmWordsFreqMap, //thmWordsFreqListBuilder, 
								docWordsFreqPreMap, threeGramFreq);
					}
				}
			}
		}
		
		/**
		 * Same as readThm, except without hyp/concl wrappers.
		 * Maps contain the same set of words. 
		 * @deprecated *Note* June 2017: keep this for now for the addition to skipGramWordList.
		 * Delete a few months later.
		 * @param thmWordsFreqListBuilder 
		 * 		List of maps, each of which is a map of word Strings and their frequencies. Used for SVD search.
		 * @param thmListBuilder
		 * @param docWordsFreqPreMap
		 * @param wordThmsMMapBuilder
		 * @throws IOException
		 * @throws FileNotFoundException
		 */
		private static void buildMapsNoAnno(//ImmutableList.Builder<ImmutableMap<String, Integer>> thmWordsFreqListBuilder,
				Map<String, Integer> docWordsFreqPreMap,
				ImmutableSetMultimap.Builder<String, Integer> wordThmsMMapBuilder, List<String> thmList,
				List<String> skipGramWordList_){
			
			Map<String, Integer> twoGramsMap = NGramsMap.get_twoGramsMap();
			Map<String, Integer> threeGramsMap = NGramsMap.get_threeGramsMap();			
			ListMultimap<String, String> posMMap = Maps.posMMap();
			
			//processes the theorems, select the words
			for(int i = 0; i < thmList.size(); i++){
				//System.out.println(counter++);
				String thm = thmList.get(i);
				//number of words to skip if an n gram has been added.
				int numFutureWordsToSkip = 0;
				//split along e.g. "\\s+|\'|\\(|\\)|\\{|\\}|\\[|\\]|\\.|\\;|\\,|:"
				List<String> thmAr = WordForms.splitThmIntoSearchWords(thm.toLowerCase());
				
				//words and their frequencies.
			//	Map<String, Integer> wordsFreqMap = new HashMap<String, Integer>();				
				int thmArSz = thmAr.size();
				for(int j = 0; j < thmArSz; j++){
					
					String singletonWordAdded = null;
					String twoGramAdded = null;
					String threeGramAdded = null;					
					String word = thmAr.get(j);	
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
					if(FreqWordsSet.commonEnglishNonMathWordsSet.contains(word) && !nGramFirstWordsSet.contains(word)
							&& skipWordBasedOnPos){ 
						continue;					
					}
					//check the following word
					if(j < thmArSz-1){
						String nextWordCombined = word + " " + thmAr.get(j+1);
						nextWordCombined = WordForms.normalizeTwoGram(nextWordCombined);
						Integer twoGramFreq = twoGramsMap.get(nextWordCombined);
						if(twoGramFreq != null){
							int freq = (int)(twoGramFreq*TWO_GRAM_FREQ_REDUCTION_FACTOR);
							twoGramFreq = freq == 0 ? 1 : freq;
							twoGramAdded = addNGramToMaps(nextWordCombined, i, //wordsFreqMap, //thmWordsFreqListBuilder, 
									docWordsFreqPreMap, twoGramFreq);
						}
						//try to see if these three words form a valid 3-gram
						if(j < thmArSz-2){
							String threeWordsCombined = nextWordCombined + " " + thmAr.get(j+2);
							Integer threeGramFreq = threeGramsMap.get(threeWordsCombined);
							if(threeGramFreq != null){
								//reduce frequency so 3-grams weigh more 
								int freq = (int)(threeGramFreq*THREE_GRAM_FREQ_REDUCTION_FACTOR);
								threeGramFreq = freq == 0 ? 1 : freq;
								threeGramAdded = addNGramToMaps(threeWordsCombined, i, //wordsFreqMap, 
										docWordsFreqPreMap, threeGramFreq);
							}
						}
					}					
					if(!GATHER_SKIP_GRAM_WORDS){
						//removes endings such as -ing, and uses synonym rep.
						//e.g. "annihilate", "annihilator", etc all map to "annihilat"
						word = WordForms.normalizeWordForm(word);
					}
					singletonWordAdded = addWordToMaps(word, i, //wordsFreqMap, //thmWordsFreqListBuilder, 
							docWordsFreqPreMap);
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
				}
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
		private static String addNGramToMaps(String word, int curThmIndex,// Map<String, Integer> thmWordsMap,
				Map<String, Integer> docWordsFreqPreMap,
				int wordTotalFreq){			
			
			if(SPECIAL_CHARACTER_PATTERN.matcher(word).find()){
				return null;
			}
			/*Integer wordFreq = thmWordsMap.get(word);
			wordFreq = null == wordFreq ? 0 : wordFreq;
			thmWordsMap.put(word, wordFreq + 1);	*/
			//only add word freq to global doc word frequency if not already done so.
			if(!docWordsFreqPreMap.containsKey(word)){
				docWordsFreqPreMap.put(word, wordTotalFreq);
			}
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
		private static String addWordToMaps(String word, int curThmIndex, //Map<String, Integer> thmWordsFreqMap,
				Map<String, Integer> docWordsFreqPreMap
				//, ImmutableSetMultimap.Builder<String, Integer> wordThmsMMapBuilder
				){			
			
			//screen the word for special characters, e.g. "/", don't put these words into map.
			if(SPECIAL_CHARACTER_PATTERN.matcher(word).find()){
				return null;
			}
			/*Integer wordFreq = thmWordsFreqMap.get(word);
			wordFreq = null == wordFreq ? 0 : wordFreq;
			thmWordsFreqMap.put(word, wordFreq + 1);*/
			
			Integer docWordFreq = docWordsFreqPreMap.get(word);
			docWordFreq = null == docWordFreq ? 0 : docWordFreq;			
			//increase freq of word by 1
			docWordsFreqPreMap.put(word, docWordFreq + 1);
			
			//wordThmsMMapBuilder.put(word, curThmIndex);
			return word;
		}
		
		/**
		 * Fills up wordsScorePreMap
		 * @param wordsScorePreMap empty map to be filled.
		 * @param docWordsFreqPreMapNoAnno Map of words and their document-wide frequencies.
		 */
		public static void buildScoreMapNoAnno(Map<String, Integer> wordsScorePreMap,
				Map<String, Integer> docWordsFreqPreMapNoAnno){		
			
			int avgScore = addWordScoresFromMap(wordsScorePreMap, docWordsFreqPreMapNoAnno);
			//System.out.println("docWordsFreqMapNoAnno "+docWordsFreqMapNoAnno);
			//put 2 grams in, freq map should already contain 2 grams
			//addWordScoresFromMap(wordsScorePreMap, twoGramsMap);
			
			//put 1 for math words that occur more frequently than the cutoff, but should still be counted, like "ring"	
			//right now (June 2017) avg still high, around 18.
			avgScore = (int)(avgScore * 2./3);
			avgScore = avgScore == 0 ? 2 : avgScore;
			for(String word : SCORE_AVG_MATH_WORDS){ 
				wordsScorePreMap.put(word, avgScore);
			}
			
			for(String word : SCORE1MATH_WORDS){
				if(wordsScorePreMap.containsKey(word)){
					wordsScorePreMap.put(word, 1);
				}
			}
			
			for(String word : SCORE0MATH_WORDS){ 
				wordsScorePreMap.remove(word);
			}
		}
		
		/**
		 * Auxiliary method for buildScoreMapNoAnno. Adds word scores from the desired map.
		 * @param wordsScorePreMap
		 * @return average score of words
		 */
		private static int addWordScoresFromMap(Map<String, Integer> wordsScorePreMap, Map<String, Integer> mapFrom) {
			int totalScore = 0;
			for(Entry<String, Integer> entry : mapFrom.entrySet()){
				//+1 so not to divide by 0.
				//wordsScoreMapBuilderNoAnno.put(entry.getKey(), (int)Math.round(1/Math.log(entry.getValue()+1)*10) );
				//wordsScoreMapBuilderNoAnno.put(entry.getKey(), (int)Math.round(1/Math.pow(entry.getValue(), 1.25)*200) );
				String word = entry.getKey();
				//if(word.equals("tex")) continue;
				int wordFreq = entry.getValue();
				//*Keep* these comments. Experimenting with scoring parameters.
				//for 1200 thms, CommAlg5 + distributions:
				//int score = wordFreq < 110 ? (int)Math.round(10 - wordFreq/4) : wordFreq < 300 ? 1 : 0;	
				//int score = wordFreq < 180 ? (int)Math.round(15 - wordFreq/4) : wordFreq < 450 ? 1 : 0;
				//until april 1:
				//int score = wordFreq < 40 ? (int)Math.round(10 - wordFreq/3) : (wordFreq < 180 ? (int)Math.round(15 - wordFreq/3) : (wordFreq < 450 ? 3 : 0));	
				//starting April 1:
				int score = wordFreq < 40 ? (int)Math.round(20 - wordFreq/3) : (wordFreq < 180 ? (int)Math.round(35 - wordFreq/4) 
						: (wordFreq < 350 ? 4 : (wordFreq < 450 ? 3 : 3)));	
				//frequently occurring words, should not score too low since they are mostly math words.
				score = score <= 0 ? 3 : score;
				wordsScorePreMap.put(word, score);
				totalScore += score;
				//System.out.print("word: "+word +" score: "+score + " freq "+ wordFreq + "$   ");
			}
			return totalScore/mapFrom.size();
		}
		
		/**
		 * Retrieves map of scores corresponding to words
		 * @return
		 */
		public static ImmutableMap<String, Integer> get_wordsScoreMap(){
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
		public static ImmutableMap<String, Integer> get_docWordsFreqMap(){
			return docWordsFreqMapNoAnno; 
		}

		/**
		 * Retrieves map of words with their document-wide frequencies.
		 * @return
		 */
		/*public static ImmutableMap<String, Integer> get_contextVecWordsNextTimeMap(){
			return contextVecWordsNextTimeMap; 
		}*/
		
		/**
		 * Retrieves map of words with their indices in contextVecWordsNextTimeMap.
		 * @return
		 */
		/*public static ImmutableMap<String, Integer> get_contextVecWordsIndexNextTimeMap(){
			return contextVecWordsIndexNextTimeMap; 
		}*/
		
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
	 * Note: This class is initialized BEFORE the static subclass ThmWordsMaps.
	 */
	public static class ThmList{
		
		//*******private static final ImmutableList<String> allThmsWithHypList;
		//just thm. same order as in allThmsWithHypList.
		/*private static final ImmutableList<String> allThmsNoHypList;
		//just hyp. same order as in allThmsWithHypList.
		private static final ImmutableList<String> allHypList;
		private static final ImmutableList<String> allThmSrcFileList;*/
		///private static final int numThms;
		//****private static final ImmutableList<ThmHypPair> allThmHypPairList;
		
		//private static final ImmutableList<BigInteger> allThmsRelationVecList;
		//private static final ImmutableList<String> allThmsContextVecList;
		
		//private static final ImmutableList<String> thmList;
		//processed with options to replace tex with latex, and expand macros with their definitions
		//list of theorems for web display, without \label{} or \index{} etc
		//private static final ImmutableList<String> webDisplayThmList;	
		/*Commented out June 2017.
		 * private static final ImmutableList<String> processedThmList;		
		//list of bare theorems, without label content 
		private static final ImmutableList<String> bareThmList;	
		//thm list with just macros replaced
		private static final ImmutableList<String> macroReplacedThmList;
		//whether to replace latex symbols with the word "tex"
		private static final boolean REPLACE_TEX = true;
		//whether to extract words from latex symbols, eg oplus->direct sum.
		private static final boolean TEX_TO_WORDS = true;
		//whether to expand macros to their definitions
		private static final boolean REPLACE_MACROS = true;*/
		//Whether in skip gram gathering mode. Used by CollectThm.ThmWordsMaps.
		private static boolean gather_skip_gram_words;
		
		static{	
			//instead of getting thmList from ThmList, need to get it from serialized data.
			//List<ParsedExpression> parsedExpressionsList;
			/* Deserialize objects in parsedExpressionOutputFileStr, so we don't 
			 * need to read and parse through all papers on every server initialization.
			 * Can just read from serialized data. */
			
			//need to modularize to multiple lists stored in cache!!!
			/**parsedExpressionsList = extractParsedExpressionList();
			
			List<String> allThmsWithHypPreList = new ArrayList<String>();
			List<String> allThmsNoHypPreList = new ArrayList<String>();
			List<String> allHypPreList = new ArrayList<String>();
			List<String> allThmSrcFilePreList = new ArrayList<String>();
			fillListsFromParsedExpressions(parsedExpressionsList, allThmsWithHypPreList,
					allThmsNoHypPreList, allHypPreList, allThmSrcFilePreList);			
			
			allThmsWithHypList = ImmutableList.copyOf(allThmsWithHypPreList);
			
			allThmHypPairList = createdThmHypPairListFromLists(allThmsNoHypPreList, allHypPreList, allThmSrcFilePreList);
			numThms = allThmHypPairList.size();*/
			
			/*ImmutableList.Builder<String> thmListBuilder = ImmutableList.builder();
			List<String> extractedThmsList = new ArrayList<String>();
			List<String> processedThmsList = new ArrayList<String>();
			List<String> macroReplacedThmsList = new ArrayList<String>();
			List<String> webDisplayThmsList = new ArrayList<String>();
			List<String> bareThmsList = new ArrayList<String>();*/
			//extractedThms = ThmList.get_thmList();
			/* Commented out June 2017.
			 * try {
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
					
					for(String fileStr : rawFileStrList){
						InputStream inputStream = servletContext.getResourceAsStream(fileStr);
						BufferedReader rawFileBReader = new BufferedReader(new InputStreamReader(inputStream));						
						extractedThmsList.addAll(ThmInput.readThm(rawFileBReader, webDisplayThmsList, bareThmsList));							
						inputStream.close();
						rawFileBReader.close();						
					}
					
					//to be used for parsing. Booleans specify options such as whether to
					//convert tex symbols to words, replace macros, etc.
					bareThmsList = ProcessInput.processInput(bareThmsList, true, false, false);
					
					macrosDefReader 
						= new BufferedReader(new InputStreamReader(servletContext.getResourceAsStream("src/thmp/data/texMacros.txt")));	
					processedThmsList = ProcessInput.processInput(extractedThmsList, macrosDefReader, REPLACE_TEX, TEX_TO_WORDS, REPLACE_MACROS);
					//the BufferedStream containing macros is set when rawFileReaderList is set.
					macroReplacedThmsList = ProcessInput.get_macroReplacedThmList();
				}
			} catch (IOException e) {
				e.printStackTrace();
				throw new RuntimeException("Processing input via ProcessInput failed!\n", e);
			}*/
			//thmListBuilder.addAll(extractedThmsList);
			//thmList = thmListBuilder.build();			
			//webDisplayThmList = ImmutableList.copyOf(webDisplayThmsList);
			//webDisplayThmList = allThmsWithHypList;
			/*bareThmList = ImmutableList.copyOf(bareThmsList);
			processedThmList = ImmutableList.copyOf(processedThmsList);
			macroReplacedThmList = ImmutableList.copyOf(macroReplacedThmsList);*/
		}

		public static void set_gather_skip_gram_words_toTrue(){
			gather_skip_gram_words = true;
		}
		
		private static ImmutableList<ThmHypPair> createdThmHypPairListFromLists(List<String> allThmsNoHypList_,
				List<String> allHypList_, List<String> allThmSrcFileList_) {
			
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
			
			//List<ParsedExpression> parsedExpressionsList;
			String parsedExpressionSerialFileStr = "src/thmp/data/parsedExpressionListTemplate.dat";
			
			if(null != servletContext){
				if(!Searcher.SearchMetaData.gatheringDataBool()){
					parsedExpressionSerialFileStr = ThmSearch.getSystemCombinedParsedExpressionListFilePathBase();
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
						parsedExpressionSerialFileStr = ThmSearch.getSystemCombinedParsedExpressionListFilePathBase();
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
		 * This is used to load parsed expressions cache.
		 * @param parsedExpressionsList
		 * @param allThmsWithHypList
		 * @param contextVecList
		 * @param relationVecList
		 */
		private static void fillListsFromParsedExpressions(List<ParsedExpression> parsedExpressionsList, 
				List<String> allThmsWithHypList, //List<String> contextVecList, List<BigInteger> relationVecList,
				List<String> allThmsNoHypPreList, List<String> allHypPreList, List<String> allThmSrcFilePreList
				){
			//System.out.println("Should be list: " + parsedExpressionsList);
			for(ParsedExpression parsedExpr : parsedExpressionsList){
				DefinitionListWithThm defListWithThm = parsedExpr.getDefListWithThm();
				
				/* Build the definition string here, could be earlier in DetectHypothesis.java,
				 * but in future may want to do different things with the list elements, so better to keep list form.*/
				/*StringBuilder defListSB = new StringBuilder(200);
				for(VariableDefinition def : defListWithThm.getDefinitionList()){
					defListSB.append(def.getOriginalDefinitionSentence()).append('\n');
				}*/
				String defStr = defListWithThm.getDefinitionStr();
				//temporarry since defStr hasn't been serialized yet.
				defStr = (defStr == null) ? "" : defStr;
				allHypPreList.add(defStr);
				//get original thm and list of definitions separately, for displaying them separately on the web.
				String thmStr = defListWithThm.getThmStr();
				allThmsNoHypPreList.add(thmStr);
				String thmWithDefStr = defStr + " " + thmStr;
				allThmsWithHypList.add(thmWithDefStr);
				
				String fileName = defListWithThm.getSrcFileName();
				if(null != fileName){
					allThmSrcFilePreList.add(fileName);
				}else{
					allThmSrcFilePreList.add("");
				}				
				//allThmSrcFilePreList.add("");
				//contextVecList.add(parsedExpr.contextVecStr());
				//relationVecList.add(parsedExpr.getRelationVec());
			}
		}		
		
		/**
		 * List of relation vectors for all thms, as extracted from deserialized 
		 * ParsedExpressions.
		 * @deprecated
		 * @return
		 */
		/*public static ImmutableList<BigInteger> allThmsRelationVecList(){
			return allThmsRelationVecList;
		}*/

		/**
		 * List of context vectors for all thms, as extracted from deserialized 
		 * ParsedExpressions.
		 * @deprecated
		 * @return
		 */
		/*public static ImmutableList<String> allThmsContextVecList(){
			return allThmsContextVecList;
		}*/

		/**
		 * Get list of theorems with their hypotheses and assumptions attached,
		 * as collected by DetectHypothesis.java. As extracted from deserialized 
		 * ParsedExpressions.
		 * @return an immutable list
		 */
		/*public static ImmutableList<String> allThmsWithHypList(){
			return allThmsWithHypList;
		}*/
		
		/*public static int numThms(){
			return numThms;
		}*/
		/**
		 * Get list of hypotheses and assumptions,
		 * as collected by DetectHypothesis.java. As extracted from deserialized 
		 * ParsedExpressions.
		 * @return an immutable list
		 */
		/*public static ImmutableList<String> allThmsNoHypList(){
			return allThmsNoHypList;
		}*/
		
		/**
		 * Get list of hypotheses and assumptions,
		 * as collected by DetectHypothesis.java. As extracted from deserialized 
		 * ParsedExpressions.
		 * @return an immutable list
		 */
		/*public static ImmutableList<String> allHypList(){
			return allHypList;
		}*/
		
		/**
		 * Get source file names.
		 * @return an immutable list
		 */
		/*public static ImmutableList<String> allThmSrcFileList(){
			return allThmSrcFileList;
		}*/
		
		/**
		 * Get source file names.
		 * @return an immutable list
		 */
		/*public static ImmutableList<ThmHypPair> allThmHypPairList(){
			return allThmHypPairList;
		}*/
		
		/**
		 * Get thmList. Macros are expanded to their full forms by default.
		 * @deprecated
		 * @return
		 */
		/*public static ImmutableList<String> get_thmList(){
			return thmList;
		}*/
		
		/**
		 * Get thmList. List of theorems for web display, without \label{} or \index{} etc.
		 * @return
		 */
		/*public static ImmutableList<String> get_webDisplayThmList(){
			return webDisplayThmList;
		}*/
		
		/**
		 * List of theorems for web parsing, without \label{} or \index{}, or label content etc.
		 * @return
		 */
		/*public static ImmutableList<String> get_bareThmList(){
			//System.out.println("bare thms " + bareThmList);
			return bareThmList;
		}
		
		public static ImmutableList<String> get_processedThmList(){
			return processedThmList;
		}*/
		
		/**
		 * Get original list expanding macros to their full forms.
		 * @return
		 */
		/*public static ImmutableList<String> get_macroReplacedThmList(){
			return macroReplacedThmList;
		}*/		
	}
	
	/**
	 * Math words that should be included, but have been 
	 * marked as fluff due to their common occurance in English.
	 * Eg "ring".
	 * @return
	 */
	public static String[] scoreAvgMathWords(){
		return SCORE_AVG_MATH_WORDS;
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
		//int a = ThmWordsMaps.CONTEXT_VEC_SIZE;
	}
}
