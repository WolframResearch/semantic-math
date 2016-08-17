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

/**
 * Reads in text from file and extracts definitions, lemmas
 * prepositions, and theorems
 * 
 * @author yihed
 *
 */
public class ThmInput {

	public static void main(String[] args) throws IOException{
		File file = new File("src/thmp/data/commAlg4.txt");
		Path fileTo = Paths.get("src/thmp/data/thmFile4.txt");
		
		List<String> thmList = readThm(file);
		
		//write list of theorems to file
		Files.write(fileTo, thmList, Charset.forName("UTF-8"));
	}

	/**
	 * @param file
	 * @return List of theorems read in.
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public static List<String> readThm(File file) throws FileNotFoundException, IOException {
		Scanner sc = new Scanner(file);
		List<String> thms = new ArrayList<String>();
		
		String newThm = "";
		boolean inThm = false;
		while(sc.hasNextLine()){
			String line = sc.nextLine();
			
			if(line.matches("(\\\\begin\\{definition\\})|(\\\\begin\\{lemma\\})")){				
				newThm = line;				
				line = sc.nextLine();
				inThm = true;
			}			
			else if(line.matches("(\\\\end\\{definition\\})|(\\\\end\\{lemma\\})")){
				inThm = false;
				newThm += "\n";
				thms.add(newThm);
			}
			
			if(inThm)
				newThm = newThm + " " + line;
		}
		
		sc.close();
		
		return thms;
	}
}
