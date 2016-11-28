package thmp.utils;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extract macros, \def's and \newcommand's from latex sources.
 * @author yihed
 *
 */
public class ExtractMacros {
	
	/*public static Map<String, String> extractDefs(){
		//read in from file
				//FileInputStream texSrcStream = new FileInputStream(TEX_SRC);
		FileReader texSrcFileReader = null;
		try{
			texSrcFileReader = new FileReader(TEX_SRC);
		}catch(FileNotFoundException e){
			e.printStackTrace();
		}
		BufferedReader texSrcFileBReader = new BufferedReader(texSrcFileReader);
		return extractDefs(texSrcFileBReader);
	}*/
	
	/**use regex to get the customized commands and their definitions
	 * e.g. \def\Spec{\mathop{\rm Spec}}.
	 * Careful about duplicate commands, need to apply commands in the 
	 * right files where it's intended.
	 */
	public static Map<String, String> extractDefs(BufferedReader texSrcFileBReader){
		
		//map to store defs, keys are commands after the "\", values
		//are the parts to substitute, including starting "\", if any.
		//e.g. \def\Spec{\mathop{\rm Spec}} => key: Spec; val: \mathop{\rm Spec}
		Map<String, String> defMap = new HashMap<String, String>();
		
		String line;
		try {
			while((line = texSrcFileBReader.readLine()) != null){
				//what about newcommand?
				if(!line.matches("\\\\def(?:.*)\\}$")) continue;
				//Pattern pattern = Pattern.compile("\\\\def\\\\([^{]*)\\{(.*)(?!(\\}$))");
				//either one from below works
				Pattern pattern = Pattern.compile("\\\\def\\\\([^{]*)\\{(.*)(?=\\}$)"); 
				//Pattern pattern = Pattern.compile("\\\\def\\\\([^{]*)\\{(.*)[^(\\}$)]");
				Matcher matcher = pattern.matcher(line);
				if(matcher.find()){
					String command = matcher.group(1);
					String val = matcher.group(2);
					defMap.put(command, val);
				}
				
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return defMap;
	}
	
	public static void main(String[] args){
		try{
			FileReader macrosReader = new FileReader("src/thmp/data/texMacros.txt");
			BufferedReader macrosBReader = new BufferedReader(macrosReader);
			Map<String, String> defMap = extractDefs(macrosBReader);
			System.out.println(defMap);
		}catch(FileNotFoundException e){
			e.printStackTrace();
		}
	}
}
