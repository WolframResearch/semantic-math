package thmp;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Scanner;

/**
 * Reads in text from file and extracts definitions, lemmas
 * prepositions, and theorems
 * 
 * @author yihed
 *
 */
public class ThmInput {

	public static void main(String[] args) throws IOException{
		File file = new File("commAlg.txt");
		Scanner sc = new Scanner(file);
		Path fileTo = Paths.get("thmFile.txt");
		ArrayList<String> thms = new ArrayList<String>();
		
		String newThm = "";
		boolean inThm = false;
		while(sc.hasNextLine()){
			String line = sc.nextLine();
			
			if(line.matches("\\\\begin\\{definition\\}|\\\\begin\\{lemma\\}")){
				newThm = "";
				inThm = true;
			}
			
			if(inThm)
				newThm = newThm + " " + line;
			
			if(line.matches("\\\\end\\{definition\\}|\\end\\{lemma\\}")){
				inThm = false;
				newThm += "\n";
				thms.add(newThm);
			}
			
		}

		//write list of theorems to file
		Files.write(fileTo, thms, Charset.forName("UTF-8"));

		sc.close();
	}
}
