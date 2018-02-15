package thmp.search;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;
import com.wolfram.puremath.dbapp.SimilarThmUtils;

import thmp.parse.ParsedExpression;
import thmp.parse.DetectHypothesis;
import thmp.parse.DetectHypothesis.DefinitionListWithThm;
import thmp.search.CollectThm.ThmWordsMaps.IndexPartPair;
import thmp.search.LiteralSearch.LiteralSearchIndex;
import thmp.search.SearchCombined.ThmHypPair;
import thmp.search.Searcher.SearchConfiguration;
import thmp.search.Searcher.SearchMetaData;
import thmp.search.TheoremGet.ContextRelationVecPair;
import thmp.search.ThmSearch.TermDocumentMatrix;
import thmp.utils.FileUtils;
import thmp.utils.MathLinkUtils.WLEvaluationMedium;
import thmp.utils.WordForms.ThmPart;

import static thmp.utils.MathLinkUtils.evaluateWLCommand;

/**
 * Tools for manipulating created projection matrix, 
 * apply projection mx to query vecs, 
 * combine serialized matrices together into List in linear time.
 * These tools are normally run locally on build machines, rather than on the server.
 * @author yihed
 */
public class ProjectionMatrix {
	
	//private static final KernelLink ml = FileUtils.getKernelLinkInstance();
	private static final Logger logger = LogManager.getLogger(ProjectionMatrix.class);
	//without the trailing extension ".mx".
	private static final String combinedMxRootPath = "src/thmp/data/mx/" 
			+ TermDocumentMatrix.COMBINED_PROJECTED_TERM_DOCUMENT_MX_NAME; //+ ".mx";
	private static final int TAR_COUNT_PER_BUNDLE = 5;//<--5 for testing for now, usually 15;
	//private static final Map<String, Integer> threeGramsMap = ThreeGramSearch.get3GramsMap();
	//private static final Map<String, Integer> twoGramsMap = NGramSearch.get2GramsMap();
	/** Map of keywords and their indices in the keywords map. Consistent with keyword indices
	 * in e.g. context vec maps. */
	
	/**
	 * Map of keywords and their scores in document, the higher freq in doc, the
	 * lower score, say 1/(log freq + 1) since log 1 = 0.
	 */
	private static final ImmutableMap<String, Integer> wordsScoreMap;

	/**
	 * Multimap of words, and the theorems (their indices) in thmList, the word
	 * shows up in.
	 */
	private static final ImmutableMultimap<String, IndexPartPair> wordThmsIndexMMap;
	
	//regex to match strings of form: "'math0702266','Florent','','Baudier'"
	private static final Pattern NAME_DATA_LINE_PATT = Pattern.compile("'([^']+)'.+");
	
	static {
		wordsScoreMap = CollectThm.ThmWordsMaps.get_wordsScoreMap();
		wordThmsIndexMMap = CollectThm.ThmWordsMaps.get_wordThmsMMap();
		
	}
	
	/**
	 * args is list of paths. 
	 * Supply list of paths (vararg) to directories, each contanining a parsedExpressionList and
	 * the *projected* term document matrices for a tar file.
	 * e.g. "0208_001Untarred/0208/", or "0304_001Untarred/0304", OR
	 * "0304_001Untarred/0304/FullTDMatrix.mx"
	 * Could also supply file containing such paths, with exact same format.
	 */
	public static void main(String[] args){
		int argsLen = args.length;
		if(argsLen == 0){
			System.out.println("Suply a list of paths containing .mx files! E.g. \"0304_001Untarred/0304\"."
					+ "Or a file containing such paths.");
			return;
		}
		//check if supplied arg is file containing directories, or a single directory.
		if(1 == argsLen){
			args = readListFromFile(args);
			argsLen = args.length;
		}
		
		serializeCombinedSources(args, argsLen);
	}

