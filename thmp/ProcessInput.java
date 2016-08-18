package thmp;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Processes theorems/lemmas/defs read in from file.
 * To be done after ThmInput.java.
 * 
 * @author yihed
 *
 */

public class ProcessInput {
	
	
	public static void main(String[] args) throws IOException{
		
		File inputFile = new File("src/thmp/data/thmFile4.txt");
		Path noTex = Paths.get("src/thmp/data/noTex4.txt");

		List<String> noTexStringList = processInput(inputFile, false);
		
		Files.write(noTex, noTexStringList, Charset.forName("UTF-8"));
	}

	/**
	 * @param inputFile
	 * @param replaceTex replaces the tex inside $ $ with "tex"
	 * @return List of Strings with latex such as \\cite removed.
	 * @throws FileNotFoundException
	 */
	public static List<String> processInput(File inputFile, boolean replaceTex) throws FileNotFoundException {
		Scanner sc = new Scanner(inputFile);		
		String noTexString = "";
		ArrayList<String> noTexStringList = new ArrayList<String>();
		
		while(sc.hasNextLine()){
			String thm = sc.nextLine();
			if(thm.matches("")) continue;			
			/*
			//get first 11 chars
			String start = thm.substring(0, 11);			
			if(start.matches("\\\\begin\\{def[a-z]*")){			
			}else if(start.matches("\\\\begin\\{lem[a-z]*")){				
			}else if(start.matches("\\\\begin\\{the[a-z]*")){				
			} */
			
			//skips the "\label"
			String[] meat = thm.split("\\\\label\\{([a-zA-Z]|-)*\\} ");
			if(meat.length > 1){
				thm = meat[1];
				//System.out.println(thm);
				if(replaceTex){
					thm = thm.replaceAll("\\$[^$]+\\$|\\$\\$[^$]+\\$\\$", "tex");
				}
				//thm.replaceAll("$[^$]\\$", "tex");
				//replaceAll("(?:\\$[^$]+\\$)|(?:\\$\\$[^$]+\\$\\$)", "tex").
				//use capturing groups to capture text inside {\it ... }
				//replace the others with non-captureing groups to speed up
				String tempThm = thm.replaceAll("\\\\begin\\{ali[^}]*\\}|\\\\end\\{ali[^}]*\\}|\\\\begin\\{equ[^}]*\\}|\\\\end\\{equ[^}]*\\}", "\\$\\$")
						.replaceAll("\\\\begin\\{[^}]*\\}|\\\\end\\{[^}]*\\}|\\\\cite\\{[^}]*\\}|\\\\item"
								+ "|\\\\ref\\{[^}]*\\}", "").replaceAll("\\{\\\\it([^}]*)\\}", "$1")
						 + "\n";
				/*Pattern regex = Pattern.compile("\\{\\\\it([^}]*)\\}");
				Matcher matcher = regex.matcher(tempThm);
				tempThm = matcher.replaceAll("$1"); */

				//noTexString += tempThm;
				noTexString = tempThm;
				
				//System.out.println(thm.replaceAll("(\\$[^$]+\\$)|(\\$\\$[^$]+\\$\\$)", "tex"));
				
			}
						
			noTexStringList.add(noTexString);
			
		}
		
		sc.close();
		return noTexStringList;
	}
		
}
