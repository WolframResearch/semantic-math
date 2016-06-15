package thmp;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Scanner;

/**
 * Reads in String and parses it
 * 
 * @author yihed
 *
 */

public class StringToParse {
	
	/*static{
		try {
			ThmP1.buildMap();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}*/
	
	public static void main(String[] args) throws IOException{
		/*
		String str = "";
		String[] strAr;
		for(int i = 0; i < args.length; i++){
			str += args[i] + " ";
		}
		
		strAr = ThmP1.preprocess(str);
		
		for(int i = 0; i < strAr.length; i++){
			//ThmP1.parse(ThmP1.tokenize(strAr[i].trim() ));				
		}
		*/
		File structMapFile = new File("structMap.txt");
		Path writeToFile = Paths.get("structMapTo.txt");
		ArrayList<String> updatedStructsList = new ArrayList<String>();
		
		//Scanner sc = new Scanner(System.in);
		Scanner sc = new Scanner(structMapFile);
		
		while(sc.hasNextLine()){
			String nextLine = sc.nextLine();
			
			nextLine = nextLine.replaceAll("put\\((\"[^\"]*\"), (\"[^\"]*\")\\)", 
					"put\\($1, new Rule\\($2, 1\\)\\)");
			
			updatedStructsList.add(nextLine);			
			
			/*
			str = sc.nextLine();
			strAr = ThmP1.preprocess(str);
			
			for(int i = 0; i < strAr.length; i++){
				ThmP1.parse(ThmP1.tokenize(strAr[i].trim() ));				
			}
			*/
		}
		
		Files.write(writeToFile, updatedStructsList, Charset.forName("UTF-8"));
		
		sc.close();
	}
}
