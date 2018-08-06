package thmp.parse;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;

import thmp.parse.ParseState.ParseStateBuilder;
import thmp.parse.ParseState.VariableDefinition;
import thmp.parse.ParseState.VariableName;
import thmp.runner.GenerateSearchDataRunner.SearchDataRunnerConfig;
import thmp.search.CollectThm;
import thmp.search.CollectThm.ThmWordsMaps.IndexPartPair;
import thmp.search.SearchCombined.ThmHypPair;
import thmp.search.Searcher;
import thmp.search.Searcher.SearchMetaData;
import thmp.search.TheoremGet.ContextRelationVecPair;
import thmp.search.ThmSearch;
import thmp.utils.FileUtils;
import thmp.utils.MacrosTrie;
import thmp.utils.TexToTree;
import thmp.utils.WordForms;
import thmp.utils.WordForms.ThmPart;
import thmp.utils.MacrosTrie.MacrosTrieBuilder;

/**
 * Used to detect hypotheses in a sentence.
 * 
 * Serializes ALL_THM_WORDS_LIST to file, to be used as seed words for next time
 * search is initialized.
 * 
 * The class DetectHypothesis should always be run from the nested class Runner,
 * as it sets the relevant settings.
 * 
 * To run this locally on developer machine, (without waiting to load all deserialized
 * files), can specify file to run on inside readAndProcessInputData().
 * 
 * @author yihed
 *
 */
public class DetectHypothesis {
	
	private static final Logger logger = LogManager.getLogger(DetectHypothesis.class);
	//pattern matching is faster than calling str.contains() repeatedly 
	//which is O(mn) time.
	private static final Pattern HYP_PATTERN = WordForms.get_HYP_PATTERN();
	//positive look behind to split on any punctuation before a space.
	//private static final Pattern PUNCTUATION_PATTERN = Pattern.compile("(?<=[\\.|;|,|!|:]) ");
	
	//Positive look behind, split on empty space preceded by bracket/paren/brace, preceded by non-empty-space
	//private static final Pattern SYMBOL_SEPARATOR_PATTERN = Pattern.compile("-|'|\\+|\\s+|(?:(?:)?<=(?:[^\\s](?:[\\(\\[\\{])))|\\)|\\]\\}");
	
	private static final Pattern BRACKET_SEPARATOR_PATTERN = Pattern.compile("([^\\(\\[\\{]+)[\\(\\[\\{].*");
	
	//used for thm scraping.
	private static final Pattern THM_SCRAPE_PATTERN = Pattern.compile("(?i)(?:theorem|lemma|conjecture)");
	//revise this!!
	private static final Pattern THM_SCRAPE_PUNCTUATION_PATTERN = Pattern.compile("(?:(?=[,!:;.\\s])|(?<=[,!:;.\\s]))");
	//punctuation pattern to eliminate
	//almost all special patterns but no -, ', ^, *, which can occur in thm names
	private static final Pattern THM_SCRAPE_ELIM_PUNCTUATION_PATTERN = Pattern
			.compile("[\\{\\[\\)\\(\\}\\]$\\%/|#@.;,:_~!&\"`+<>=#]");
	
	//contains ParsedExpressions, to be serialized to persistent storage
	//***private static final List<ParsedExpression> parsedExpressionList = new ArrayList<ParsedExpression>();
	//for inspection
	private static final List<String> parsedExpressionStrList = new ArrayList<String>();
	private static final List<ContextRelationVecPair> contextRelationVecPairList = new ArrayList<ContextRelationVecPair>();
	private static final List<String> DefinitionListWithThmStrList = new ArrayList<String>();
	private static final List<String> allThmsStrWithSpaceList = new ArrayList<String>();
	
	private static final String parsedExpressionSerialFileStr = "src/thmp/data/parsedExpressionList";
	private static final String contextRelationPairSerialFileStr = "src/thmp/data/vecs/contextRelationVecPairList0";
	private static final String parsedExpressionStringFileStr = "src/thmp/data/parsedExpressionList.txt";
	
	public static final String parsedExpressionSerialFileNameStr = "parsedExpressionList";
	private static final String parsedExpressionStringFileNameStr = "parsedExpressionList.txt";
	
	private static final String contextRelationPairSerialFileNameStr = ThmSearch.TermDocumentMatrix.CONTEXT_VEC_PAIR_LIST_FILE_NAME;
	public static final String allThmNameScrapeSerStr = "src/thmp/data/allThmNameScrape.dat";
	public static final String allThmNameScrapeTxtStr = "src/thmp/data/allThmNameScrape.txt";
	public static final String thmNameScrapeNameRoot = "thmNameScrape";
	
	private static final String allThmsStringFileStr = "src/thmp/data/allThmsList.txt";
	private static final String allThmsStringFileNameStr = "allThmsList.txt";
	
	//files to serialize theorem words to.
	public static final String allThmWordsMapSerialFileStr = "src/thmp/data/allThmWordsMap.dat";
	private static final String allThmWordsMapStringFileStr = "src/thmp/data/allThmWordsMap.txt";
	public static final String allThmWordsMapSerialFileNameStr = "allThmWordsMap.dat";
	private static final String allThmWordsMapStringFileNameStr = "allThmWordsMap.txt";
	
	//not used by next runs, but nice to have the list for inspection.
	//private static final String allThmWordsSerialFileStr = "src/thmp/data/allThmWordsList.dat";
	//private static final String allThmWordsStringFileStr = "src/thmp/data/allThmWordsList.txt";
	
	//commented out March 2018. private static final String statsFileStr = "src/thmp/data/parseStats.txt";
	
	/**used for scraping names**/	
	private static final String THM_SCRAPE_SER_FILENAME = "thmNameScrape.dat";
	private static final String THM_SCRAPE_TXT_FILENAME = "thmNameScrape.txt";
	//stop words that come after the stirng "theorem", to stop scraping before, the word immediately before.
	private static final Set<String> SCRAPE_STOP_WORDS_BEFORE_SET = new HashSet<String>();
	private static final Pattern THM_SCRAPE_ELIM_PATTERN = Pattern.compile("(?:from|proof|prove[sd]*|next)");
	private static final Pattern WHITE_SPACE_PATTERN = Pattern.compile("\\s*");
	//replace $$ with $, so context vecs can close any opened begin math delimiters
	static final Pattern doubleDollarPatt = Pattern.compile("\\$\\$");
	static final String defaultThmType = "Theorem";
	//max number of variables to retrieve context strings for.
	private static final int variableDefinitionListThresh = 3;
	//max length for definition sentence.
	private static final int defSentenceLenThresh = 270;
	
	//serialize the words as well, to bootstrap up after iterations of processing. The math words are going to 
	//stabilize.
	//This is ordered based on word frequencies.
	//private static final List<String> ALL_THM_WORDS_LIST;
	private static final Map<String, Integer> ALL_THM_WORDS_FREQ_MAP;
	
	private static final boolean PARSE_INPUT_VERBOSE = false;
	//whether to gather a list of statistics, such as percentage of thms with full parses, or non-null head ParseStruct's.
	//private static final boolean GATHER_STATS = true;
	
	//pattern for lines to skip any kind of parsing, even hypothesis-detection.
	//skip examples and bibliographies  
	//Pattern.compile("\\\\begin\\{proof\\}.*|\\\\begin\\{exam.*|\\\\begin\\{thebib.*");
	private static final Pattern SKIP_PATTERN = WordForms.getSKIP_PATTERN();	
	////Pattern.compile("\\\\end\\{proof\\}.*|\\\\end\\{exam.*|\\\\end\\{thebib.*")
	private static final Pattern END_SKIP_PATTERN = WordForms.getEND_SKIP_PATTERN();			
	
	//single lines to skip. Such as comments
	//Pattern.compile("^%.*|\\\\begin\\{bib.*")
	private static final Pattern SINGLE_LINE_SKIP_PATTERN = WordForms.getSINGLE_LINE_SKIP_PATTERN();
	
	private static final Pattern END_DOCUMENT_PATTERN = Pattern.compile("\\\\end\\{document\\}.*");
	private static final Pattern NEW_DOCUMENT_PATTERN = Pattern.compile(".*\\\\documentclass.*");
	private static final int NUM_NON_TEX_TOKEN_THRESHOLD = 4;
	private static final int THM_MAX_CHAR_SIZE = 1800;
	//this path is only used in this class, for inspection, so not in SearchMetadata
	private static final String parserErrorLogPath = "src/thmp/data/parserErrorLog.txt";
	private static final boolean DEBUG = FileUtils.isOSX() ? InitParseWithResources.isDEBUG() : false;
	private static final int CONTEXT_SB_LENGTH_THRESHOLD = 10000;
	private static final Pattern ENUMERATE_PATTERN = Pattern.compile("(?:\\\\(?:begin|end)\\{enumerate\\}"
			+ "|\\\\(?:begin|end)\\{itemize\\})");

	static{
		FileUtils.set_dataGenerationMode();	
		ALL_THM_WORDS_FREQ_MAP = CollectThm.ThmWordsMaps.get_docWordsFreqMap();
		
		//Stop words used when scraping theorem names.
		String[] beforeStopWordsAR = new String[]{"by", "of","to","above","in", "By", "with", "is", "from",
				"following", "then", "thus", "this"};
		for(String w : beforeStopWordsAR) {
			SCRAPE_STOP_WORDS_BEFORE_SET.add(w);
		}
	}
	
