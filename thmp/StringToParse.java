package thmp;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;

import thmp.ThmP1.ParsedPair;

/**
 * Reads in String and parses it
 * 
 * @author yihed
 *
 */

public class StringToParse {
	
	static{
		/*Maps.buildMap();
		try {
			Maps.readLexicon();
			Maps.readFixedPhrases();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}*/
	}
	
	public static void main(String[] args) throws IOException{
		
		String str;
		String[] strAr;
	
		/*File structMapFile = new File("structMap.txt");
		Path writeToFile = Paths.get("structMapTo.txt");
		ArrayList<String> updatedStructsList = new ArrayList<String>();
		*/
		Scanner sc = new Scanner(System.in);
		//Scanner sc = new Scanner(structMapFile);
		
		while(sc.hasNextLine()){
			/*nextLine = nextLine.replaceAll("put\\((\"[^\"]*\"), (\"[^\"]*\")\\)", 
					"put\\($1, new Rule\\($2, 1\\)\\)");
			
			updatedStructsList.add(nextLine);	*/		
			
			
			str = sc.nextLine();
			strAr = ThmP1.preprocess(str);
			
			ParseState parseState = new ParseState();
			for(int i = 0; i < strAr.length; i++){
				//alternate commented out line to enable tex converter
				//ThmP1.parse(ThmP1.tokenize(TexConverter.convert(strAr[i].trim()) ));
				parseState = ThmP1.tokenize(strAr[i].trim(), parseState);
				parseState = ThmP1.parse(parseState);				
			}
			
			List<ParsedPair> parsedList = ThmP1.getParsedExpr();
			for(ParsedPair parsedExpr : parsedList){
				System.out.println(parsedExpr);
			}
		}
		
		//Files.write(writeToFile, updatedStructsList, Charset.forName("UTF-8"));
		
		sc.close();
	}
}
