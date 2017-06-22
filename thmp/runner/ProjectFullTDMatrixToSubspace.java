package thmp.runner;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;

import thmp.search.ProjectionMatrix;
import thmp.search.ThmSearch;
import thmp.search.ThmSearch.TermDocumentMatrix;
import thmp.utils.FileUtils;
import thmp.utils.WordForms;

/**
 * Project full-dimensional term document matrix to a 
 * low-dimensional space.
 * This is useful, say if we wanted to update the projection matrix,
 * or change the dimension of the low dimensional space, ie the number of 
 * singular values to keep.
 * @author yihed
 */
public class ProjectFullTDMatrixToSubspace {
	
	public static void main(String[] args) throws IOException{
		//enter file containing list of of paths or files that are FullTDMatrix's		
		if(args.length < 1){
			System.out.println("Enter a file containing root paths to directories containing "
					+ "full-dim matrices! E.g. 0208_001Untarred/0208");
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
				projectFullTDMatrix(line);
			}
		}finally{
			bReader.close();
			inputStreamReader.close();
		}
	}

	/**
	 * For each mx, project down to subspace, by calling projectTermDocumentMatrix
	 * @param args
	 */
	private static void projectFullTDMatrix(String dirName) {
		//e.g. /0208_001Untarred/0208
		String texFilesDirPath = thmp.utils.FileUtils.addIfAbsentTrailingSlashToPath(dirName);
		
		String fullTermDocumentMxPath = texFilesDirPath + ThmSearch.TermDocumentMatrix.FULL_TERM_DOCUMENT_MX_NAME + ".mx";
		if(!Files.isRegularFile(Paths.get(fullTermDocumentMxPath))){
			return;
		}
		String projectionMxPath = TermDocumentMatrix.DATA_ROOT_DIR_SLASH + TermDocumentMatrix.PROJECTION_MX_FILE_NAME; 
		String projectedTermDocumentMxPath = texFilesDirPath + TermDocumentMatrix.PROJECTED_MX_NAME + ".mx";
		//"0208_001/0208/FullTDMatrix.mx". "0208_001/0208/ProjectedTDMatrix.mx". "src/thmp/data/termDocumentMatrixSVD.mx"
		//signature: (String fullTermDocumentMxPath, String projectionMxPath, 
		//	String projectedTermDocumentMxPath)		
		TermDocumentMatrix.projectTermDocumentMatrix(fullTermDocumentMxPath, projectionMxPath, projectedTermDocumentMxPath);
	}
	
}