	public static class Runner{
		
		public static void main(String[] args){
			SearchDataRunnerConfig runnerConfig = SearchDataRunnerConfig.DEFAULT_CONFIG;
			generateSearchData(args, runnerConfig);
		}
		
		/**
		 * 
		 * @param args
		 * @param runnerConfig configuration for generating data
		 */
		public static void generateSearchData(String[] args, SearchDataRunnerConfig runnerConfig){
			InputParams inputParams = new InputParams(args);
			//need separate runner class to set
			if(inputParams.usePreviousDocWordsFreqMaps){
				Searcher.SearchMetaData.set_previousWordDocFreqMapsPath(inputParams.getPathToDocWordsFreqMap());
			}
			/*This line means we must be careful when evoking this class, which will 
			 * set_gatheringDataBoolToTrue() */
			Searcher.SearchMetaData.set_gatheringDataBoolToTrue();
			DetectHypothesis.readAndProcessInputData(inputParams, runnerConfig);
		}
	}
	
	/**
	 * Statistics class to record statistics such as the percentage of thms 
	 * that have spanning parses and/or non-null headParseStruct's.
	 * Should use -D etc to indicate what type of parameters.
	 */
	public static class Stats{
		//number of thms for which headParseStruct == null
		private int headParseStructNullNum = 0;
		//total number of theorems
		private int totalThmsNum = 0;
		
		public Stats(){
			this.headParseStructNullNum = 0;
			this.totalThmsNum = 0;
		}
		
		public Stats(int numHeadParseStructNull, int numTotalThms){
			this.headParseStructNullNum = numHeadParseStructNull;
			this.totalThmsNum = numTotalThms;
		}
		
		/**
		 * @param headParseStructNullNum the headParseStructNullNum to set
		 */
		public void incrementHeadParseStructNullNum() {
			this.headParseStructNullNum++;
		}

		/**
		 * @param totalThmsNum the totalThmsNum to set
		 */
		public void incrementTotalThmsNum() {
			this.totalThmsNum++;
		}

		/**
		 * @return the headParseStructNullNum
		 */
		public int getHeadParseStructNullNum() {
			return headParseStructNullNum;
		}

		/**
		 * @return the totalThmsNum
		 */
		public int getTotalThmsNum() {
			return totalThmsNum;
		}
		
		/**
		 * @return the totalThmsNum
		 */
		public double getNonNullPercentage() {
			return ((double)headParseStructNullNum)/totalThmsNum;
		}
		
		@Override
		public String toString(){
			StringBuilder sb = new StringBuilder("totalThmsNum: ").append(totalThmsNum);
			sb.append(" Percetage nontrivial parseStructHeads: ").append(getNonNullPercentage());
			return sb.toString();
		}
	}
	
	/**
	 * Combination of theorem String and the list of
	 * assumptions needed to define the variables in theorem.
	 */
	public static class DefinitionListWithThm implements Serializable, TheoremContainer {
		
		private static final long serialVersionUID = 7178202892278343033L;
		//singleton placeholder instance
		static final DefinitionListWithThm PLACEHOLDER_DEF_LIST_WITH_THM 
			= new DefinitionListWithThm("", Collections.<VariableDefinition>emptyList(), "", "");
		
		private String thmStr;
		
		private String definitionStr;
		//name of source file from which thm is extracted.
		private String srcFileName;
		
		private transient List<VariableDefinition> definitionList = new ArrayList<VariableDefinition>();
		
		public DefinitionListWithThm(String thmStr, List<VariableDefinition> definitionList,
				String definitionStr_, String srcFileName_){
			this.thmStr = thmStr;
			this.definitionList = definitionList;
			this.definitionStr = definitionStr_;
			this.srcFileName = srcFileName_;
		}
		
		@Override
		public boolean equals(Object other){
			if(!(other instanceof DefinitionListWithThm)){
				return false;
			}
			DefinitionListWithThm otherDefThm = (DefinitionListWithThm)other;
			if(!this.definitionStr.equals(otherDefThm.definitionStr)){
				return false;
			}
			if(!this.srcFileName.equals(otherDefThm.srcFileName)){
				return false;
			}
			return true;
		}
		
		@Override
		public String toString(){
			//initial capacity should be average number of characters.
			StringBuilder sb = new StringBuilder(250);
			if(null != this.definitionList){
				sb.append("- definitionList: ").append(definitionList);
			}
			if(null != thmStr){
				sb.append("thmStr: -").append(thmStr);
			}
			return sb.toString();
		}
		
		public String getDefinitionStr(){
			return this.definitionStr;
		}
		/**
		 * Returns String of thm along with definitions.
		 * Note that this field is transient, so will
		 * return null from serialized data!
		 */
		@Override
		public String getEntireThmStr() {
			return definitionStr + " " + thmStr;
		}
		
		/**
		 * @return the theorem String.
		 */
		public String getThmStr() {
			return thmStr;
		}
		
		/**
		 * @return name of source file from which thm is extracted.
		 */
		public String getSrcFileName() {
			return srcFileName;
		}
		
		/**
		 * @return the definitionList
		 */
		public List<VariableDefinition> getDefinitionList() {
			return definitionList;
		}		
	}
	
	/**
	 * Whether the inputStr is a hypothesis. By checking whether the input 
	 * contains any assumption-indicating words.
	 * @param inputStr
	 * @return
	 */
	public static boolean isHypothesis(String inputStr){
		if(HYP_PATTERN.matcher(inputStr.toLowerCase()).find()){
			return true;
		}
		return false;
	}
	
	private static class InputParams{
		
		String texFilesDirPath;
		//file containing names of tex files
		String serializedTexFileNamesFileStr;
		//e.g. "0208_001/0208/ProjectedTDMatrix.mx"
		boolean usePreviousDocWordsFreqMaps;
		String pathToProjectionMx;
		//map of word frequencies.
		String pathToWordFreqMap;
		String parsedExpressionSerialFileStr = DetectHypothesis.parsedExpressionSerialFileStr;
		String wordThmIndexMMapSerialFileStr = Searcher.SearchMetaData.wordThmIndexMMapSerialFilePath();
		String contextRelationPairSerialFileStr = DetectHypothesis.contextRelationPairSerialFileStr;
		String allThmWordsMapSerialFileStr = DetectHypothesis.allThmWordsMapSerialFileStr;
		String allThmWordsMapStringFileStr = DetectHypothesis.allThmWordsMapStringFileStr;
		String parsedExpressionStringFileStr = DetectHypothesis.parsedExpressionStringFileStr; //parsedExpressionStrList
		String allThmsStringFileStr = DetectHypothesis.allThmsStringFileStr; //allThmsStrWithSpaceList
		
		static final String texFilesSerializedListFileName = Searcher.SearchMetaData.texFilesSerializedListFileName;
		
		//where to put the full dim TD matrix
		String fullTermDocumentMxPath;
		/**
		 * Usual use needs *3* parameters in args. March 2017.
		 * texFilesDirPath, pathToProjectionMx, pathToWordFreqMap.
		 * @param args
		 */
		InputParams(String args[]){
			int argsLen = args.length;
			if(argsLen > 0){
				texFilesDirPath = thmp.utils.FileUtils.addIfAbsentTrailingSlashToPath(args[0]);
				
				serializedTexFileNamesFileStr = texFilesDirPath +texFilesSerializedListFileName;
				
				if(argsLen > 2){
					pathToProjectionMx = args[1];
					pathToWordFreqMap = args[2];
					usePreviousDocWordsFreqMaps = true;
					char fileSeparatorChar = File.separatorChar;
					int texFilesDirPathLen = texFilesDirPath.length();
					if(texFilesDirPath.charAt(texFilesDirPathLen-1) != fileSeparatorChar){
						texFilesDirPath = texFilesDirPath + fileSeparatorChar;
					}
					
					this.parsedExpressionSerialFileStr = texFilesDirPath + DetectHypothesis.parsedExpressionSerialFileNameStr;
					this.contextRelationPairSerialFileStr = texFilesDirPath + "vecs/" + DetectHypothesis.contextRelationPairSerialFileNameStr;
					this.wordThmIndexMMapSerialFileStr = texFilesDirPath + SearchMetaData.wordThmIndexMMapSerialFileName() ;
					this.allThmWordsMapSerialFileStr = texFilesDirPath + DetectHypothesis.allThmWordsMapSerialFileNameStr;
					this.allThmWordsMapStringFileStr = texFilesDirPath + DetectHypothesis.allThmWordsMapStringFileNameStr;
					this.parsedExpressionStringFileStr = texFilesDirPath + DetectHypothesis.parsedExpressionStringFileNameStr; //parsedExpressionStrList
					this.allThmsStringFileStr = texFilesDirPath + DetectHypothesis.allThmsStringFileNameStr;
					
					//create fullTermDocumentMxPath using base path
					this.fullTermDocumentMxPath = texFilesDirPath + ThmSearch.TermDocumentMatrix.FULL_TERM_DOCUMENT_MX_NAME + ".mx";					
				}			
			}
		}
		String getPathToProjectionMx(){
			return pathToProjectionMx;
		}
		
		String getPathToDocWordsFreqMap(){
			return pathToWordFreqMap;
		}
	}
	