	/**
	 * Parses raw names data string to map.
	 * @return Map, keys are paper ID, values are String data for the names.
	 * E.g. {"math0702266" : "'math0702266','Florent','','Baudier'"}
	 */
	private static Multimap<String, String> parseRawNameDataFile() {
		
		String filePath = SearchMetaData.nameRawDataPath();
		List<String> nameDataList = FileUtils.readLinesFromFile(filePath);
		
		/*processed sources handed to me contain duplicates*/
		Multimap<String, String> namesMMap = HashMultimap.create();
		
		Matcher m;
		//each line has form 'math0702266','Florent','','Baudier'
		for(String dataStr : nameDataList) {
			if(!(m=NAME_DATA_LINE_PATT.matcher(dataStr)).matches()) {
				System.out.println("ProjectionMatrix - name data Pattern doesn't match!");
				continue;
			}
			String paperIdStr = m.group(1);
			namesMMap.put(paperIdStr, dataStr.toLowerCase());
		}		
		return namesMMap;
	}
	
	/**
	 * Combined sources contained in files supplied. Serialize the combined sources.
	 * Create bundles for given number of files, 
	 * @param args
	 * @param argsLen
	 */
	private static void serializeCombinedSources(String[] args, int argsLen) {
		//List<String> parsedExpressionFilePathList = new ArrayList<String>();
		//List<String> contextRelationVecPairFilePathList = new ArrayList<String>();
		//form list of String's of paths, e.g. "0208_001/0208/termDocumentMatrixSVD.mx".
		List<String> projectedMxFilePathList = new ArrayList<String>();
		List<ThmHypPair> combinedPEList = new ArrayList<ThmHypPair>();
		List<ContextRelationVecPair> combinedVecsList = new ArrayList<ContextRelationVecPair>();
		List<Integer> bundleStartThmIndexList = new ArrayList<Integer>();
		List<String> allThmNameScrapeList = new ArrayList<String>();
		
		ListMultimap<String, LiteralSearchIndex> literalSearchIndexMap 
			= ArrayListMultimap.create();
		
		String thmsStringListDestPath = TermDocumentMatrix.DATA_ROOT_DIR_SLASH + TermDocumentMatrix.ALL_THM_STRING_FILE_NAME;
		FileUtils.runtimeExec("rm " + thmsStringListDestPath);
		
		Multimap<String, String> paperIdNameDataMap = parseRawNameDataFile();
		
		FileWriter nameCSVFileWriter = null;
		try {
			nameCSVFileWriter = new FileWriter(SearchMetaData.nameCSVDataPath());
		} catch (IOException e) {			
			throw new IllegalStateException("IOException while opening FileWriter!");
		}
		
	    BufferedWriter nameCSVBWriter = new BufferedWriter(nameCSVFileWriter);
	    PrintWriter nameCSVPWriter = new PrintWriter(nameCSVBWriter);
		
		int loopTotal = argsLen / TAR_COUNT_PER_BUNDLE + 1;
		int vecsFileNameCounter = 0;
		//will go up to O(10^6) when all tars are included.
		int thmCounter = 0;
		//combined MMap from multiple tars.
		Multimap<String, IndexPartPair> combinedWordThmIndexMMap = ArrayListMultimap.create();
		//process TAR_COUNT_PER_BUNDLE each time. This loops over tar files.
		//Loops about 1569/15 ~ 105 times if all tar files supplied.
		for(int i = 0; i < loopTotal; i++){
			//stringbuilder for creating name csv file.
			StringBuilder nameDBSb = new StringBuilder(300000);
			
			int start = i * TAR_COUNT_PER_BUNDLE;
			int nextIndex = (i+1)*TAR_COUNT_PER_BUNDLE;
			int end = nextIndex < argsLen ? nextIndex : argsLen;
			//int end = i < loopTotal-1 ? (i+1)*TAR_COUNT_PER_BUNDLE : argsLen;
			if(end > start){
				bundleStartThmIndexList.add(thmCounter);
				for(int j = start; j < end; j++){
					String dirName = args[j];
					if(!(new File(dirName)).exists()){
						continue;
					}
					String path_j = FileUtils.addIfAbsentTrailingSlashToPath(dirName);
					projectedMxFilePathList.add(path_j + ThmSearch.TermDocumentMatrix.PROJECTED_MX_NAME + ".mx");	
					String peFilePath = path_j + ThmSearch.TermDocumentMatrix.PARSEDEXPRESSION_LIST_FILE_NAME_ROOT;
									
					String vecsFilePath = path_j + "vecs/" + ThmSearch.TermDocumentMatrix.CONTEXT_VEC_PAIR_LIST_FILE_NAME;
					String wordThmIndexMMapPath = path_j + SearchMetaData.wordThmIndexMMapSerialFileName();
					
					thmCounter = addExprsToLists(path_j, peFilePath, combinedPEList, vecsFilePath, combinedVecsList, wordThmIndexMMapPath,
							combinedWordThmIndexMMap, literalSearchIndexMap, allThmNameScrapeList, thmCounter, nameDBSb,
							paperIdNameDataMap);
					
					//append lists of ThmHypPair's to one file
					/* don't do this for now, cause memory overflow on allowed space on byblis67 - Aug 21, 2017
					String thmsListOriginPath = path_j + TermDocumentMatrix.ALL_THM_STRING_FILE_NAME;
					FileUtils.runtimeExec("cat " + thmsListOriginPath + " >> " + thmsStringListDestPath);*
					*/
				}
				//thmCounter += combinedPEList.size();
				System.out.println("Serializing combinedPEList size: " + combinedPEList.size() + "   index: " +i);
				System.out.println("Serializing combinedVecsList size: " + combinedVecsList.size() + "   index: " +i);
				
				//write csv file
				nameCSVPWriter.write(nameDBSb.toString());
				
				serializeListsToFile(combinedPEList, i);
				combinedPEList = new ArrayList<ThmHypPair>();
				///return the counter
				vecsFileNameCounter = splitAndUpdateCombinedVecsList(combinedVecsList, vecsFileNameCounter);
				//without the trailing ".mx". E.g. "CombinedTDMatrix0"
				String combinedProjectedTDMxName = TermDocumentMatrix.COMBINED_PROJECTED_TERM_DOCUMENT_MX_NAME + i;
				//Appends the index i to the name.
				combineProjectedMx(projectedMxFilePathList, TermDocumentMatrix.PROJECTED_MX_NAME, i, combinedProjectedTDMxName);
				projectedMxFilePathList = new ArrayList<String>();
			}
		}
		//serialize the remaining thm vecs (must be less than the number of thms per vecsBundle).
		if(!combinedVecsList.isEmpty()){
			//Name deliberately does not contain ".dat" at end.
			String path = TheoremGet.ContextRelationVecBundle.BASE_FILE_STR 
					+ String.valueOf(vecsFileNameCounter);
			FileUtils.serializeObjToFile(combinedVecsList, path);
		}
		int keywordsMapSz = CollectThm.ThmWordsMaps.get_CONTEXT_VEC_SIZE();
		Searcher.SearchConfiguration searchConfig = new Searcher.SearchConfiguration(bundleStartThmIndexList, thmCounter,
				keywordsMapSz);
		List<Searcher.SearchConfiguration> searchConfigList = new ArrayList<Searcher.SearchConfiguration>();
		searchConfigList.add(searchConfig);
		FileUtils.serializeObjToFile(searchConfigList, SearchConfiguration.searchConfigurationSerialPath());
		
		List<ListMultimap<String, LiteralSearchIndex>> literalSearchIndexMapList 
			= new ArrayList<ListMultimap<String, LiteralSearchIndex>>();
		literalSearchIndexMapList.add(literalSearchIndexMap);
		//to be put into database, rather than storing in memory, for access at app runtime
		FileUtils.serializeObjToFile(literalSearchIndexMapList, SearchMetaData.literalSearchIndexMapPath());
		//for human inspection purposes, so put path here.
		final String literalSearchMapKeysPath = "src/thmp/data/literalSearchIndexMapKeys.txt";
		FileUtils.writeToFile(literalSearchIndexMap.keySet(), literalSearchMapKeysPath);
		
		//serialize scraped thm names
		FileUtils.serializeObjToFile(allThmNameScrapeList, DetectHypothesis.allThmNameScrapeSerStr);
		FileUtils.writeToFile(allThmNameScrapeList, DetectHypothesis.allThmNameScrapeTxtStr);
		
		//serialize map into one file, to be loaded at once in memory at runtime.
		//should be ~240 MB.
	 	String wordThmIndexMMapPath = FileUtils.getPathIfOnServlet(SearchMetaData.wordThmIndexMMapSerialFilePath());
	 	List<Multimap<String, IndexPartPair>> combinedWordThmIndexMMapList = new ArrayList<Multimap<String, IndexPartPair>>();
	 	combinedWordThmIndexMMapList.add(combinedWordThmIndexMMap);	 	
	 	FileUtils.serializeObjToFile(combinedWordThmIndexMMapList, wordThmIndexMMapPath);
	 	
	 	FileUtils.silentClose(nameCSVFileWriter);
		
	}

