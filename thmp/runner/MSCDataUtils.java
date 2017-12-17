package thmp.runner;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

import thmp.parse.DetectHypothesis;
import thmp.search.SearchCombined.ThmHypPair;
import thmp.utils.FileUtils;
import thmp.utils.WordForms;

/**
 * Utilities to facilitate generation of
 *  classifier data for MSC classes.
 * Now generating function names for Michael.
 * @author yihed
 *
 */
public class MSCDataUtils {

	/**path to store data*/
	private static final String titleTxtFileName = "mscTitleNames.txt";
	
	public static void main(String[] args) throws IOException {
		//should supply list of paths to locations containing the FullTDMatrix 
		//and parsedExpressionList (of ThmHypPair s), e.g. 
		///home/usr0/yihed/thm/1401_001Untarred/1401.
		//enter file containing list of of paths or files that are FullTDMatrix's
		
				if(args.length < 1){
					System.out.println("Enter a file containing root paths to directories containing "
							+ "msc data! E.g. 0208_001Untarred/0208");
					return;
				}		
				String fileName = args[0];
				InputStreamReader inputStreamReader = new InputStreamReader(new FileInputStream(fileName));
				BufferedReader bReader = new BufferedReader( inputStreamReader);
				String line;
				try{
					while((line=bReader.readLine()) != null){
						if(WordForms.getWhiteEmptySpacePattern().matcher(line).matches()){
							continue;
						}
						int lineLen = line.length();
						if(lineLen > 3 && line.substring(lineLen-3).equals(".mx")){
							line = FileUtils.findFilePathDirectory(line);
						}
						saveArxivTitlesToFile(line);
					}
				}finally{
					bReader.close();
					inputStreamReader.close();
				}
	}
	
	/**
	 * Saves the file names as separate file, to be read in by 
	 * WL function that classifies. 
	 * @param dirPath path to directory containing parsedExpressionList,
	 * e.g. "/home/usr0/yihed/thm/1401_001Untarred/1401"
	 */
	private static void saveArxivTitlesToFile(String dirPath) {
		
		dirPath = FileUtils.addIfAbsentTrailingSlashToPath(dirPath);
		//deserialize parsedExpressionList, get the paperId list,
		//write to file.
		@SuppressWarnings("unchecked")
		List<ThmHypPair> thmHypPairList = (List<ThmHypPair>)FileUtils.deserializeListFromFile(dirPath + 
				DetectHypothesis.parsedExpressionSerialFileNameStr);
		
		StringBuilder sb = new StringBuilder(20000);
		for(ThmHypPair pair : thmHypPairList) {
			sb.append(pair.srcFileName()).append("\n");
		}
		if(sb.length() > 0) {
			sb = sb.deleteCharAt(sb.length()-1);
		}
		String namesFilePath = dirPath + titleTxtFileName;
		FileUtils.writeToFile(sb.toString(), namesFilePath);		
	}	
	
}