	/**
	 * only parse if sentence is hypothesis, when parsing outside theorems.
		to build up variableNamesMMap. Also collect the sentence that 
		defines a variable, to include inside the theorem for search.
	 * Normal use is when args is 2-String argument, first is path to dir containing
	 * tex files, second is list of tex files in that dir.
	 * 1st, path to directory containing .tex files. 
	 * 2nd, texFileNamesSerialFileStr, path to file names that contain names of the .tex files 
	 * that should be parsed.
	 * 3rd, path to projection mx.
	 * 4th, path to allThmWordsMap
	 * Should use configuration file!
	 * @param args
	 */
	private static void readAndProcessInputData(//String[] args
			InputParams inputParams, SearchDataRunnerConfig runnerConfig) {
		/* read in a directory name, parse all the files individually. */ 		
		//try to read file path from command line argument first
		//path should be absolute.
		
		/*could be file or dir*/
		File inputFile = null;
		
		/*absolute path to files and the tar file name they live in*/
		Map<String, String> texFileNamesMap = null;
		String texFileNamesSerialFileStr = inputParams.serializedTexFileNamesFileStr;
		if(texFileNamesSerialFileStr != null){
			String texFilesDirPath = inputParams.texFilesDirPath;
			inputFile = new File(texFilesDirPath);
			//set of *absolute* path names.
			texFileNamesMap = deserializeTexFileNames(texFileNamesSerialFileStr);
			if(null == texFileNamesMap){
				return;
			}
		}
		//resort to default file if no arg supplied. Useful to for testing locally.
		else{
				/*put file here if testing locally on developer machine*/
				inputFile = new File("src/thmp/data/Total.txt");
				inputFile = new File("/Users/yihed/Downloads/math0011136");
				inputFile = new File("src/thmp/data/math0210227");
				
				inputFile = new File("/Users/yihed/Downloads/0704.2030");
				inputFile = new File("src/thmp/data/0704.2030");
				inputFile = new File("/Users/yihed/Downloads/test/1605.01240");
				inputFile = new File("src/thmp/data/test1.txt");
				inputFile = new File("/Users/yihed/Downloads/testJavaNov.tex");
				inputFile = new File("/Users/yihed/Downloads/testJava2.tex");
				inputFile = new File("/Users/yihed/Downloads/0709.2001.tex");
				inputFile = new File("/Users/yihed/Downloads/1406.6713.tex");
				inputFile = new File("/Users/yihed/Downloads/test/testThm.txt");
				inputFile = new File("/Users/yihed/Downloads/stuchMarch25.tex");
				inputFile = new File("/Users/yihed/Downloads/1703.08650");
				inputFile = new File("/Users/yihed/Downloads/test/capitalLem.tex");
				inputFile = new File("/Users/yihed/Downloads/teoTest2.txt");
				inputFile = new File("/Users/yihed/Downloads/test/thmp/preambleTest1.txt");
				inputFile = new File("/Users/yihed/Downloads/test/thmp/preambleTest1ca.txt");
				inputFile = new File("/Users/yihed/Downloads/test/thmp/alignTest.txt");
				inputFile = new File("/Users/yihed/Downloads/test/thmp/may21_0.txt");
				inputFile = new File("/Users/yihed/Downloads/test/thmp/may22_1.txt");
				inputFile = new File("/Users/yihed/Downloads/test/thmp/may23_0.txt");
				inputFile = new File("/Users/yihed/Downloads/test/thmp/july3_0.txt");
				inputFile = new File("/Users/yihed/Downloads/test/thmp/july9_0.txt");
				inputFile = new File("/Users/yihed/Downloads/test/thmp/july3_1.txt");
				inputFile = new File("/Users/yihed/Downloads/test/thmp/july10_0.txt");
				inputFile = new File("/Users/yihed/Downloads/test/thmp/july13_0.txt");				
				inputFile = new File("/Users/yihed/Downloads/test/thmp/july17_0.txt");
				inputFile = new File("/Users/yihed/Downloads/test/thmp/july18_0.txt");
				inputFile = new File("/Users/yihed/Downloads/test/thmp/aug6_0.txt");
				
		}		
		List<DefinitionListWithThm> defThmList = new ArrayList<DefinitionListWithThm>();
		List<ThmHypPair> thmHypPairList = new ArrayList<ThmHypPair>();
		Stats stats = new Stats();
		if(inputFile.isDirectory()){
			//get all filenames from dir. Get tex file names from serialized file data.
					
				final boolean scrapeThmNames = FileUtils.SCRAPE_THM_NAME_Q;
				List<String> thmNameList = new ArrayList<String>();
				
				for(Map.Entry<String, String> fileNameEntry : texFileNamesMap.entrySet()){
					//this is absolute file path, e.g. /home/usr0/yihed/thm/
					//0309_001Untarred/0309/math0309323/cwahl-ch3.tex
					String fileName = fileNameEntry.getKey();
					File file = new File(fileName);
					BufferedReader inputBF = null;
					try{
						inputBF = new BufferedReader(new FileReader(file));	
					}catch(FileNotFoundException e){						
						String msg = fileName + " source file not found!";
						System.out.println(msg);
						logger.error(msg);
						continue;
					}
					//file name needed as metadata for search. e.g. math0309323
					String texFileName = fileNameEntry.getValue();
					try{						
						if(scrapeThmNames) {
							scrapeThmNames(inputBF, thmNameList);
						} else {
							List<String> curFileThmNameList = new ArrayList<String>();
							extractThmsFromFiles(inputBF, defThmList, thmHypPairList, stats, texFileName, curFileThmNameList);
							//add delimiter to separate thm names per paper
							if(!curFileThmNameList.isEmpty()) {
								thmNameList.add("\n");
								thmNameList.add(texFileName);
								thmNameList.addAll(curFileThmNameList);
							}							
						}
					}catch(OutOfMemoryError e){
						String timeStr = new SimpleDateFormat("yyyy_MM_dd_HH:mm").format(Calendar.getInstance().getTime());
						String msg = "\n"+timeStr + " Exception when processing: " + fileName + e+"\nwith trace " + Arrays.toString(e.getStackTrace());						
						logger.error(msg);
						System.out.println(msg);
						throw e;
					}catch(Throwable e){
						String timeStr = new SimpleDateFormat("yyyy_MM_dd_HH:mm").format(Calendar.getInstance().getTime());
						String msg = "\n"+timeStr + " Exception when processing: " + fileName + e+"\nwith trace " + Arrays.toString(e.getStackTrace());						
						FileUtils.appendObjToFile(msg, parserErrorLogPath);
						logger.error(msg);
						System.out.println(msg);
					}finally {
						FileUtils.silentClose(inputBF);						
					}
				}
				
				if(!thmNameList.isEmpty()) {
					FileUtils.serializeObjToFile(thmNameList, inputParams.texFilesDirPath + THM_SCRAPE_SER_FILENAME);					
					FileUtils.writeToFile(thmNameList, inputParams.texFilesDirPath + THM_SCRAPE_TXT_FILENAME);
				}
				//serialize, so don't discard the items already parsed.
				//serialization only applicable when running on byblis
				if(!FileUtils.isOSX()){
					serializeDataToFile(stats, thmHypPairList, inputParams, runnerConfig);		
				}					
				
		}else{
			BufferedReader inputBF = null;
			try{
				inputBF = new BufferedReader(new FileReader(inputFile));
			}catch(FileNotFoundException e){
				logger.error(e.getStackTrace());
				throw new IllegalStateException(e);
			}
			
			try{
				extractThmsFromFiles(inputBF, defThmList, thmHypPairList, stats, inputFile.getName());				
			}catch(Throwable e){
				logger.error("Error during thm exptraction and parsing!"+e.getMessage());			
				throw e;
			}finally {
				FileUtils.silentClose(inputBF);
			}
			
			if(!FileUtils.isOSX()){
				serializeDataToFile(stats, thmHypPairList, inputParams, runnerConfig);	
			}
			
			//March 26: temporary to debug kahler
			//HashMultimap<String, IndexPartPair> wordThmIndexMMap = HashMultimap.create();
			//createWordThmIndexMMap(thmHypPairList, wordThmIndexMMap);
			//System.out.println("wordThmIndexMMap: "+wordThmIndexMMap);
		}
		System.out.println("STATS -- percentage of non-trivial ParseStruct heads: " + stats.getNonNullPercentage() 
			+ " out of total " + stats.getTotalThmsNum() + "thms");
		//should actually make these local vars, so no need to clear at end
		parsedExpressionStrList.clear() ;
		contextRelationVecPairList.clear();
		DefinitionListWithThmStrList.clear();
		allThmsStrWithSpaceList.clear();
		
		boolean deserialize = false;
		if(deserialize){
			deserializeParsedExpressionsList();
		}		
		FileUtils.cleanupJVMSession();
	}

	/**
	 * Add thm names scraped from inputBF to thmNameList.
	 * *Only* used for theorem name scraping.
	 * @param inputBF
	 * @param thmNameMSet
	 */
	private static void scrapeThmNames(BufferedReader inputBF, List<String> thmNameList) {
		
		String line;
		try {
			//Matcher m;
			while((line = inputBF.readLine()) != null) {
				//analyze line, get thms
				scrapeThmNames(line, thmNameList);				
			}
		} catch (IOException e) {			
			throw new IllegalStateException("IOException while scraping thm names", e);
		}
	}
	