	/**
	 * @param args
	 * @return
	 */
	private static String[] readListFromFile(String[] args) {
		File file = new File(args[0]);
		System.out.println("Trying to see if " + args[0] +" is file!");
		if(file.isFile()){
			
			List<String> pathsList = new ArrayList<String>();
			try{
				FileReader fileReader = null;
				BufferedReader bReader = null;
				try{
					fileReader = new FileReader(file);
					bReader = new BufferedReader(fileReader);
					String line;
					
					while((line = bReader.readLine()) != null){
						int lineLen = line.length();
						//in case the file is supplied in the form that includes the mx.
						//e.g. 0405_001Untarred/0405/FullTDMatrix.mx
						if(lineLen > 3 && line.substring(lineLen-3).equals(".mx")){
							line = FileUtils.findFilePathDirectory(line);
						}
						pathsList.add(line);
					}
				}finally{
					FileUtils.silentClose(fileReader);
					FileUtils.silentClose(bReader);
				}
				
			}catch(FileNotFoundException e){
				throw new IllegalStateException(e);
			}
			catch(IOException e){
				throw new IllegalStateException(e);
			}
			args = new String[pathsList.size()];
			args = pathsList.toArray(args);
			
		}
		return args;
	}
	
	/**
	 * Split combinedVecsList and serialize all pieces to file, up to an integral multiple
	 * of ContextRelationVecBundle.numThmsInBundle(). Put the rest back in combinedVecsList,
	 * to be serialized next time. This guarantees all files contain same number of vecs, 
	 * until the very last file for all tars.
	 * @param combinedVecsList
	 * @param vecsFileNameCounter
	 */
	private static int splitAndUpdateCombinedVecsList(List<ContextRelationVecPair> combinedVecsList,
			int vecsFileNameCounter) {
		int numThmsInBundle = TheoremGet.ContextRelationVecBundle.numThmsInBundle();
		String baseFileStr = TheoremGet.ContextRelationVecBundle.BASE_FILE_STR;
		int combinedVecsListSz = combinedVecsList.size();
		int numBundle = combinedVecsListSz / numThmsInBundle;
		//int remainder = combinedVecsListSz - numBundle * numThmsInBundle;
		
		for(int fileCounter = 0; fileCounter < numBundle; fileCounter++){			
			String path = baseFileStr + String.valueOf(vecsFileNameCounter);
			vecsFileNameCounter++;
			List<ContextRelationVecPair> curList = new ArrayList<ContextRelationVecPair>();
			int curBase = numThmsInBundle * fileCounter;
			for(int j = 0; j < numThmsInBundle; j++){
				curList.add(combinedVecsList.get(j+curBase));
			}
			FileUtils.serializeObjToFile(curList, path);
		}
		/*Add the remainder to combinedVecsList, to be serialized with subsequent thms*/
		//String path = baseFileStr + String.valueOf(vecsFileNameCounter);
		List<ContextRelationVecPair> curList = new ArrayList<ContextRelationVecPair>();
		for(int i = numBundle * numThmsInBundle; i < combinedVecsListSz; i++){
			curList.add(combinedVecsList.get(i));
		}
		//System.out.println("splitAndUpdateCombinedVecsList - curList.size() " + curList.size() + " numBundle: "+ numBundle); 
		combinedVecsList.clear();
		if(!curList.isEmpty()){
			combinedVecsList.addAll(curList);
			//FileUtils.serializeObjToFile(curList, path);
			//vecsFileNameCounter++;
		}
		return vecsFileNameCounter;
	}

