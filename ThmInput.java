import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

/**
 * Reads in text from file and extracts definitions, lemmas
 * prepositions, and theorems
 * 
 * @author yihed
 *
 */
public class ThmInput {

	public static void main(String[] args) throws FileNotFoundException{
		File file = new File("commAlg.txt");
		Scanner sc = new Scanner(file);
		
		String newThm = "";
		boolean inThm = false;
		while(sc.hasNextLine()){
			String line = sc.nextLine();
			
			if(line.matches("\\begin{definition}|\\begin{lemma}")){
				newThm = "";
				inThm = true;
			}
			
			if(inThm)
				newThm = newThm + " " + line;
			
			if(line.matches("\\end{definition}|\\end{lemma}")){
				inThm = false;
				//write newThm to file
				
			}
			
		}
		
		sc.close();
	}
}