	/**
	 * Add thm names scraped from line to thmNameMSet.
	 * *Only* used for theorem name scraping.
	 * @param line line to process from.
	 * @param thmNameMSet
	 */
	private static void scrapeThmNames(String line, List<String> thmNameList) {
		
		List<String> lineList = Arrays.asList(THM_SCRAPE_PUNCTUATION_PATTERN.split(line));
		int lineListSz = lineList.size();
		for(int i = 0; i < lineListSz; i++) {
			String word = lineList.get(i);
			if(THM_SCRAPE_PATTERN.matcher(word).matches()) {
				String thmWords = collectThmWordsBeforeAfter(lineList, i);
				if(!WHITE_SPACE_PATTERN.matcher(thmWords).matches()) {
					thmNameList.add(thmWords);							
				}
			}
		}			
	}
	
	/**
	 * Collect certain number of words before and after thm
	 * @param thmList
	 * @param index index to look before or after
	 * @return
	 */
	private static String collectThmWordsBeforeAfter(List<String> thmList, int index) {
		int indexBoundToCollect = 6;
		StringBuilder sb = new StringBuilder(40);
		int thmListSz = thmList.size();
		int i = 1;
		int count = 0;
		boolean gathered = false;
		while(count < indexBoundToCollect && index - i > -1) {
			String curWord = thmList.get(index-i);
			if(WordForms.getWhiteEmptySpacePattern().matcher(curWord).matches()) {
				i++;
				continue;
			}
			if(THM_SCRAPE_ELIM_PUNCTUATION_PATTERN.matcher(curWord).find()) {
				break;
			}
			if(THM_SCRAPE_ELIM_PATTERN.matcher(curWord).matches()) {
				return "";
			}
			if(count==0 && SCRAPE_STOP_WORDS_BEFORE_SET.contains(curWord)) {
				break;
			}
			sb.insert(0, curWord + " ");
			i++;
			count++;
			gathered = true;
		}
		sb.append(thmList.get(index)).append(" ");
		indexBoundToCollect = 7;
		i = 1;
		count = 0;
		while(count < indexBoundToCollect && index + i < thmListSz) {
			String curWord = thmList.get(index+i);
			if(WordForms.getWhiteEmptySpacePattern().matcher(curWord).matches()) {
				i++;
				continue;
			}	
			if(THM_SCRAPE_ELIM_PUNCTUATION_PATTERN.matcher(curWord).find()) {
				break;
			}
			if(count==0 && WordForms.DIGIT_PATTERN.matcher(curWord).find()) {
				break;
			}
			if(THM_SCRAPE_ELIM_PATTERN.matcher(curWord).matches()) {
				return "";
			}
			sb.append(curWord).append(" ");
			i++;
			count++;
			gathered = true;
		}
		if(sb.length() > 1) {
			sb.deleteCharAt(sb.length()-1);
		}
		if(gathered) {
			return sb.toString();
		}else {
			return "";
		}		
	}
	
	@SuppressWarnings("unchecked")
	private static Map<String, String> deserializeTexFileNames(String texFileNamesSerialFileStr) {
		return ((List<Map<String, String>>)FileUtils.deserializeListFromFile(texFileNamesSerialFileStr)).get(0);
	}

	private static void extractThmsFromFiles(BufferedReader inputBF, List<DefinitionListWithThm> defThmList, 
			List<ThmHypPair> thmHypPairList, Stats stats, String fileName, List<String> scrapedThmNameList) {

		ParseStateBuilder parseStateBuilder = new ParseStateBuilder();		
		ParseState parseState = parseStateBuilder.build();		
		try{
			readAndParseThm(inputBF, parseState, defThmList, thmHypPairList, stats, fileName, scrapedThmNameList);		
		}catch(IOException e){
			e.printStackTrace();
			logger.error(e.getStackTrace());
		}		
		DefinitionListWithThmStrList.add(defThmList.toString()+ "\n");
	}
	
	/**
	 * Entry point to extract thms given a file.
	 * @param inputBF
	 * @param defThmList empty list.
	 * @param thmHypPairList Emoty list.
	 * @param stats
	 * @param fileName name of file, to append to parsed thms.
	 * @param scraped thm name list.
	 */
	private static void extractThmsFromFiles(BufferedReader inputBF, List<DefinitionListWithThm> defThmList, 
			List<ThmHypPair> thmHypPairList, Stats stats, String fileName) {

		List<String> scrapedThmNameList = null;
		extractThmsFromFiles(inputBF, defThmList, thmHypPairList, stats, fileName, scrapedThmNameList);
	}
	
	/**
	 * Serialize collected data to persistent storage, such as lists of ThmHypPair's.
	 * The serializations done in this method should remain atomic, i.e. do *not* perform 
	 * a subset of steps only, since we rely on the different serialized data to come from
	 * the same source with the same settings.
	 * @param pathToProjectionMx path to  projection mx, if specified.
	 */
	private static void serializeDataToFile(Stats stats, List<ThmHypPair> thmHypPairList,
			InputParams inputParams, SearchDataRunnerConfig runnerConfig) {
		
		String pathToProjectionMx = inputParams.getPathToProjectionMx();
		String pathToWordFreqMap = inputParams.pathToWordFreqMap;
		String fullTermDocumentMxPath = inputParams.fullTermDocumentMxPath;
		String parsedExpressionSerialFileStr = inputParams.parsedExpressionSerialFileStr;
		String contextRelationPairSerialFileStr = inputParams.contextRelationPairSerialFileStr;
		String allThmWordsMapSerialFileStr = inputParams.allThmWordsMapSerialFileStr;
		String wordThmIndexMMapSerialFileStr = inputParams.wordThmIndexMMapSerialFileStr;
		String allThmWordsMapStringFileStr = inputParams.allThmWordsMapStringFileStr;
		String parsedExpressionStringFileStr = inputParams.parsedExpressionStringFileStr;
		String allThmsStringFileStr = inputParams.allThmsStringFileStr;
		//texFilesDirPath already contains trailing file separator
		String curTexFilesDirPath = inputParams.texFilesDirPath;
		
		boolean projectionPathsNotNull = (null != pathToProjectionMx && null != pathToWordFreqMap);		
		logger.info("Serializing parsedExpressionList, etc, to file...");
		try{
			/*Multimap of words and the indices of thm's. To be used by intersection search
			 *The indices need to be processed again later when combined into one MMap for multiple tars.
			 * in projectMatrix.java*/
			HashMultimap<String, IndexPartPair> wordThmIndexMMap = HashMultimap.create();
			//actually turn ParsedExpression's into ThmHypPair's. To facilitate deserialization
			//into cache at runtime with minimal processing.
			createWordThmIndexMMap(thmHypPairList, wordThmIndexMMap);
			
			List<Multimap<String, IndexPartPair>> wordThmIndexMMapList = new ArrayList<Multimap<String, IndexPartPair>>();
			wordThmIndexMMapList.add(wordThmIndexMMap);
			
			FileUtils.serializeObjToFile(wordThmIndexMMapList, wordThmIndexMMapSerialFileStr);
			String wordThmIndexMMapTxtFileStr = wordThmIndexMMapSerialFileStr
					.substring(0, wordThmIndexMMapSerialFileStr.length()-3) + "txt";
			FileUtils.writeToFile(wordThmIndexMMapList, wordThmIndexMMapTxtFileStr);
			
			FileUtils.serializeObjToFile(thmHypPairList, parsedExpressionSerialFileStr);
			//write parsedExpressionList to file
			FileUtils.writeToFile(thmHypPairList, parsedExpressionStringFileStr);
			
			FileUtils.serializeObjToFile(contextRelationVecPairList, contextRelationPairSerialFileStr);
			
			List<Map<String, Integer>> wordMapToSerializeList = new ArrayList<Map<String, Integer>>();
			wordMapToSerializeList.add(ALL_THM_WORDS_FREQ_MAP);
			FileUtils.serializeObjToFile(wordMapToSerializeList, allThmWordsMapSerialFileStr);
			//this list is for human inspection.
			List<String> wordMapStringList = new ArrayList<String>();
			wordMapStringList.add(ALL_THM_WORDS_FREQ_MAP.toString());
			FileUtils.writeToFile(wordMapStringList, allThmWordsMapStringFileStr);
			
			//write just the thms
			FileUtils.writeToFile(allThmsStrWithSpaceList, allThmsStringFileStr);
			//append to stats file!
			//FileUtils.appendObjToFile(stats, statsFileStr); <--cmmented out Dec 2017
			
		}catch(Throwable e){
			logger.error("Error occurred when writing and serializing to file! " + e);
			throw e;
		}
		logger.info("Done serializing parsedExpressionList & co to files! Beginning to compute SVD for parsedExpressionList thms.");		
		
		createTimeStamp(curTexFilesDirPath);
		
		/* Creates the term document matrix, and serializes to .mx file.
		 * If this step fails, need to re-run to produce matrix. This should run at end of this method,
		 * so others have already serialized in case this fails.*/
		if(runnerConfig.regenerateMx()) {
			ImmutableList<TheoremContainer> immutableThmHypPairList = ImmutableList.copyOf(thmHypPairList);
			if(projectionPathsNotNull){
				Map<String, Integer> wordFreqMap = getWordFreqMap(pathToWordFreqMap);
				//first serialize full dimensional TD mx, then project using provided projection mx.
				ThmSearch.TermDocumentMatrix.serializeHighDimensionalTDMx(immutableThmHypPairList, fullTermDocumentMxPath, wordFreqMap);
				//replace the last bit of the path with the name of the projected(reduced) mx.
				String pathToReducedDimTDMx = replaceFullTDMxName(fullTermDocumentMxPath, ThmSearch.TermDocumentMatrix.PROJECTED_MX_NAME);
				ThmSearch.TermDocumentMatrix.projectTermDocumentMatrix(fullTermDocumentMxPath, pathToProjectionMx, 
						pathToReducedDimTDMx);
			}else{
				ThmSearch.TermDocumentMatrix.createTermDocumentMatrixSVD(immutableThmHypPairList);
			}
		}
	}