	/**
	 * 
	 * @param combinedPEList List to be serialized.
	 * @param peFileNameCounter used to construct next file path.
	 */
	private static void serializeListsToFile(List<ThmHypPair> combinedPEList,
			//List<ContextRelationVecPair> combinedVecsList
			int peFileNameCounter) {
		String targetFilePath = ThmSearch.getSystemCombinedParsedExpressionListFilePathBase() 
				+ String.valueOf(peFileNameCounter);
		FileUtils.serializeObjToFile(combinedPEList, targetFilePath);	
		//targetFilePath = ThmSearch.getSystemCombinedContextRelationVecPairListFilePath();
		//FileUtils.serializeObjToFile(combinedPEList, targetFilePath);
	}
	
	/**
	 * Add Expr's to various lists combining the different serialized lists
	 * from individual tars.
	 * @param dirName name of directory for the files to be placed in. Ending file separator included.
	 * @param peFilePath
	 * @param combinedPEList
	 * @param vecsFilePath
	 * @param combinedVecsList
	 * @param wordThmIndexMMapPath
	 * @param combinedWordThmIndexMMap
	 * @param startingThmIndex
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private static int addExprsToLists(String dirPathStr, String peFilePath, List<ThmHypPair> combinedPEList, String vecsFilePath,
			List<ContextRelationVecPair> combinedVecsList, String wordThmIndexMMapPath, 
			Multimap<String, IndexPartPair> combinedWordThmIndexMMap, ListMultimap<String, LiteralSearchIndex> literalSearchIndexMap,
			List<String> allThmNameScrapeList, int startingThmIndex, StringBuilder nameDBSB,
			Multimap<String, String> paperIdNameDataMap) {
		
		List<ThmHypPair> thmHypPairList = (List<ThmHypPair>)FileUtils.deserializeListFromFile(peFilePath);
		int thmHypPairListSz = thmHypPairList.size();
		combinedPEList.addAll(thmHypPairList);

		//add scraped thm names.
		String thmNameScrapeDirPath = dirPathStr + DetectHypothesis.thmNameScrapeNameRoot + ".dat";
		if((new File(thmNameScrapeDirPath)).exists()) {
			List<String> thmNameScrapeList = (List<String>)FileUtils.deserializeListFromFile(thmNameScrapeDirPath);
			allThmNameScrapeList.addAll(thmNameScrapeList);			
		}
		
		for(int i = 0; i < thmHypPairListSz; i++) {
			ThmHypPair curPair = thmHypPairList.get(i);
			String thmStr = curPair.getEntireThmStr();
			int curThmIndex = startingThmIndex+i;
			LiteralSearch.addThmLiteralSearchIndexToMap(thmStr, curThmIndex, literalSearchIndexMap);
			String curPairPaperId = curPair.srcFileName();
			//String paperNameData = paperIdNameDataMap.get(curPairPaperId);
			Collection<String> paperNameDataCol = paperIdNameDataMap.get(curPairPaperId);
			if(paperNameDataCol.isEmpty()) {
				System.out.println("ProjectionnMatrix - Raw data file does not contain name data for "+curPairPaperId);
				continue;
			}
			//paperNameDataCol contains all authors for that paper.
			for(String paperNameData : paperNameDataCol) {
				nameDBSB.append(curThmIndex + "," + paperNameData + "\n");
			}
		}
		
		combinedVecsList.addAll((List<ContextRelationVecPair>)FileUtils.deserializeListFromFile(vecsFilePath));
		
		Multimap<String, IndexPartPair> wordThmIndexMMap = ((List<Multimap<String, IndexPartPair>>)
				FileUtils.deserializeListFromFile(wordThmIndexMMapPath)).get(0);
		/*****/
		//temporary ugly conversion code for data migration
		//temporary to account for inconsistency amongst different data files, Nov 29
		//Collection<IndexPartPair> indexPartPairCol = wordThmIndexMMap.get("group");
		Iterator<Map.Entry<String, IndexPartPair>> iter = wordThmIndexMMap.entries().iterator();
		//Iterator<IndexPartPair> iter = indexPartPairCol.iterator();
		if(iter.hasNext()) {
		try {
			Map.Entry<String, IndexPartPair> p1 = iter.next();
			//intentionally not used
			IndexPartPair pair = p1.getValue();
		}catch(java.lang.ClassCastException e) {
			
			Multimap<String, Integer> wordThmsIntMultimap = ((List<Multimap<String, Integer>>)
					FileUtils.deserializeListFromFile(wordThmIndexMMapPath)).get(0);
			wordThmIndexMMap = HashMultimap.create();
			for(Map.Entry<String, Integer> entry : wordThmsIntMultimap.entries()) {
				wordThmIndexMMap.put(entry.getKey(), new IndexPartPair(entry.getValue(), ThmPart.STM, new byte[] {}));
			}					
		}
		}
		/****/
		for(String keyWord : wordThmIndexMMap.keySet()){
			//update the indices of the thms with respect to the tar's position.
			Collection<IndexPartPair> updatedIndices = new ArrayList<IndexPartPair>();
			for(IndexPartPair indexPair : wordThmIndexMMap.get(keyWord)){				
				updatedIndices.add(new IndexPartPair(indexPair.thmIndex() + startingThmIndex, indexPair.thmPart()));				
			}
			combinedWordThmIndexMMap.putAll(keyWord, updatedIndices);
		}
		return startingThmIndex + thmHypPairListSz;
	}
	
	/**
	 * Apply projection matrices (dInverse and uTranspose) to given matrix (vectors should be row vecs).
	 * The relevan variables should already have been loaded in any kernel the EvaluationMedium is retrieved from.
	 * @param queryMxStrName mx to be applied (could be 1-D if a single query vec) to. This is List of 
	 * row vectors (List's).
	 * @param dInverseName
	 * @param uTransposeName
	 * @param corMxName
	 * @param projectedMxName
	 */
	protected static void applyProjectionMatrix(String queryMxStrName, String dInverseName, String uTransposeName, 
			String corMxName, String projectedMxName){
		
		WLEvaluationMedium medium = FileUtils.acquireWLEvaluationMedium();		
		String msg = "ProjectionMatrix.applyProjectionMatrix- Transposing and applying corMx...";
		logger.info(msg);
		String queryMxStrTransposeName = "queryMxStrNameTranspose";
		queryMxStrTransposeName = queryMxStrName;
		//try {
			//process query first with corMx. Convert to column vectors, so rows represent words.
		////////////
		/*applies projection mx. Need to Transpose from column vectors, so rows represent thms.*/		
		evaluateWLCommand(medium, 
				"If[Length["+queryMxStrName+"] > 0,"
				+ "q0 =" + queryMxStrTransposeName + "+ "+
				TermDocumentMatrix.COR_MX_SCALING_FACTOR+"*" + corMxName + "."+ queryMxStrTransposeName 
				+ ";" + projectedMxName + "= Transpose[" + dInverseName + "." + uTransposeName + ".q0] ,"
				+ projectedMxName + "={}]"
				, false, true);
			/*evaluateWLCommand(medium, "q0 =" + queryMxStrTransposeName + "+ "+
					TermDocumentMatrix.COR_MX_SCALING_FACTOR+"*" + corMxName + "."+ queryMxStrTransposeName, false, true);*/
			//ml.evaluate("q0 = " + queryMxStrTransposeName + "+ 0.08*" + corMxName + "."+ queryMxStrTransposeName +";");
			//ml.discardAnswer();
			//System.out.println("ProjectionMatrix.applyProjectionMatrix, "
				//	+evaluateWLCommand("(" + corMxName + "."+ queryMxStrTransposeName+")[[1]]", true, false));
			/*applies projection mx. Need to Transpose from column vectors, so rows represent thms.*/
			//******evaluateWLCommand(medium, projectedMxName + "= Transpose[" + dInverseName + "." + uTransposeName + ".q0]", false, true);
			//ml.evaluate(projectedMxName + "= Transpose[" + dInverseName + "." + uTransposeName + ".q0];");
			//ml.discardAnswer();
		/*} catch (MathLinkException e) {
			throw new IllegalStateException(e);
		}*/		
		FileUtils.releaseWLEvaluationMedium(medium);
	}
	
	/**
	 * Loads matrices in from mx files, concatenates them into 
	 * one Internal`Bag.
	 * @param projectedMxFilePathList List of .mx file names containing the thm vectors (term doc mx for each).
	 * e.g. "0208_001/0208/termDocumentMatrixSVD.mx".
	 * @param projectedMxContextPath path to context of projected mx. 
	 * @param projectedMxName name of projected matrix (as list of *row* vectors). Name is same for each .mx file.
	 * created from projecting full term-document matrices from full dimensional ones.
	 */
	public static void combineProjectedMx(List<String> projectedMxFilePathList, //String projectedMxContextPath,
			String projectedMxName, int mxFileIndex, String concatenatedListName){
		String msg = "ProjectionMatrix.combineProjectedMx- about to get paths from files.";
		System.out.println(msg);
		logger.info(msg);
		WLEvaluationMedium ml = FileUtils.acquireWLEvaluationMedium();

		/* Need to get overall length */
		evaluateWLCommand(ml, "lengthCount=0");
		int fileCounter = 1;
		for(String filePath : projectedMxFilePathList){			
			evaluateWLCommand(ml, "<<" +filePath);
			String ithMxName = projectedMxName + fileCounter;
			evaluateWLCommand(ml, ithMxName + "=" //+ projectedMxContextPath + "`" 
					+ projectedMxName + "; lengthCount += Length[" + ithMxName + "]");
			//evaluateWLCommand(ml, "lengthCount += Length[" + ithMxName + "]");
			fileCounter++;
		}
		//create bag with initial capacity.
		evaluateWLCommand(ml, "bag=Internal`Bag[Range[lengthCount]]; rangeCounter=0");
		//System.out.println("ProjectionMatrix, total lengthCount "+evaluateWLCommand("lengthCount", true, true));
		
		msg = "In combineProjectedMx(), Internal`Bag created.";
		System.out.println(msg);
		logger.info(msg);		
		for(int i = 1; i < fileCounter; i++){
			String ithName = projectedMxName+i;
			evaluateWLCommand(ml, "Internal`BagPart[bag, Range[rangeCounter+1, (rangeCounter+= Length["+ithName+"]) ]] ="
					+ ithName);
		}
		evaluateWLCommand(ml, concatenatedListName + "= Internal`BagPart[bag, All]");
		//System.out.println("ProjectionMatrix, concatenatedListName "+evaluateWLCommand(concatenatedListName, true, true));
		
		evaluateWLCommand(ml, "DumpSave[\"" + combinedMxRootPath  + mxFileIndex + ".mx\"," + concatenatedListName + "]");
		FileUtils.releaseWLEvaluationMedium(ml);
		
		msg = "In combineProjectedMx(), Done concatenating matrices!";
		System.out.println(msg);
		logger.info(msg);		
	}
	
}
