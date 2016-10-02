package thmp;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
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
		boolean writeToFile = false;
		if(writeToFile){
			//File file = new File("src/thmp/data/commAlg5.txt");
			//String srcFileStr = "src/thmp/data/commAlg5.txt";
			//String srcFileStr = "src/thmp/data/multilinearAlgebra.txt";
			//String srcFileStr = "src/thmp/data/functionalAnalysis.txt";
			String srcFileStr = "src/thmp/data/fieldsRawTex.txt";			
			
			//Path fileTo = Paths.get("src/thmp/data/thmFile5.txt");
			//Path fileTo = Paths.get("src/thmp/data/multilinearAlgebraThms.txt");
			Path fileTo = Paths.get("src/thmp/data/fieldsThms.txt");
			
			FileReader srcFileReader = new FileReader(srcFileStr);
			BufferedReader srcFileBReader = new BufferedReader(srcFileReader);
			
			List<String> thmList = readThm(srcFileBReader);
			
			//write list of theorems to file
			Files.write(fileTo, thmList, Charset.forName("UTF-8"));
		}
	}

	/**
	 * @param file
	 * @return List of theorems read in.
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public static List<String> readThm(BufferedReader srcFileReader) throws FileNotFoundException, IOException {
		//BufferedReader is faster than scanner

		//Scanner sc = new Scanner(file);
		List<String> thms = new ArrayList<String>();
		
		String newThm = "";
		boolean inThm = false;
		String line;
		while((line=srcFileReader.readLine()) != null){
			//while(sc.hasNextLine()){
			if(line.matches("\\s*")) continue;
			
			//if(line.matches("(?:\\\\begin\\{def[^}]*\\}|\\\\begin\\{lem[^}]*\\}|\\\\begin\\{th[^}]*\\}|\\\\begin\\{prop[^}]*\\})(?:.)*")){	
			if(line.matches("\\\\begin\\{def(?:.*)|\\\\begin\\{lem(?:.*)|\\\\begin\\{th(?:.*)|\\\\begin\\{prop(?:.*)|\\\\begin\\{proclaim(?:.*)")){	
				//if(line.matches("\\\\begin\\{definition\\}|\\\\begin\\{lemma\\}")){
				//if(line.matches("\\\\begin\\{definition\\}|\\\\begin\\{lemma\\}|\\\\begin\\{thm\\}|\\\\begin\\{theorem\\}")){	
				newThm = line;				
				line = srcFileReader.readLine();
				inThm = true;
			}			
			//else if(line.matches("\\\\end\\{definition\\}|\\\\end\\{lemma\\}")){
			else if(line.matches("\\\\end\\{def(?:.*)|\\\\end\\{lem(?:.*)|\\\\end\\{th(?:.*)|\\\\end\\{prop(?:.*)|\\\\endproclaim(?:.*)")){
			//else if(line.matches("\\\\end\\{definition\\}|\\\\end\\{lemma\\}|\\\\end\\{thm\\}|\\\\end\\{theorem\\}")){
				inThm = false;
				newThm += "\n";
				if(!newThm.matches("\\s*")) thms.add(newThm);
				newThm = "";
				continue;
			}
			
			if(inThm)
				newThm = newThm + " " + line;
		}
		
		//srcFileReader.close();
		//System.out.println("Inside ThmInput, thmsList " + thms);
		return thms;
	}
}