	/**
	 * Create timestamp in the current processing directory
	 * 
	 * @param curTexFilesDirPath Already includes slash
	 */
	private static void createTimeStamp(String curTexFilesDirPath) {
		//delete previous timestamp files first
		Runtime rt = Runtime.getRuntime();
		
		try {
			rt.exec("rm " + curTexFilesDirPath + "*timestamp");
		} catch (IOException e) {
			String msg = "IOException while trying to remove previous timestamp file: " + e;
			//print since running locally
			System.out.println(msg);
			logger.error(msg);
		}			
		//don't need to wait
		
		//e.g. Wed Jun 21 12:14:15 CDT 2017
		List<String> dateAr = WordForms.splitThmIntoSearchWordsList((new java.util.Date()).toString());
		int dateArLen = dateAr.size();
		//texFilesDirPath already contains trailing file separator
		StringBuilder dateSB = new StringBuilder(curTexFilesDirPath);
		for(int i = 1; i < 3 && i < dateArLen; i++){
			dateSB.append(dateAr.get(i));
		}
		dateSB.append("timestamp");
		FileUtils.writeToFile("", dateSB.toString());
	}
	
	/**
	 * Convert list of ParsedExpression's to list of ThmHypPair's for serialization.
	 * @param peList
	 * @param wordThmIndexMMap map used for intersection. Keys are words, values are IndexPartPair for word.
	 * @return
	 */
	private static void createWordThmIndexMMap(List<ThmHypPair> peList,
			HashMultimap<String, IndexPartPair> wordThmIndexMMap){		
		
		int thmIndex = 0;
		for(ThmHypPair pe : peList){
			char thmType = pe.thmType().charAt(0);
			
			IndexPartPair indexPartPair = new IndexPartPair(thmIndex, ThmPart.STM, thmType);
			String stm = pe.thmStr();
			pe.thmType();
			
			CollectThm.ThmWordsMaps.addToWordThmIndexMap(wordThmIndexMMap, stm, indexPartPair);
			
			indexPartPair = new IndexPartPair(thmIndex, ThmPart.HYP,  thmType);
			String hyp = pe.hypStr();
			CollectThm.ThmWordsMaps.addToWordThmIndexMap(wordThmIndexMMap, hyp, indexPartPair);
			thmIndex++;
		}		
	}
	
	private static String replaceFullTDMxName(String fullTermDocumentMxPath, String projectedMxName){
		char separatorChar = File.separatorChar;
		int i = fullTermDocumentMxPath.length() - 1;
		while(i > -1 && fullTermDocumentMxPath.charAt(i) != separatorChar){
			i--;
		}
		if(i == -1){
			return projectedMxName;
		}
		return fullTermDocumentMxPath.substring(0, i+1) + projectedMxName + ".mx";		
	}
	
	@SuppressWarnings("unchecked")
	private static Map<String, Integer> getWordFreqMap(String pathToWordFreqMap){
		Map<String, Integer> wordFreqMap = ((List<Map<String, Integer>>)FileUtils.deserializeListFromFile(pathToWordFreqMap)).get(0);
		return wordFreqMap;
	}
	
	/**
	 * Deserialize objects in parsedExpressionOutputFileStr, so we don't 
	 * need to read and parse through all papers on every server initialization.
	 * Can just read from serialized data.
	 */
	@SuppressWarnings("unchecked")
	private static List<ParsedExpression> deserializeParsedExpressionsList(){
	
		List<ParsedExpression> parsedExpressionsList = null;
		FileInputStream fileInputStream = null;
		ObjectInputStream objectInputStream = null;
		try{
			fileInputStream = new FileInputStream(parsedExpressionSerialFileStr);
			objectInputStream = new ObjectInputStream(fileInputStream);
		}catch(FileNotFoundException e){
			e.printStackTrace();
			throw new IllegalStateException("ParsedExpressionList output file not found!");
		}catch(IOException e){
			e.printStackTrace();
			throw new IllegalStateException("IOException while opening ObjectOutputStream");
		}
		
		try{
			Object o = objectInputStream.readObject();
			parsedExpressionsList = (List<ParsedExpression>)o;		
		}catch(IOException e){
			e.printStackTrace();
			throw new IllegalStateException("IOException while reading deserialized data!");
		}catch(ClassNotFoundException e){
			e.printStackTrace();
			throw new IllegalStateException("ClassNotFoundException while writing to file or closing resources");
		}finally{
			try{
				objectInputStream.close();
				fileInputStream.close();
			}catch(IOException e){
				e.printStackTrace();
				throw new IllegalStateException("IOException while closing resources");
			}
		}
		return parsedExpressionsList;
	}
	
