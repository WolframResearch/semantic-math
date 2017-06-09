package thmp.crawl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import thmp.parse.DetectHypothesis;
import thmp.utils.FileUtils;

/**
 * Preprocesses zip file directory, e.g. delete non-math .gz files.
 * First creates Set of .gz files whose names are of the form e.g. 0704.0001.gz,
 * but which are math .gz files.
 * @author yihed
 *
 */
public class PreprocessZipfileDir {

	private static final Set<String> mathGzFileSet = new HashSet<String>();
	private static final Pattern GZ_FILE_PATTERN = Pattern.compile("(.+)\\.gz");
	private static final Pattern DIGITS_FILENAME_PATTERN = Pattern.compile("\\d{4}\\.\\d{4}");
	private static final Logger logger = LogManager.getLogger(PreprocessZipfileDir.class);

	static{
		createMathGzFileSet(mathGzFileSet);
	}
	/**
	 * Fille mathGzFileSet with list of math filenames of the form 1234.5678 
	 * (without concluding .gz).
	 * @param fileSet
	 */
	private static void createMathGzFileSet(Set<String> fileSet){
		//
		final String mathGzFileKeys = "src/thmp/data/mathFileKeys.txt";
		try{
			InputStream fileInputStream = null;
			BufferedReader bReader = null;
			try{
				fileInputStream = new FileInputStream(mathGzFileKeys);
				bReader = new BufferedReader(new InputStreamReader(fileInputStream));
				String line;
				while(null != (line = bReader.readLine())){
					fileSet.add(line);
				}
			}finally{
				FileUtils.silentClose(bReader);
				FileUtils.silentClose(fileInputStream);
			}
		}catch(IOException e){
			throw new IllegalStateException(e);
		}
		
	}
	
	/**
	 * args[0] contains *relative* path to the directory to be cleaned, so dir with all .gz files.
	 * Execution is currently in the parent directory, containing all .tar files.
	 */
	public static void main(String[] args){
		
		//delete non math .gz files, since unpacked files don't contain 'math' in their names,
		//even if they are math files.
		if(args.length == 0){
			System.out.println("Supply a directory of gzip files!");
			return;
		}
		//create absolute path from relative path.
		//srcBasePath contains all the .gz files.
		String srcBasePath = System.getProperty("user.dir") + "/" + args[0];
		System.out.println("PreprocessZipFile - file dir: " + System.getProperty("user.dir") + "/" + args[0]);
		
		//get file names
		File dir = new File(srcBasePath);
		if (!dir.isDirectory()) {
			String msg = "Source directory" + srcBasePath + " is not a directory!";
			System.out.println(msg);
			throw new IllegalStateException(msg);
		}
		File[] files = dir.listFiles();		
		for(File file : files){
			String fileName = file.getName();
			if(shouldDeleteFile(fileName)){
				try{
					Files.delete(file.toPath());
				}catch(IOException e){
					String msg = "IOException while deleting file!";
					System.out.println(msg);
					logger.error(msg);
				}
			}
		}
	}
	/**
	 * Whether the .gz file with filename should be deleted, ie not 
	 * a math or math physics etc file.
	 * @param fileName e.g. "math33256.gz", or 0704.0001.gz
	 * @return
	 */
	private static boolean shouldDeleteFile(String fileName){
		//the filename root before the ".gz" part.
		String fileNameRoot = fileName;
		Matcher matcher;
		if((matcher=GZ_FILE_PATTERN.matcher(fileName)).matches()){
			fileNameRoot = matcher.group(1);
		}else{
			//e.g. .pdf file.
			return true;
		}
		
		if(Pattern.compile("math.+").matcher(fileNameRoot).matches() 
				|| (DIGITS_FILENAME_PATTERN.matcher(fileNameRoot).matches() && mathGzFileSet.contains(fileNameRoot))){
			return false;
		}
		return true;
	}
}
