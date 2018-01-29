package thmp.runner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;

import thmp.parse.TheoremContainer;
import thmp.runner.GenerateSearchDataRunner.SearchDataRunnerConfig;
import thmp.search.CollectThm;
import thmp.search.Searcher;
import thmp.search.TriggerMathThm2;
import thmp.utils.FileUtils;

/**
 * Creates vectors for classifying MSC classes.
 * @author yihed
 */
public class CreateMscVecs {

	private static final String wordsScoreMapPath = "src/thmp/data/wordsScoreMap.json";
	//put such a file in each tar directory.
	private static final String mscTermsFileName = "mscTermsJan27.txt";
	//Map of paperId and classifications.
	private static final Map<String, String> paperIdMscMap = new HashMap<String, String>();
	private static final Pattern MSC_LINE_PATT = Pattern.compile("([^,]+),(.+)");
	private static final Pattern COMMA_PATT = Pattern.compile(",");
	
	
	static {
		
		String paperIdMscPath = "src/thmp/data/mscDataString1.txt";
		List<String> mscLines = FileUtils.readLinesFromFile(paperIdMscPath);
		//each line has form "0704.0005,42B30,42B35"
		for(String line : mscLines) {
			Matcher m = MSC_LINE_PATT.matcher(line);
			if(!m.matches()) {
				continue;
			}
			//String[] lineAr = MSC_LINE_PATT.split(line);
			//int lineArLen = lineAr.length;
			String paperId = m.group(1);
			String mscStr = m.group(2);
			
			String[] mscStrAr = COMMA_PATT.split(mscStr);
			int arLen = mscStrAr.length;
			
			StringBuilder mscSb = new StringBuilder(30);
			
			//some have for, q-alg9610016,05Exx,33Cxx
			//q-alg9610017,05Exx,33-xx, or q-alg9701036,17B
			
			for(int i = 0; i < arLen; i++) {
				String curMsc = mscStrAr[i];
				//some are e.g. "(Primary)", 
				//q-alg9706016,17B70,(Primary),17B37,17B35,(Secondary)
				if(curMsc.contains("(")) {
					continue;
				}
				mscSb.append(curMsc).append(",");
			}
			if(mscSb.length() == 0) {
				continue;
			}
			paperIdMscMap.put(paperId, mscSb.substring(0, mscSb.length()-1));				
		}		
	}
	
	public static class Paper implements TheoremContainer{
	
		private String paper;
		
		public Paper(String paper_) {
			this.paper = paper_;
		}
		
		@Override
		public String getEntireThmStr() {
			return this.paper;
		}
	}
	
	public static void main(String[] args) {	
		
	}
	
	/**
	 * 
	 * @param dirName Has form e.g. "0208_001Untarred/0208".
	 * And for msc directory processing, it's "0208_001mscUntarred/0208"
	 */
	public static void processFilesInTar(String dirName, SearchDataRunnerConfig runnerConfig) {
		
		dirName = FileUtils.addIfAbsentTrailingSlashToPath(dirName);
		
		System.out.println("CreateMscVecs - dirName " + dirName);
		String texFileNamesSerialFileStr = dirName + Searcher.SearchMetaData.texFilesSerializedListFileName;
		
		//these two maps are sync'd
		List<Paper> paperList = new ArrayList<Paper>();
		List<String> paperIdList = new ArrayList<String>();
		//map of paperId's and all absolute paths of tex files associated to that paper.
		Multimap<String, String> paperIdPathMMap = ArrayListMultimap.create();
		
		@SuppressWarnings("unchecked")
		Map<String, String> texFileNamesMap = 
			((List<Map<String, String>>)FileUtils.deserializeListFromFile(texFileNamesSerialFileStr)).get(0);
		for(Map.Entry<String, String> fileNameEntry : texFileNamesMap.entrySet()){
			/*each entry has form e.g. /home/usr0/yihed/thm/0201_001Untarred/0201/math0201320/llncs.cls=math0201320,*/
			//check against names entry of files 
			String paperId = fileNameEntry.getValue();
			String mscStr = paperIdMscMap.get(paperId);
			
			if(null == mscStr) {
				continue;
			}			
			paperIdPathMMap.put(paperId, fileNameEntry.getKey());				
		}
		
		StringBuilder funNameSb = new StringBuilder(500000);			
		for(String paperId : paperIdPathMMap.keySet()) {
				
			Collection<String> pathCol = paperIdPathMMap.get(paperId);
			StringBuilder paperSb = new StringBuilder(5000);
				
			for(String path : pathCol) {
					//each path is absolute path
				paperSb.append(FileUtils.readStrFromFile(path)).append("\n");
			}
				
			String paperStr = paperSb.toString();
			
			if(runnerConfig.generateFuncName()) {
				ScrapeFunctionName.scrapeFuncNames(paperStr, funNameSb);
			}
			Paper paper = new Paper(paperStr);
			paperList.add(paper);
			paperIdList.add(paperId);
		}
		if(runnerConfig.generateFuncName()) {
			//this should be separated into own class!!!
				//file to hold function names for Michael
			String funcFileName = dirName + "funcNames.txt";
			FileUtils.writeToFile(funNameSb.toString(), funcFileName);			
			funNameSb = null;
		}
		
		processTarDir(dirName, paperList, paperIdList);		
	}
	
