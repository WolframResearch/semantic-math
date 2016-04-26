package thmp;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;

/**
 * Processes theorems/lemmas/defs read in from file
 * 
 * @author yihed
 *
 */

public class ProcessInput {

	//  
	//
	public static void main(String[] args) throws IOException{
		
		File inputFile = new File("thmFile2.txt");
		Scanner sc = new Scanner(inputFile);		
		
		
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
				System.out.println(thm);
				//thm.replaceAll("$[^$]\\$", "tex");
				System.out.println(thm.replaceAll("(\\$[^$]+\\$)|(\\$\\$[^$]+\\$\\$)", "tex"));
				
			}
			
			
		}
		
		sc.close();
	}
		
}
