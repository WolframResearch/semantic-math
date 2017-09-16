package thmp.runner;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
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
	private static final Pattern FILES_TO_KEEP_REGEX = Pattern.compile(".+(?:\\.mx|ecs|dat|txt|stamp|List)");
	
	static {
		FILES_TO_KEEP = new HashSet<String>();
		String[] a = new String[] {SearchMetaData.wordThmIndexMMapSerialFileName(),
				"wordThmIndexMMap.txt", TermDocumentMatrix.FULL_TERM_DOCUMENT_MX_NAME+".mx",
				};
		for(String fileName : a) {
			FILES_TO_KEEP.add(fileName);
		}
	}
	
	public static void main(String[] args) throws FileNotFoundException, IOException, InterruptedException{
		//read in location of source file
		if(0 == args.length){
			System.out.println("Please supply a file to read data sources from!");
			return;
		}
		//filenames should be absolute paths
		List<String> fileNamesList = extractNamesFromFile(args[0]);
		System.out.println("GenerateSearchDataRunner-fileNamesList: " + fileNamesList);
		runScripts(fileNamesList);
	}
	
	/**
	 * A fileName refers to name (including path) of tar file, 
	 * e.g. /prospectus/crawling/_arxiv/src/arXiv_src_0308_001.tar
	 * @param fileNamesList
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private static void runScripts(List<String> fileNamesList) throws IOException, InterruptedException{
		
		long beforeTime = System.currentTimeMillis();
		for(String fileName : fileNamesList){			
			//ProcessBuilder pb = new ProcessBuilder("/home/usr0/yihed/thm/unpack2.sh", fileName);
			//File workingDir = new File("/home/usr0/yihed/thm");
			//pb.directory(workingDir);
			//pb.start();
			
			//first argument to generateSearchData takes form e.g. 0208_001Untarred/0208
			//a fileName takes the form e.g. /prospectus/crawling/_arxiv/src/arXiv_src_1412_017.tar.
			int fileNameLen = fileName.length();
			if(fileNameLen < 12){
				logger.error("filename length should not be smaller than 12.");
				continue;
			}
			String dirNameRoot = fileName.substring(fileNameLen-12, fileNameLen-4);
			/*the script name must coincide with that in both bash scripts.	*/	
			String fileDir;
			final boolean scrapeThmName = FileUtils.SCRAPE_THM_NAME_Q;
			if(scrapeThmName) {
				fileDir = dirNameRoot + "Untarred1" + File.separator 
						+ fileName.substring(fileNameLen-12, fileNameLen-8);
			}else {
				fileDir = dirNameRoot + "Untarred" + File.separator 
						+ fileName.substring(fileNameLen-12, fileNameLen-8);
			}
			
			//skip untar'ing if directory already exists
			if(!(new File(fileDir)).exists()){
				Runtime rt = Runtime.getRuntime();
				Process pr = rt.exec(UNPACK_SCRIPT_FILE_PATH + fileName);			
				FileUtils.waitAndPrintProcess(pr);
				/*the script name must coincide with that in both bash scripts.	*/	
				System.out.println("Done unpacking file " + fileName + ". Starting to generate search data");
			}
			
			DetectHypothesis.Runner.generateSearchData(new String[]{fileDir, 
					TermDocumentMatrix.DATA_ROOT_DIR_SLASH + TermDocumentMatrix.PROJECTION_MX_FILE_NAME,
					Searcher.SearchMetaData.wordDocFreqMapPath()
					//"src/thmp/data/termDocumentMatrixSVD.mx", "src/thmp/data/allThmWordsMap.dat"
					});
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
			
			/*Runtime rt = Runtime.getRuntime();
			Process pr = rt.exec("/home/usr0/yihed/thm/unpack2.sh " + fileName);			
			FileUtils.waitAndPrintProcess(pr);			
			FileUtils.findFilePathDirectory(fileDir);*/
			System.out.println("Done generating search data for files in " + fileDir);			
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