	/**
	 * Extracts list of theorems/propositions/etc from provided BufferedReader,
	 * with hypotheses added. 
	 * @param srcFileReader
	 *            BufferedReader to get tex from.
	 * @param thmWebDisplayList
	 *            List to contain theorems to display for the web. without
	 *            \labels, \index, etc. Can be null, for callers who don't need it.
	 * @param macros author-defined macros using \newtheorem
	 * @return List of unprocessed theorems read in from srcFileReader, for bag
	 *         of words search. Empty at input.
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public static void readAndParseThm(BufferedReader srcFileReader, 
			ParseState parseState, List<DefinitionListWithThm> definitionListWithThmList,
			List<ThmHypPair> thmHypPairList,
			Stats stats, String fileName, List<String> scrapedThmNameList) throws IOException{
		
		//print to indicate progress, since all other outputs are suppressed during processing.
		System.out.print("...Processing "+fileName);
		List<String> customBeginThmList = new ArrayList<String>();
		
		MacrosTrieBuilder macrosTrieBuilder = new MacrosTrieBuilder();
		//contextual sentences outside of theorems, to be scanned for
		//definitions, and parse those definitions. Reset between theorems.
		StringBuilder contextSB = new StringBuilder();
		
		Map<String, String> thmTypeMap = new HashMap<String, String>(ThmInput.defaultThmTypeMap);
		
		String line = extractMacros(srcFileReader, customBeginThmList, macrosTrieBuilder, thmTypeMap);
		MacrosTrie macrosTrie = macrosTrieBuilder.build();
		
		//append list of macros to THM_START_STR and THM_END_STR
		Pattern[] customPatternAr = addMacrosToThmBeginEndPatterns(customBeginThmList);
		
		Pattern thmStartPattern = customPatternAr[0];
		Pattern thmEndPattern = customPatternAr[1];	
		Pattern eliminateBeginEndThmPattern = customPatternAr[2];
		
		StringBuilder newThmSB = new StringBuilder();
		Matcher matcher;
		boolean inThm = false;
		String curThmType = defaultThmType;
		
		if(null != line){
			
			matcher = thmStartPattern.matcher(line);
			if (matcher.find()) {			
				inThm = true;
				String thmTypeKey = matcher.group(1);
				
				if(null != thmTypeKey) {
					String thmType = thmTypeMap.get(thmTypeKey);
					if(null != thmType) {
						curThmType = thmType;
					}else {
						curThmType = defaultThmType;
					}
				}else {
					curThmType = defaultThmType;
				}
				parseState.setInThmFlag(true);
				
				matcher = ThmInput.beginAnyPattern.matcher(line);
				if(matcher.matches()) {
					newThmSB.append(line.substring(matcher.end(1), line.length()));
				}
			}
		}
		while ((line = srcFileReader.readLine()) != null) {
			if (WordForms.getWhiteEmptySpacePattern().matcher(line).matches()){
				continue;
			}
			//single lines to skip. Such as comments
			if(SINGLE_LINE_SKIP_PATTERN.matcher(line).matches()){
				continue;
			}
			//scrape theorem names. - temporary for Michael, Sept 19.
			if(null != scrapedThmNameList) {
				scrapeThmNames(line, scrapedThmNameList);
			}			
			
			//should skip certain sections, e.g. \begin{proof}
			Matcher skipMatcher = SKIP_PATTERN.matcher(line);
			if(skipMatcher.find()){
				while ((line = srcFileReader.readLine()) != null){
					if(END_SKIP_PATTERN.matcher(line).find()){						
						break;
					}
				}
				continue;
			}
			
			boolean appendedToThm = false;
			matcher = thmStartPattern.matcher(line);
			if (matcher.matches()) {		
				
				String thmTypeKey = matcher.group(1);
				
				if(null != thmTypeKey) {
					String thmType = thmTypeMap.get(thmTypeKey);
					if(null != thmType) {
						curThmType = thmType;
					}else {
						curThmType = defaultThmType;
					}
				}else {
					curThmType = defaultThmType;
				}
				
				String contextStr;
				int contextSBLen = contextSB.length();
				if(contextSBLen > CONTEXT_SB_LENGTH_THRESHOLD){
					//if length exceeds certain threshold, the pattern replacements below can choke,
					//e.g. observed with length that's greater than 200,000. Note this will cut off
					//in middle of sentences.
					int strStart = contextSBLen - CONTEXT_SB_LENGTH_THRESHOLD;
					//grab the most recent substring
					contextStr = contextSB.substring(strStart);
				}else{
					contextStr = contextSB.toString();					
				}				
				//don't need to remove Tex Markup and expand macros here, later after setence has been picked
				//contextStr = ThmInput.removeTexMarkup(contextStr, null, null, macrosTrie,
				//		eliminateBeginEndThmPattern);
				
				contextStr = ThmInput.removeTexMarkup(contextStr, null, null, macrosTrie,
						eliminateBeginEndThmPattern);
				//scan contextSB for assumptions and definitions
				//and parse the definitions
				detectAndParseHypothesis(contextStr, parseState, stats);	
				
				inThm = true;
				newThmSB.append(line);
				appendedToThm = true;
				//this should be set *after* calling detectAndParseHypothesis(), since detectAndParseHypothesis
				//depends on the state.
				parseState.setInThmFlag(true);
				contextSB.setLength(0);
			}
			//if and not else if, since \begin{} and \end{} could be on same line.
			if (thmEndPattern.matcher(line).matches()) {
				
				inThm = false;				
				if(0 == newThmSB.length()){
					continue;
				}
				
				matcher = ThmInput.endAnyPattern.matcher(line);
				//Need to read in until \end{cor} etc
				//but not beyond that.				
				if(matcher.matches()) {
					newThmSB.append(" ").append(matcher.group(1));
				}else {
					newThmSB.append(" ").append(line);
				}
				
				if(newThmSB.length() > THM_MAX_CHAR_SIZE){
					logger.info("thm length exceeds maximum allowable size!");
					continue;
				}
				
				//parse hyp and thm.
				processParseHypThm(newThmSB, parseState, stats, definitionListWithThmList, thmHypPairList, fileName, macrosTrie,
						curThmType, eliminateBeginEndThmPattern);				
				continue;
			}else if(END_DOCUMENT_PATTERN.matcher(line).matches()){
				parseState.parseRunGlobalCleanUp();
				//read until a new document, marked by \documentclass..., is encountered.
				//sometimes the \documentclass follows immediately after \end{document},
				//without starting a new line.
				if(!NEW_DOCUMENT_PATTERN.matcher(line).matches()){
					while(null != (line = srcFileReader.readLine())){					
						if(NEW_DOCUMENT_PATTERN.matcher(line).matches()){
							break;
						}					
					}
				}
				//If multiple latex documents gathered together in one file.				
				macrosTrieBuilder = new MacrosTrieBuilder();
				customBeginThmList = new ArrayList<String>();
				line = extractMacros(srcFileReader, customBeginThmList, macrosTrieBuilder, thmTypeMap);
				macrosTrie = macrosTrieBuilder.build();
				//append list of macros to THM_START_STR and THM_END_STR, for the *next* document in case 
				//multiple are concatenated together.
				customPatternAr = addMacrosToThmBeginEndPatterns(customBeginThmList);				
				continue;
			}

			if(inThm && !appendedToThm){
				newThmSB.append(" ").append(line);
			}else if (!inThm) {
				//need to parse to gather definitions
				//add to contextSB
				contextSB.append(" ").append(line);
			}
		}
		if(DEBUG) {
			parseState.writeUnknownWordsToFile();	
		}
	}

	public static void readAndParseThm(BufferedReader srcFileReader, 
			ParseState parseState, List<DefinitionListWithThm> definitionListWithThmList,
			List<ThmHypPair> thmHypPairList,
			Stats stats, String fileName) throws IOException{
		
		List<String> scrapedThmNameList = null;
		readAndParseThm(srcFileReader, parseState, definitionListWithThmList,
				thmHypPairList, stats, fileName, scrapedThmNameList);
	}
	/**
	 * Processes (e.g. remove tex markup) and parses a theorem after it has been read in.
	 * @param newThmSB StringBuilder containing the theorem.
	 * @param parseState
	 * @param stats
	 * @param definitionListWithThmList
	 * @param thmType, e.g. Theorem, Conjecture, Lemma, etc.
	 * @return thm, just thm, no hyp
	 */
	private static String processParseHypThm(StringBuilder newThmSB, ParseState parseState, Stats stats, 
			List<DefinitionListWithThm> definitionListWithThmList, List<ThmHypPair> thmHypPairList,
			String srcFileName, MacrosTrie macrosTrie, String thmType,
			Pattern eliminateBeginEndThmPattern){
		
		// Replace Tex in text with macro expansions.
		//process here, return two versions, one for bag of words, one
		// for display
		// strip \df, \empf. Index followed by % strip, not percent
		// don't strip.
		// replace enumerate and \item with *
		//thmWebDisplayList, and bareThmList should both be null
		String thm = ThmInput.removeTexMarkup(newThmSB.toString(), null, null, macrosTrie,
				eliminateBeginEndThmPattern);
		
		//Must clear headParseStruct and curParseStruct of parseState, so newThm
		//has its own stand-alone parse tree.
		parseState.setCurParseStruct(null);
		parseState.setHeadParseStruct(null);
		
		//first gather hypotheses in the theorem.
		detectAndParseHypothesis(thm, parseState, stats);
		
		//if contained in local map, should be careful about when to append map.		
		//append to newThmSB additional hypotheses that are applicable to the theorem.				
		DefinitionListWithThm thmDef = appendHypothesesAndParseThm(thm, parseState, thmHypPairList, stats, srcFileName,
				macrosTrie, thmType, eliminateBeginEndThmPattern);		
		
		if(thmDef != DefinitionListWithThm.PLACEHOLDER_DEF_LIST_WITH_THM){
			definitionListWithThmList.add(thmDef);
		}
		
		//should parse the theorem.
		//serialize the full parse, i.e. parsedExpression object, along with original input.				
		
		//local clean up, after done with a theorem, but still within same document.
		parseState.parseRunLocalCleanUp();
		newThmSB.setLength(0);		
		return thm;
	}
	
	/**
	 * Create custom start and end patterns by appending to THM_START_STR and THM_END_STR.
	 * @param macrosList
	 * @return
	 */
	private static Pattern[] addMacrosToThmBeginEndPatterns(List<String> macrosList) {
		//compiler will inline these, so don't add function calls to stack.
		Pattern[] customPatternAr = new Pattern[]{ThmInput.THM_START_PATTERN, ThmInput.THM_END_PATTERN,
				ThmInput.ELIMINATE_BEGIN_END_THM_PATTERN};
		if(!macrosList.isEmpty()){
			StringBuilder startBuilder = new StringBuilder();
			StringBuilder endBuilder = new StringBuilder();
			StringBuilder eliminateBuilder = new StringBuilder();
			
			for(String macro : macrosList){
				//create start and end macros  .*\\\\begin\\s*\\{def(?:.*)
				//but only if isn't already included in THM_START_PATTERN \\\\begin\\{def(?:[^}]*)\\}\\s*
				if(ThmInput.THM_START_PATTERN.matcher("\\begin{" + macro).matches()){
					continue;
				}
				//capture the macro for thm start, to find theorem type, for display on website.
				startBuilder.append(macro).append("|");
				
				//should only match white spaces instead of starting with "|.*\\\...":
				endBuilder.append(macro).append("|");
				//e.g. "\\begin{lemma*} if no numbering needed"
				eliminateBuilder.append("|\\\\begin\\s*\\{").append(macro).append("\\**\\}\\s*");
				eliminateBuilder.append("|\\\\end\\s*\\{").append(macro).append("\\**\\}\\s*");
			}
			if(startBuilder.length() > 0){
				customPatternAr[0] = Pattern.compile(ThmInput.THM_START_STR0 + startBuilder + ThmInput.THM_START_STR1);
				customPatternAr[1] = Pattern.compile(ThmInput.THM_END_STR0 + endBuilder + ThmInput.THM_END_STR1);
				customPatternAr[2] = Pattern.compile(ThmInput.ELIMINATE_BEGIN_END_THM_STR + eliminateBuilder, 
						Pattern.CASE_INSENSITIVE);
			}
		}
		//if(true) throw new IllegalStateException("updated macros list: " + Arrays.deepToString(customPatternAr));
		return customPatternAr;
	}

