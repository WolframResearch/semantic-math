package thmp.crawl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import thmp.utils.FileUtils;

/**
 * Extract content from .tex files and put it to a .txt file
 * @author yihed
 */
public class TexToTxt {

	private static final String SRC_DIR = "src/thmp/data/functional_analysis_operator_algebras";
	private static final String TXT_File = "src/thmp/data/functional_analysis_operator_algebras/functionalAnalysis.txt";
	
	public static void main(String[] args){	
		
		//list to be written to txt file
		List<String> texContentList = new ArrayList<String>();
		
		File dir = new File(SRC_DIR);
		File[] fileList = dir.listFiles();
		if(fileList == null){
			System.out.println("Not a directory!");
			return;
		}
		//get all files that end in .tex
		for(int i = 0; i < fileList.length; i++){			
			try {
				File file = fileList[i];
				if(!file.getName().matches("[^.]*\\.tex")) continue;
				FileReader fileReader = new FileReader(file);
				BufferedReader fileBufferedReader = new BufferedReader(fileReader);
				String line;
				while((line = fileBufferedReader.readLine()) != null){
					texContentList.add(line);
				}
				fileBufferedReader.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
		}
		Path txtFilePath = Paths.get(TXT_File);
		FileUtils.writeToFile(texContentList, txtFilePath);
	}
	
}
