package thmp.runner;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import thmp.parse.DetectHypothesis;
import thmp.search.SearchIntersection;
import thmp.search.Searcher;
import thmp.search.Searcher.SearchMetaData;
import thmp.search.ThmSearch.TermDocumentMatrix;
import thmp.utils.FileUtils;
import thmp.utils.WordForms;

/**
 * Drives search data generation scripts.
 * 
 * @author yihed
 */
public class GenerateSearchDataRunner {

	private static final Logger logger = LogManager.getLogger(SearchIntersection.class);
	private static final double numMiliSecPerHour = 3600000;
	private static final String UNPACK_SCRIPT_FILE_PATH = "/home/usr0/yihed/thm/unpack2.sh ";
	private static final Set<String> FILES_TO_KEEP;
	private static final Pattern FILES_TO_KEEP_REGEX = Pattern.compile(".+\\.(?:mx|ecs|dat|txt|stamp|List)");
	private static final Pattern CONFIG_COMMENT_PATT = Pattern.compile("^\\s*#.*");
	//setting e.g. "msc:true"
	private static final Pattern CONFIG_PATT = Pattern.compile("([^:]+):(.+)");
	
	private static final Pattern TRUE_PATT = Pattern.compile("\\s*(?i)true\\s*");
	private static final Pattern FALSE_PATT = Pattern.compile("\\s*(?i)false\\s*");
	
	static {
		FILES_TO_KEEP = new HashSet<String>();
		String[] a = new String[] {SearchMetaData.wordThmIndexMMapSerialFileName(),
				"wordThmIndexMMap.txt", TermDocumentMatrix.FULL_TERM_DOCUMENT_MX_NAME+".mx",
				};
		for(String fileName : a) {
			FILES_TO_KEEP.add(fileName);
		}
	}
	
	private static class SearchDataRunnerConfigBuilder{
		//whether currently generating msc words data
		private boolean msc;
		//whether to regenerate Mx files, creating TermDocumentMatrix, etc,
		//anew, or 
		private boolean regenerateMxFiles;
		
		/**
		 * @param configFilePath Must be guaranteed to be a file path by contract.
		 */
		SearchDataRunnerConfigBuilder(String configFilePath){
			List<String> lines = FileUtils.readLinesFromFile(configFilePath);
			for(String line : lines) {
				if(CONFIG_COMMENT_PATT.matcher(line).matches()) {
					continue;
				}
				Matcher m;
				if((m=CONFIG_PATT.matcher(line)).matches()) {
					String option = m.group(1);
					String value = m.group(2);
					parseConfigStr(option, value);
				}
			}
		}
		
		void parseConfigStr(String option, String val) {
			switch(option) {
				case "msc":
					this.msc = getBooleanVal(val);
				case "regenerateMx":
					this.regenerateMxFiles = getBooleanVal(val);
			}
		}
		
		boolean getBooleanVal(String boolStr) {
			if(TRUE_PATT.matcher(boolStr).matches()) {
				return true;
			}
			return false;
		}
		
		SearchDataRunnerConfig build() {
			return new SearchDataRunnerConfig(this);
		}
		
	}
	
	public static class SearchDataRunnerConfig{
		//whether currently generating msc words data
		private boolean msc;
		//whether to regenerate Mx files, creating TermDocumentMatrix, etc,
		//anew, or 
		private boolean regenerateMxFiles;
		public static final SearchDataRunnerConfig DEFAULT_CONFIG = new SearchDataRunnerConfig();
		
		private SearchDataRunnerConfig() {			
		}
		
		private SearchDataRunnerConfig(SearchDataRunnerConfigBuilder builder){
			this.msc = builder.msc;
			this.regenerateMxFiles = builder.regenerateMxFiles;
		}

		public boolean msc(){
			return this.msc;
		}
		
		public boolean regenerateMx(){
			return this.regenerateMxFiles;
		}
	}
	