	/**
	 * Read in custom macros, break as soon as \begin{...} encountered, 
	 * in particular \begin{document}. There are no \begin{...} in the preamble.
	 * But some authors use the bad practice of defining macros after \begin{document}.
	 * @param srcFileReader
	 * @param thmMacrosList *Only* macros to indicate begin of new theorems, propositions, etc.
	 * @param thmTypeMap Map of thm macro, and the thm type, i.e. {key: thm, value: Theorem}.
	 * @throws IOException
	 */
	private static String extractMacros(BufferedReader srcFileReader, List<String> thmMacrosList, 
			MacrosTrieBuilder macrosTrieBuilder, Map<String, String> thmTypeMap) throws IOException {
		
		String line = null;
		boolean documentBegan = false;
		while ((line = srcFileReader.readLine()) != null) {
			//should also extract those defined with \def{} patterns.
			Matcher newThmMatcher;	
			//break on any other begin, *after* but not including \begin{document}. Some authors follow the 
			//bad practice of defining macros after \begin{document}.			
			if(ThmInput.BEGIN_PATTERN.matcher(line).find()){
				if(ThmInput.BEGIN_DOC_PATTERN.matcher(line).find()) {
					documentBegan = true;
				}else if(documentBegan) {
					break;					
				}
			} 
			if((newThmMatcher = ThmInput.NEW_THM_PATTERN.matcher(line)).matches()){
				//should be a proposition, hypothesis, etc. E.g. don't look through proofs.
				//Keep whether it's thm, def, conjecture, etc, to label on the website.
				Matcher m;
				if((m=ThmInput.THM_TERMS_PATTERN.matcher(newThmMatcher.group(2))).find()){
					//e.g. thm, conj, def.
					String thmMacro = newThmMatcher.group(1);
					//describe whether it's thm, hyp, etc.
					String thmType = m.group(1);
					thmTypeMap.put(thmMacro, thmType);
					thmMacrosList.add(thmMacro);	
				}
			}else if((newThmMatcher = ThmInput.NEW_CMD_PATTERN.matcher(line)).matches()){
				String commandStr = newThmMatcher.group(1);
				String replacementStr = newThmMatcher.group(4);
				String slotCountStr = newThmMatcher.group(2);
				int slotCount = null == slotCountStr ? 0 : Integer.valueOf(slotCountStr);
				//default argument for optional parameters. The default for this default value
				// is null, so no need to check for null.
				String optArgDefault = newThmMatcher.group(3);				
				macrosTrieBuilder.addTrieNode(commandStr, replacementStr, slotCount, optArgDefault);
			}else if((newThmMatcher = ThmInput.NEW_DEF_PATTERN.matcher(line)).matches()){
				//case of \def\X{{\cal X}}
				String commandStr = newThmMatcher.group(1);
				String replacementStr = newThmMatcher.group(2);
				macrosTrieBuilder.addTrieNode(commandStr, replacementStr, 0);
			}else if((newThmMatcher = ThmInput.MATH_OP_PATTERN.matcher(line)).matches()){
				//case of \DeclareMathOperator{\sin}{sin}
				String commandStr = newThmMatcher.group(1);
				String replacementStr = newThmMatcher.group(2);
				//typeset "sin x" non-italicized, and with space after op.
				replacementStr = "\\text{" + replacementStr + " }";
				macrosTrieBuilder.addTrieNode(commandStr, replacementStr, 0);
			}	
		}
		//if(true)throw new IllegalStateException("macros: "+macrosList);
		return line;
	}
	
	/**
	 * detect hypotheses and definitions, and add definitions to parseState.
	 * @param contextSB
	 * @param parseState
	 */
	private static void detectAndParseHypothesis(String contextStr, ParseState parseState, Stats stats){
		
		//split on punctuations precede a space, but keep the punctuation.
		//String[] contextStrAr = PUNCTUATION_PATTERN.split(contextStr);
		List<String> originalCaseInputList = new ArrayList<String>();
		String[] contextStrAr = ThmP1.preprocess(contextStr, originalCaseInputList);
		boolean inputsSameLen = contextStrAr.length == originalCaseInputList.size();
		for(int i = 0; i < contextStrAr.length; i++){
			String sentence = contextStrAr[i];
			if(isHypothesis(sentence)){	
				parseState.setCurParseStruct(null);
				parseState.setHeadParseStruct(null);
				sentence = inputsSameLen ? originalCaseInputList.get(i) : sentence;
				ParseRun.parseInput(sentence, parseState, PARSE_INPUT_VERBOSE, stats);
			}
		}
	}
	
	/**
	 * Append hypotheses and definition statements in front of thmSB, 
	 * for the variables that do appear in thmSB.
	 * 
	 * @param thmSB
	 * @param parseState
	 */
	private static DefinitionListWithThm appendHypothesesAndParseThm(String thmStr, ParseState parseState, 
			List<ThmHypPair> thmHypPairList, Stats stats, String srcFileName,
			MacrosTrie macrosTrie, String thmType, Pattern eliminateBeginEndThmPattern){
		
		StringBuilder definitionSB = new StringBuilder();		
		StringBuilder latexExpr = new StringBuilder();
		
		List<VariableDefinition> variableDefinitionList = new ArrayList<VariableDefinition>();
		//varDefSet set to keep track of which VariableDefinition's have been added, so not to 
		//add duplicate ones.
		Set<VariableDefinition> varDefSet = new HashSet<VariableDefinition>();
		
		int thmStrLen = thmStr.length();		
		boolean mathMode = false;
		
		//Parse the thm first, with the variableNamesMMap already updated to include contexual definitions.
				//should return parsedExpression object, and serialize it. But only pick up definitions that are 
				//not defined locally within this theorem.
		//System.out.println("~~~~~~parsing~~~~~~~~~~");
		try{
			ParseRun.parseInput(thmStr, parseState, PARSE_INPUT_VERBOSE, stats);
		}catch(Throwable e){
			String msg = "\nThrowable thrown when parsing thm: " + Arrays.toString(e.getStackTrace());
			System.out.println(msg);
		}
		
		//remove if(false) after testing!
		//if(false)
		if(parseState.numNonTexTokens() < NUM_NON_TEX_TOKEN_THRESHOLD || parseState.curParseExcessiveLatex()){
			//set parseState flag, not strictly necessary, since cleanup will happen soon,
			//but set for safe practice.
			parseState.setCurParseExcessiveLatex(false);
			return DefinitionListWithThm.PLACEHOLDER_DEF_LIST_WITH_THM;
		}
		
		//filter through text and try to pick up definitions.
		for(int i = 0; i < thmStrLen; i++){
			if(variableDefinitionList.size() >= variableDefinitionListThresh) {
				break;
			}
			char curChar = thmStr.charAt(i);			
			//go through thm, get the variables that need to be defined
			//once inside Latex, use delimiters, should also take into account
			//the case of entering math mode with \[ ! Although mostly interested
			//in those wrapped in $
			if(curChar == '$' && !WordForms.isCharEscaped(thmStr, i)){
				if(!mathMode){
					mathMode = true;					
				}else{
					mathMode = false;
					//process the latexExpr, first pick out the variables,
					//and try to find definitions for them. Appends original
					//definition strings to thmWithDefSB. Should only append
					//variables that are not defined within the same thm.				
					List<VariableDefinition> varDefList = pickOutVariables(latexExpr.toString(), //variableNamesMMap,
							parseState, varDefSet, definitionSB, macrosTrie, eliminateBeginEndThmPattern);
					//if(true) throw new RuntimeException();
					if(DEBUG) {
						System.out.println("DetectHypothesis - varDefList " + varDefList);
						System.out.println("DetectHypothesis - parseState.getGlobalVariableNamesMMap " + parseState.getGlobalVariableNamesMMap()
						+ " localVariableNamesMMap:  " + parseState.getLocalVariableNamesMMap());
					}
					variableDefinitionList.addAll(varDefList);
					latexExpr.setLength(0);
				}			
			}else if(mathMode){
				latexExpr.append(curChar);
			}			
		}
		
		String definitionStr = definitionSB.toString();
		//postprocess thm string, to better display on web
		thmStr = postProcessThmForSearch(thmStr);
		DefinitionListWithThm defListWithThm = 
				new DefinitionListWithThm(thmStr, variableDefinitionList, definitionStr, srcFileName);
		
		//relational and context vecs can't be null, since ImmutableList cannot contain null elements
		Set<Integer> relationalContextVec = parseState.getRelationalContextVec();
		
		if(null == relationalContextVec){
			//write placeholder
			relationalContextVec = new HashSet<Integer>();
		}
		Map<Integer, Integer> combinedContextVecMap = parseState.getCurThmCombinedContextVecMap();
		if(null == combinedContextVecMap){
			combinedContextVecMap = ParseState.PLACEHOLDER_CONTEXT_VEC();
		}
		
		//create parsedExpression to serialize to persistent storage to be used later
		//for search, etc
		/*** June 2017 ParsedExpression parsedExpression = new ParsedExpression(thmStr, parseState.getHeadParseStruct(),
						defListWithThm);*/		
		//parsedExpressionList.add(parsedExpression);
		ThmHypPair thmHypPair = new ThmHypPair(thmStr, definitionStr, srcFileName, thmType);
		thmHypPairList.add(thmHypPair);
		if(DEBUG) System.out.println("DetectHypothesis - thmHypPair "+ thmHypPair);
		
		ContextRelationVecPair vecsPair = new ContextRelationVecPair(combinedContextVecMap, relationalContextVec);
		contextRelationVecPairList.add(vecsPair);
		String thmHypPairString = thmHypPair.toString();
		parsedExpressionStrList.add(thmHypPairString);
		allThmsStrWithSpaceList.add(thmHypPairString + "\n");
		//return this to supply to search later
		return defListWithThm;
	}
	
	/**
	 * Post process thm string for search.
	 * @param thmStr
	 * @return
	 */
	private static String postProcessThmForSearch(String thmStr) {
		Matcher enumMatcher = ENUMERATE_PATTERN.matcher(thmStr);
		if(enumMatcher.find()){
			thmStr = enumMatcher.replaceAll("");
		}
		return thmStr;
	}

