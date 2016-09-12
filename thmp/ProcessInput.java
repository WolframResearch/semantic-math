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
		
		File inputFile = new File("src/thmp/data/thmFile5.txt");
		Path noTex = Paths.get("src/thmp/data/noTex5.txt");

		List<String> noTexStringList = processInput(inputFile, false);
		
		Files.write(noTex, noTexStringList, Charset.forName("UTF-8"));
	}

	/**
	 * Reads in from file, potentially replaces latex, puts thms into a list, 
	 * @param inputFile
	 * @param replaceTex replaces the tex inside $ $ with "tex"
	 * @return List of Strings with latex such as \\cite removed.
	 * @throws FileNotFoundException
	 */
	public static List<String> processInput(File inputFile, boolean replaceTex) throws FileNotFoundException {
		Scanner sc = new Scanner(inputFile);		
		
		ArrayList<String> noTexStringList = new ArrayList<String>();
		
		while(sc.hasNextLine()){
			String thm = sc.nextLine();
			if(thm.matches("")) continue;			
			String noTexString = inputReplace(thm, replaceTex);
			noTexStringList.add(noTexString);
		}
		
		sc.close();
		return noTexStringList;
	}
	
	/**
	 * Processes list of inputs.
	 * @param thmInputList
	 * @param replaceTex
	 * @return
	 */
	public static List<String> processInput(List<String> thmInputList, boolean replaceTex){
		List<String> inputProcessedList = new ArrayList<String>();
		
		for(int i = 0; i < thmInputList.size(); i++){
			String thm = thmInputList.get(i);
			String thmProcessed = inputReplace(thm, replaceTex);
			inputProcessedList.add(thmProcessed);
		}
		return inputProcessedList;
	}
	
	/**
	 * Simplifies input string by making various string replacements.
	 * @param thm
	 * @param replaceTex
	 * @return
	 */
	private static String inputReplace(String thm, boolean replaceTex){
		String[] meat = thm.split("\\\\label\\{([a-zA-Z]|-)*\\} ");
		String noTexString = "";
		//get the second part if separated by "\label{...}"
		if(meat.length > 1){
			thm = meat[1];
			//System.out.println(thm);
		}
		noTexString = replaceThm(thm, replaceTex);			

		return noTexString;
	}

	/**
	 * @param thm
	 * @param replaceTex
	 * @return
	 */
	private static String replaceThm(String thm, boolean replaceTex) {
		String noTexString;
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
		return noTexString;
	}
}