	/**
	 * At least one arg required, for path to tar, optional arg to specify whether to 
	 * collect msc data, with "msc" option
	 * @param args
	 * @throws FileNotFoundException
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public static void main(String[] args) throws FileNotFoundException, IOException, InterruptedException{
		int argsLen = args.length;
		//read in location of source file
		if(0 == argsLen){
			System.out.println("Please supply a file to read data sources from!");
			return;
		}
		//filenames should be absolute paths
		List<String> fileNamesList = extractNamesFromFile(args[0]);
		System.out.println("GenerateSearchDataRunner-fileNamesList: " + fileNamesList);
		
		SearchDataRunnerConfig runnerConfig = SearchDataRunnerConfig.DEFAULT_CONFIG;
		
		if(2 == argsLen) {
			//second arg should be path to config file
			String configFilePath = args[1];
			
			File file = new File(configFilePath);
			if(!file.isFile()) {
				System.out.println("If supplied, 2nd argumnet must be path to configuration file!");
				return;
			}
			SearchDataRunnerConfigBuilder builder = new SearchDataRunnerConfigBuilder(configFilePath);
			runnerConfig = builder.build();		
		}
		
		runScripts(fileNamesList, runnerConfig);
		
		/*if(2 == argsLen && args[1].toLowerCase().equals("msc")) {			
			runScripts(fileNamesList, true);
		}else {
			runScripts(fileNamesList);			
		}*/
	}

	/**
	 * A fileName refers to name (including path) of tar file, 
	 * e.g. /prospectus/crawling/_arxiv/src/arXiv_src_0308_001.tar
	 * @param fileNamesList
	 * @throws IOException
	 * @throws InterruptedException
	 */
	/*private static void runScripts(List<String> fileNamesList) throws IOException, InterruptedException{
		runScripts(fileNamesList, false);
	}*/
	
	/**
	 * A fileName refers to name (including path) of tar file, 
	 * e.g. /prospectus/crawling/_arxiv/src/arXiv_src_0308_001.tar
	 * @param fileNamesList
	 * @param gatherMscData Whether to gather data for msc classifier.
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private static void runScripts(List<String> fileNamesList, SearchDataRunnerConfig runnerConfig) 
			throws IOException, InterruptedException{
		
		long beforeTime = System.currentTimeMillis();
		
		boolean gatherMscData = runnerConfig.msc;
		//already created, and edited that map. Oct 25 2017
		if(false && gatherMscData) {
			CreateMscVecs.wordsScoreMapToJson();
		}
		
		for(String fileName : fileNamesList){		
			
			//Want to create, first argument to generateSearchData takes form e.g. 0208_001Untarred/0208
			/* A fileName takes the form e.g. /prospectus/crawling/_arxiv/src/arXiv_src_1412_017.tar.*/
			int fileNameLen = fileName.length();
			if(fileNameLen < 12){
				logger.error("filename length should not be smaller than 12.");
				continue;
			}
			String dirNameRoot = fileName.substring(fileNameLen-12, fileNameLen-4);
			
			String fileDir;
			
			/*the script name must coincide with that in both bash scripts. 
			 * E.g. UNPACK_SCRIPT_FILE_PATH	*/	
			if(!gatherMscData) {
				fileDir = dirNameRoot + "Untarred" + File.separator 
						+ fileName.substring(fileNameLen-12, fileNameLen-8);
			}else {
				fileDir = dirNameRoot + "mscUntarred" + File.separator 
						+ fileName.substring(fileNameLen-12, fileNameLen-8);
			}
			
			//skip untar'ing if directory already exists
			//don't skip! in case the files had been deleted
			//if(!(new File(fileDir)).exists()){
				Runtime rt = Runtime.getRuntime();
				/*script that unpacks the file, deletes non-tex files, and create map of files that 
				 * should be processed. */
				Process pr = rt.exec(UNPACK_SCRIPT_FILE_PATH + fileName);			
				FileUtils.waitAndPrintProcess(pr);
				/*the script name must coincide with that in both bash scripts.	*/	
				System.out.println("Done unpacking file " + fileName + ". Starting to generate search data");
			//}
			if(!gatherMscData) {
				DetectHypothesis.Runner.generateSearchData(new String[]{fileDir, 
						TermDocumentMatrix.DATA_ROOT_DIR_SLASH + TermDocumentMatrix.PROJECTION_MX_FILE_NAME,
						Searcher.SearchMetaData.wordDocFreqMapPath()
					}, runnerConfig);
			}else {
				//create msc classifier data.
				CreateMscVecs.processFilesInTar(fileDir);				
			}
			
			try {
				//delete files to save disk space, but that would remove data that can be useful later <--
				//just need to untar again if needed.
				File[] fileDirFileAr = new File(fileDir).listFiles();
				if(null != fileDirFileAr) {
					for(File file : fileDirFileAr) {
						//delete files except designated file names.
						if(!FILES_TO_KEEP_REGEX.matcher(file.getName()).matches()) {
							if(file.isDirectory()) {
								//specify full path, since have own FileUtils class.
								org.apache.commons.io.FileUtils.deleteDirectory(file);							
							}else {
								file.delete();
							}
						}					
					}
				}
			}catch(Exception e) {
				//catch exception, so to not interrupt data processing, since this is clean up phase.
				logger.error("Exception while deleting files for " + fileName + e.getMessage());				
			}
			
			String timeStr = new SimpleDateFormat("yyyy_MM_dd_HH:mm").format(Calendar.getInstance().getTime());
			System.out.println("Done generating search data for files in " + fileDir + " at " + timeStr);		
		}
		
		long afterTime = System.currentTimeMillis();
		System.out.println("time it took to evalute data for "+ fileNamesList +" files:" + (afterTime-beforeTime)/numMiliSecPerHour + " hours");
	}

	/**
	 * Returns list of files 
	 * @param fileName
	 * @return
	 * @throws FileNotFoundException
	 */
	private static List<String> extractNamesFromFile(String fileName) throws FileNotFoundException{
		FileInputStream fileInputStream = new FileInputStream(fileName);
		BufferedReader br = new BufferedReader(new InputStreamReader(fileInputStream));
		List<String> list = new ArrayList<String>();
		String line;
		try{
			while(null != (line = br.readLine())){
				Matcher matcher = WordForms.getWhiteEmptySpacePattern().matcher(line);
				if(matcher.matches()){
					continue;
				}
				matcher = WordForms.SPACES_AROUND_TEXT_PATTERN().matcher(line);
				if(matcher.matches()){
					line = matcher.group(1);				
				}
				list.add(line);			
			}
		}catch(IOException e){
			//throw since would defeat purpose if can't read file.
			throw new IllegalStateException(e);
		}finally{
			FileUtils.silentClose(br);
		}
		return list;
	}
	
}