	/**
	 * Picks out variables to be defined, and try to match them with prior definitions
	 * as stored in parseState, stored when the chunk of text prior to thm was parsed.
	 * Picks up variable definitions.
	 * @param latexExpr 
	 * @param thmDefSB StringBuilder that's the original input string appended
	 * to the definition strings.
	 * @param varDefSet set to keep track of which VariableDefinition's have been added, so not to 
	 * add duplicate ones.
	 */
	private static List<VariableDefinition> pickOutVariables(String latexExpr, 
			//ListMultimap<VariableName, VariableDefinition> variableNamesMMap,
			ParseState parseState, Set<VariableDefinition> varDefSet,
			StringBuilder thmDefSB, MacrosTrie macrosTrie, Pattern eliminateBeginEndThmPattern){
		
		//list of definitions needed in this latexExpr
		List<VariableDefinition> varDefList = new ArrayList<VariableDefinition>();
		
		List<String> varsList = TexToTree.texToTree(latexExpr);
		if(DEBUG) System.out.println("DetectHypothesis - varsList " + varsList);
		int varsListSz = varsList.size();
		for(int i = 0; i < varsListSz; i++){
			
			String possibleVar = varsList.get(i);
			
			//Get a variableName and check if a variable has been defined.
			VariableName possibleVariableName = ParseState.createVariableName(possibleVar);
			VariableDefinition possibleVarDef = new VariableDefinition(possibleVariableName, null, null);
			
			boolean isLocalVar = parseState.getVariableDefinitionFromName(possibleVarDef);
			//whether the variable definition was defined locally in the theorem, used to determine whether
			//to include originalDefiningSentence.
			
			if(DEBUG) {
				System.out.println("^^^ local variableNamesMMap: "+ parseState.getLocalVariableNamesMMap());
				System.out.println("^^^ global variableNamesMMap: "+ parseState.getGlobalVariableNamesMMap());				
			}
			
			//if empty, check to see if bracket pattern, if so, check just the name without the brackets.
			//e.g. x in x(yz)
			if(null == possibleVarDef.getDefiningStruct()){
				Matcher bracketSeparatorMatcher = BRACKET_SEPARATOR_PATTERN.matcher(possibleVar);
				if(bracketSeparatorMatcher.find()){
					possibleVariableName = ParseState.createVariableName(bracketSeparatorMatcher.group(1));
					possibleVarDef.setVariableName(possibleVariableName);
					//look within current thm, and in context gathered.
					isLocalVar = parseState.getVariableDefinitionFromName(possibleVarDef);				
				}
			}
			//if some variable found.
			if(!isLocalVar && null != possibleVarDef.getDefiningStruct()){
				
				String defSentence = possibleVarDef.getOriginalDefinitionSentence();
				if(defSentence.length() > defSentenceLenThresh) {					
					continue;
				}
				if(!varDefSet.contains(possibleVarDef)){
					varDefSet.add(possibleVarDef);
					varDefList.add(possibleVarDef);
					boolean isContextStr = true;
					defSentence = ThmInput.removeTexMarkup(defSentence, 
							null, null, macrosTrie, eliminateBeginEndThmPattern, isContextStr);
					//close any starting delimiters, e.g. "$", \[, etc					
					defSentence = closeMathDelim(defSentence);
					thmDefSB.append(defSentence).append(" ");
				}
			}			
		}
		return varDefList;
	}
	
	/**
	 * Close opening math delimiters that may not be closed at end of context sentence.
	 *  e.g. "$", \[, etc. Note could have close without opening, if start was cut off.
	 *  This is after \begin{align}, etc have been substituted, so only need to check 
	 *  $ and \[. <-what about \begin{equation}?
	 * @param contextStr
	 * @return
	 */
	public static String closeMathDelim(String contextStr) {
		//iterate backwards 
		boolean closed = false;
		boolean opened = false;
		int strLen = contextStr.length();
		if(strLen == 0) {
			return contextStr;
		}
		int dollarCount = 0;
		for(int i = strLen-1; i > 0; i--) {
			
			if(contextStr.charAt(i-1) == '\\') {				
				if(!closed && contextStr.charAt(i) == '[') {
					opened = true;
				}else if(contextStr.charAt(i) == ']') {
					closed = true;
				}		
			}
			//now contextStr.charAt(i-1) != '\\'			
			else if(contextStr.charAt(i) == '$'){
				dollarCount++;				
			}
		}
		if(opened) {
			contextStr += "\\]";
		}
		//use else here, since can't append both \] and $
		else {
			if(contextStr.charAt(0) == '$') {
				dollarCount++;
			}			
			//look for odd
			if((dollarCount & 1) == 1) {
				//replace "$$" with "$", so 
				Matcher matcher = doubleDollarPatt.matcher(contextStr);
				matcher.replaceAll("\\$");
				//note this is not fool-proof: might not be the right one being closed.
				//e.g. "s_1$ text $ s $"
				contextStr += "$";
			}
		}		
		return contextStr;
	}
	
	/**
	 * Try substituting definitions in, for context 
	 * searcher to better detect relations. E.g. then $s_i$ converges goes
	 * to "sequence $s_i$ converges".
	 * //add defining structures of variable in string for parsing purposes.
	//Input thmStr is thm to be parsed. Only substitute relatively simply variables,
	//e.g. $f$, or $H(X, O_X)=...$" on lhs of equals.
	 * @param thm
	 * @return
	 */	
	public static String replaceSymbols(String thmStr, ParseState parseState){
		
		int thmStrLen = thmStr.length();
		StringBuilder latexExpr = new StringBuilder(10);
		StringBuilder sb = new StringBuilder(thmStrLen + 40);
		
		boolean mathMode = false;
		//filter through text and try to pick up definitions.
				for(int i = 0; i < thmStrLen; i++){		
					char curChar = thmStr.charAt(i);	
					sb.append(curChar);
					//go through thm, get the variables that need to be defined
					//once inside Latex, use delimiters, should also take into account
					//the case of entering math mode with \[ ! Although mostly interested
					//in those wrapped in $
					if(curChar == '$'  && !WordForms.isCharEscaped(thmStr, i)){
						if(!mathMode){
							mathMode = true;					
						}else{
							mathMode = false;
							//process the latexExpr, first pick out the variables,
							//and try to find definitions for them. Appends original
							//definition strings to thmWithDefSB. Should only append
							//variables that are not defined within the same thm.				
							String varDefStr = pickOutVariables(latexExpr.toString(), //variableNamesMMap,
									parseState);
							//if(true) throw new RuntimeException();
							if(DEBUG) {
								System.out.println("DetectHypothesis - varDefStr " + varDefStr);
								System.out.println("DetectHypothesis - parseState.getGlobalVariableNamesMMap " + parseState.getGlobalVariableNamesMMap()
								+ " localVariableNamesMMap:  " + parseState.getLocalVariableNamesMMap());
							}
							//variableDefinitionList.addAll(varDefList);
							latexExpr.setLength(0);
							sb.append(" ").append(varDefStr).append(" ");
						}			
					}else if(mathMode){
						latexExpr.append(curChar);
					}			
				}
		
		return sb.toString();
	}
	
	/**
	 * Find the variable corresponding to input latexExpr, given 
	 * variable definitions collected when parsing thm. 
	 * 
	 * @param latexExpr
	 * @param parseState
	 * @param varDefSet
	 * @param thmDefSB
	 * @param macrosTrie
	 * @return
	 */
	private static String pickOutVariables(String latexExpr, 
			ParseState parseState){
		
		//list of definitions needed in this latexExpr
		//List<VariableDefinition> varDefList = new ArrayList<VariableDefinition>();
		
		List<String> varsList = TexToTree.texToTree(latexExpr);
		if(DEBUG) System.out.println("DetectHypothesis - varsList " + varsList);
		int varsListSz = varsList.size();
		String structStr = "";
		//only use first defining struct		
		if(varsListSz > 0){
			
			String possibleVar = varsList.get(0);
			
			//Get a variableName and check if a variable has been defined.
			VariableName possibleVariableName = ParseState.createVariableName(possibleVar);
			VariableDefinition possibleVarDef = new VariableDefinition(possibleVariableName, null, null);
			
			parseState.getVariableDefinitionFromName(possibleVarDef);
			//whether the variable definition was defined locally in the theorem, used to determine whether
			//to include originalDefiningSentence.
			
			if(DEBUG) {
				System.out.println("^^^ local variableNamesMMap: "+ parseState.getLocalVariableNamesMMap());
				System.out.println("^^^ global variableNamesMMap: "+ parseState.getGlobalVariableNamesMMap());				
			}
			
			//if empty, check to see if bracket pattern, if so, check just the name without the brackets.
			//e.g. x in x(yz)
			if(null == possibleVarDef.getDefiningStruct()){
				Matcher bracketSeparatorMatcher = BRACKET_SEPARATOR_PATTERN.matcher(possibleVar);
				if(bracketSeparatorMatcher.find()){
					possibleVariableName = ParseState.createVariableName(bracketSeparatorMatcher.group(1));
					possibleVarDef.setVariableName(possibleVariableName);
					//look within current thm, and in context gathered.
					parseState.getVariableDefinitionFromName(possibleVarDef);				
				}
			}
			Struct definingStruct = possibleVarDef.getDefiningStruct();
			//if some variable found. //!isLocalVar &&
			if(null != definingStruct){	
				//possibleVarDef.getOriginalDefinitionSentence();
				structStr = definingStruct.nameStr();				
			}			
		}
		return structStr;
	}
	
}