	/**
	 * Process a single directory corresponding to a tar.
	 * @param tarDirPath path to the tar directory to be processed, 
	 * e.g. 0208_001Untarred/0208/.
	 * @param paperList list of Strings of entire papers
	 * @param paperIdList List of paperId's. Sync'd with paperList.
	 */
	private static void processTarDir(String tarDirPath, List<Paper> paperList,
			List<String> paperIdList) {
		
		int paperListSz = paperList.size();
		
		assert paperListSz == paperIdList.size();
		
		StringBuilder termSb = buildWordFreqDataStr(paperList, paperIdList, paperListSz);
		//remove trailing newline
		int termSbLen = termSb.length();
		if(termSbLen > 0) {
			termSb.deleteCharAt(termSbLen-1);
			//each tar should have own file in its directory
			//String termsFileName = "mscTerms.txt";
			String termsFilePath = tarDirPath + mscTermsFileName;
			FileUtils.writeToFile(termSb, termsFilePath);
		}
		
		/*
		 * Further Processing on command line: 
		 * Need to index by paper, keep counter, need final data with paper id, the actual words that occur.
		 * Create both, map for paper and words, and map for paper and indices.
		 * Also needs words-score map, to json format.
		
		 * Can check against map e.g. 0704.0005,42B30,42B35 at runtime to form map.		
		 * can cat at the end to combine multiple files: cat file1 file2 ... > mscCombined.txt		 
		 */
	}
	
	public static StringBuilder buildWordFreqDataStr(List<Paper> paperList) {
		int paperListSz = paperList.size();
		return buildWordFreqDataStr(paperList, null, paperListSz);
	}
	
	private static StringBuilder buildWordFreqDataStr(List<Paper> paperList, List<String> paperIdList,
			int paperListSz) {
		
		List<int[]> coordinatesList = new ArrayList<int[]>();
		List<Double> weightsList = new ArrayList<Double>();
		
		//list of papers on one tar.
		
		//gather set of papers in one tar.		
		/*for(String paperPath : mscPaperPathList) {
			//each path is absolute path
			String paperStr = FileUtils.readStrFromFile(paperPath);
			Paper paper = new Paper(paperStr);
			paperList.add(paper);
		}*/
		
		//the coordinates list contains arrays of size 2, [keyWordIndex, thmCounter], need to increment
		//thmCounter by current running counter for all tars. Need
		List<Multiset<String>> allTermsList = TriggerMathThm2.gatherTermDocumentMxEntries(paperList, coordinatesList, weightsList);
		
		StringBuilder termSb = new StringBuilder(50000);
		
		//create msc data string, e.g. "1504.01535;word1,freq1;word2,freq2; ... ; \n05C,35Q,"
		for(int i = 0; i < paperListSz; i++) {
			
			Multiset<String> paperTermsMSet = allTermsList.get(i);
			if(paperTermsMSet.isEmpty()) {
				continue;
			}
			
			String paperId = null;
			if(null != paperIdList) {
				paperId = paperIdList.get(i);
				termSb.append(paperId);
			}
			//the first semicolon still ok, will get StringSplit' away
			for(String word : paperTermsMSet.elementSet()) {
				//append each term
				termSb.append(";").append(word)
				.append(",").append(paperTermsMSet.count(word));
			}
			//lines have form id, terms \n msc class string
			if(null != paperId) {
				termSb.append("\n").append(paperIdMscMap.get(paperId)).append("\n");		
			}
		}
		return termSb;
	}
	
	/**
	 * Serializes wordsScore map to Json file.
	 */
	public static void wordsScoreMapToJson() {
		
		Map<String, Integer> wordsScoreMap = CollectThm.ThmWordsMaps.get_wordsScoreMap();		
				//create JSON from wordsScoreMap,
				StringBuilder wordsScoreSb = new StringBuilder(30000);
				wordsScoreSb.append("{");
				for(Map.Entry<String, Integer> wordsScorePair : wordsScoreMap.entrySet()) {
							
							wordsScoreSb.append("\"" + wordsScorePair.getKey() + "\" : " + wordsScorePair.getValue() + ",");
				}
				wordsScoreSb.append("}");
						
						//String wordsScoreMapPath = "src/thmp/data/wordsScoreMap.json";
				FileUtils.writeToFile(wordsScoreSb, wordsScoreMapPath);
	}
}
