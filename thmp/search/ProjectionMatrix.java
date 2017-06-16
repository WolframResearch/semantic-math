package thmp.search;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.wolfram.jlink.Expr;
import com.wolfram.jlink.KernelLink;
import com.wolfram.jlink.MathLinkException;

import thmp.parse.ParsedExpression;
import thmp.search.SearchCombined.ThmHypPair;
import thmp.search.Searcher.SearchConfiguration;
import thmp.search.Searcher.SearchMetaData;
import thmp.search.TheoremGet.ContextRelationVecPair;
import thmp.search.ThmSearch.TermDocumentMatrix;
import thmp.utils.FileUtils;
import thmp.utils.MathLinkUtils;
import thmp.utils.MathLinkUtils.WLEvaluationMedium;

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
	private static final String combinedMxRootPath = "src/thmp/data/" 
			+ TermDocumentMatrix.COMBINED_PROJECTED_TERM_DOCUMENT_MX_NAME; //+ ".mx";
	private static final int TAR_COUNT_PER_BUNDLE = 15;
	
	/**
	 * args is list of paths. 
	 * Supply list of paths (vararg) to directories, each contanining a parsedExpressionList and
	 * the *projected* term document matrices for a tar file.
	 * e.g. "0208_001Untarred/0208/", or "0304_001Untarred/0304"
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
		
		int loopTotal = argsLen / TAR_COUNT_PER_BUNDLE + 1;
		int vecsFileNameCounter = 0;
		//will go up to O(10^6) when all tars are included.
		int thmCounter = 0;
		//combined MMap from multiple tars.
		Multimap<String, Integer> combinedWordThmIndexMMap = ArrayListMultimap.create();
		//process TAR_COUNT_PER_BUNDLE each time. This loops over tar files.
		//Loops about 1569/15 ~ 105 times if all tar files supplied.
		for(int i = 0; i < loopTotal; i++){
			int start = i * TAR_COUNT_PER_BUNDLE;
			int nextIndex = (i+1)*TAR_COUNT_PER_BUNDLE;
			int end = nextIndex < argsLen ? nextIndex : argsLen;
			//int end = i < loopTotal-1 ? (i+1)*TAR_COUNT_PER_BUNDLE : argsLen;
			if(end > start){
				for(int j = start; j < end; j++){
					//be sure to check it's valid path to valid .mx
					String fileName = args[j];
					if(!(new File(fileName)).exists()){
						continue;
					}
					String path_i = FileUtils.addIfAbsentTrailingSlashToPath(fileName);
					projectedMxFilePathList.add(path_i + ThmSearch.TermDocumentMatrix.PROJECTED_MX_NAME + ".mx");	
					String peFilePath = path_i + ThmSearch.TermDocumentMatrix.PARSEDEXPRESSION_LIST_FILE_NAME_ROOT;
					String vecsFilePath = path_i + "vecs/" + ThmSearch.TermDocumentMatrix.CONTEXT_VEC_PAIR_LIST_FILE_NAME;
					String wordThmIndexMMapPath = path_i + SearchMetaData.wordThmIndexMMapSerialFileName();
					thmCounter = addExprsToLists(peFilePath, combinedPEList, vecsFilePath, combinedVecsList, wordThmIndexMMapPath,
							combinedWordThmIndexMMap, thmCounter
							);
				}				
				bundleStartThmIndexList.add(thmCounter);
				//thmCounter += combinedPEList.size();
				
				serializeListsToFile(combinedPEList, i);
				combinedPEList = new ArrayList<ThmHypPair>();
				///return the counter
				vecsFileNameCounter = splitAndUpdateCombinedVecsList(combinedVecsList, vecsFileNameCounter);
				//without the trailing ".mx"
				String combinedProjectedTDMxName = TermDocumentMatrix.COMBINED_PROJECTED_TERM_DOCUMENT_MX_NAME;
				//String contextPath, String vMxName, String concatenatedListName
				combineProjectedMx(projectedMxFilePathList, //TermDocumentMatrix.PROJECTED_MX_CONTEXT_NAME, 
						TermDocumentMatrix.PROJECTED_MX_NAME, i, combinedProjectedTDMxName);
				projectedMxFilePathList = new ArrayList<String>();
			}
		}
		//serialize the remaining
		if(!combinedVecsList.isEmpty()){
			//Name deliberately does not contain ".dat" at end.
			String path = TheoremGet.ContextRelationVecBundle.BASE_FILE_STR 
					+ String.valueOf(vecsFileNameCounter);
			FileUtils.serializeObjToFile(combinedVecsList, path);
		}		
		Searcher.SearchConfiguration searchConfig = new Searcher.SearchConfiguration(bundleStartThmIndexList);
		List<Searcher.SearchConfiguration> searchConfigList = new ArrayList<Searcher.SearchConfiguration>();
		searchConfigList.add(searchConfig);
		FileUtils.serializeObjToFile(searchConfigList, SearchConfiguration.searchConfigurationSerialPath());
		
		//serialize map into one file, to be loaded at once in memory at runtime.
		//should be ~240 MB.
	 	String wordThmIndexMMapPath = FileUtils.getServletPath(SearchMetaData.wordThmIndexMMapSerialFilePath());
	 	List<Multimap<String, Integer>> combinedWordThmIndexMMapList = new ArrayList<Multimap<String, Integer>>();
	 	combinedWordThmIndexMMapList.add(combinedWordThmIndexMMap);	 	
	 	FileUtils.serializeObjToFile(combinedWordThmIndexMMapList, wordThmIndexMMapPath);
		/* Combine parsedExpressionList's */
		//combineParsedExpressionList(parsedExpressionFilePathList);
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
		/*Serialize the remainder*/
		//String path = baseFileStr + String.valueOf(vecsFileNameCounter);
		List<ContextRelationVecPair> curList = new ArrayList<ContextRelationVecPair>();
		for(int i = numBundle * numThmsInBundle; i < combinedVecsListSz; i++){
				curList.add(combinedVecsList.get(i));
		}
		combinedVecsList = new ArrayList<ContextRelationVecPair>();
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

	
	@SuppressWarnings("unchecked")
	private static int addExprsToLists(String peFilePath, List<ThmHypPair> combinedPEList, String vecsFilePath,
			List<ContextRelationVecPair> combinedVecsList, String wordThmIndexMMapPath, 
			Multimap<String, Integer> combinedWordThmIndexMMap, 
			int startingThmIndex) {
		List<ThmHypPair> thmHypPairList = (List<ThmHypPair>)FileUtils.deserializeListFromFile(peFilePath);
		int thmHypPairListSz = thmHypPairList.size();
		combinedPEList.addAll(thmHypPairList);	
		combinedVecsList.addAll((List<ContextRelationVecPair>)FileUtils.deserializeListFromFile(vecsFilePath));
		Multimap<String, Integer> wordThmIndexMMap = ((List<Multimap<String, Integer>>)
				FileUtils.deserializeListFromFile(wordThmIndexMMapPath)).get(0);
		
		for(String keyWord : wordThmIndexMMap.keySet()){			
			//update the indices of the thms with respect to the tar's position.
			Collection<Integer> updatedIndices = new ArrayList<Integer>();
			for(int index : wordThmIndexMMap.get(keyWord)){
				updatedIndices.add(index + startingThmIndex);
			}
			combinedWordThmIndexMMap.putAll(keyWord, updatedIndices);
		}
		return startingThmIndex + thmHypPairListSz;
	}

	/*private static void combineParsedExpressionList(List<String> parsedExpressionFilePathList) {
		List<ParsedExpression> combinedPEList = new ArrayList<ParsedExpression>();
		for(String path : parsedExpressionFilePathList){
			@SuppressWarnings("unchecked")
			List<ParsedExpression> peList = (List<ParsedExpression>)FileUtils.deserializeListFromFile(path);
			combinedPEList.addAll(peList);
		}
		String targetFilePath = ThmSearch.getSystemCombinedParsedExpressionListFilePath();
		FileUtils.serializeObjToFile(combinedPEList, targetFilePath);
	}*/

	/**
	 * Apply projection matrices (dInverse and uTranspose) to given matrix (vectors should be row vecs).
	 * @param queryMxStrName mx to be applied, could be 1-D. List of row vectors (List's).
	 * @param dInverseName
	 * @param uTransposeName
	 * @param corMxName
	 * @param projectedMxName
	 */
	public static void applyProjectionMatrix(String queryMxStrName, String dInverseName, String uTransposeName, 
			String corMxName, String projectedMxName){
		
		WLEvaluationMedium medium = FileUtils.acquireWLEvaluationMedium();		
		String msg = "ProjectionMatrix.applyProjectionMatrix- Transposing and applying corMx...";
		logger.info(msg);
		String queryMxStrTransposeName = "queryMxStrNameTranspose";
		queryMxStrTransposeName = queryMxStrName;
		//try {
			//process query first with corMx. Convert to column vectors, so rows represent words.
			evaluateWLCommand(medium, "q0 =" + queryMxStrTransposeName + "+ 0.08*" + corMxName + "."+ queryMxStrTransposeName, false, true);
			//ml.evaluate("q0 = " + queryMxStrTransposeName + "+ 0.08*" + corMxName + "."+ queryMxStrTransposeName +";");
			//ml.discardAnswer();
			//System.out.println("ProjectionMatrix.applyProjectionMatrix, "
				//	+evaluateWLCommand("(" + corMxName + "."+ queryMxStrTransposeName+")[[1]]", true, false));
			//applies projection mx with given vectors. Need to Transpose from column vectors, so rows represent thms.
			evaluateWLCommand(medium, projectedMxName + "= Transpose[" + dInverseName + "." + uTransposeName + ".q0]", false, true);
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
	 * @param  List of .mx file names containing the thm vectors (term doc mx for each).
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
