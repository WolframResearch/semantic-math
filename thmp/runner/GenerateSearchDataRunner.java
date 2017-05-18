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
import java.util.List;
import java.util.regex.Matcher;

import thmp.utils.FileUtils;
import thmp.utils.WordForms;

/**
 * Drives search data generation scripts.
 * 
 * @author yihed
 */
public class GenerateSearchDataRunner {

	
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
	
	private static void runScripts(List<String> fileNamesList) throws IOException, InterruptedException{
		
		for(String fileName : fileNamesList){			
			//ProcessBuilder pb = new ProcessBuilder("/home/usr0/yihed/thm/unpack2.sh", fileName);
			//File workingDir = new File("/home/usr0/yihed/thm");
			//pb.directory(workingDir);
			//pb.start();
			Runtime rt = Runtime.getRuntime();
			Process pr = rt.exec("/home/usr0/yihed/thm/unpack2.sh " + fileName);
			InputStream inputStream = pr.getInputStream();
			InputStreamReader inputStreamReader = new InputStreamReader(inputStream);	
			byte[] byteAr = new byte[1024];
			while(-1 != inputStream.read(byteAr) ){
				System.out.println(new String(byteAr, Charset.forName("UTF-8")));
			}
			pr.waitFor();
			
			FileUtils.silentClose(inputStreamReader);
			System.out.println("Should be done executing script");
		}
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
