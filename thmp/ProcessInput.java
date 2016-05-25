package thmp;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Processes theorems/lemmas/defs read in from file
 * 
 * @author yihed
 *
 */

public class ProcessInput {
	
	//
	public static void main(String[] args) throws IOException{
		
		File inputFile = new File("thmFile3.txt");
		Scanner sc = new Scanner(inputFile);		
		Path noTex = Paths.get("noTex3.txt");
		String noTexString = null;
		
		while(sc.hasNextLine()){
			String thm = sc.nextLine();
			if(thm.matches("")) continue;			
			
			String start = thm.substring(0, 11);
			
			if(start.matches("\\\\begin\\{def[a-z]*")){
			
			}else if(start.matches("\\\\begin\\{lem[a-z]*")){
				
			}else if(start.matches("\\\\begin\\{the[a-z]*")){
				
			}
			
			//skips the "\label"
			String[] meat = thm.split("\\\\label\\{([a-zA-Z]|-)*\\} ");
			if(meat.length > 1){
				thm = meat[1];
				//System.out.println(thm);
				//thm.replaceAll("$[^$]\\$", "tex");
				//replaceAll("(\\$[^$]+\\$)|(\\$\\$[^$]+\\$\\$)", "tex").
				//use capturing groups to capture text inside {\it ... }
				//replace the others with non-captureing groups to speed up
				String tempThm = thm.replaceAll("\\\\begin\\{ali[^}]*\\}|\\\\end\\{ali[^}]*\\}|\\\\begin\\{equ[^}]*\\}|\\\\end\\{equ[^}]*\\}", "\\$\\$")
						.replaceAll("\\\\begin\\{[^}]*\\}|\\\\end\\{[^}]*\\}|\\\\cite\\{[^}]*\\}|\\\\item"
								+ "|\\\\ref\\{[^}]*\\}", "").replaceAll("\\{\\\\it([^}]*)\\}", "$1")
						 + "\n";
				/*Pattern regex = Pattern.compile("\\{\\\\it([^}]*)\\}");
				Matcher matcher = regex.matcher(tempThm);
				tempThm = matcher.replaceAll("$1"); */
				
				noTexString += tempThm;
				//System.out.println(thm.replaceAll("(\\$[^$]+\\$)|(\\$\\$[^$]+\\$\\$)", "tex"));
				
			}
			
			ArrayList<String> noTexStringList = new ArrayList<String>();
			noTexStringList.add(noTexString);
			
			Files.write(noTex, noTexStringList, Charset.forName("UTF-8"));
		}
		
		sc.close();
	}
		
}
