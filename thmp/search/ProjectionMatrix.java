package thmp.search;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.wolfram.jlink.Expr;
import com.wolfram.jlink.KernelLink;
import com.wolfram.jlink.MathLinkException;

import thmp.search.ThmSearch.TermDocumentMatrix;
import thmp.utils.FileUtils;
import thmp.utils.MathLinkUtils;

import static thmp.utils.MathLinkUtils.evaluateWLCommand;

/**
 * Tools for manipulating created projection matrix, 
 * apply projection mx to query vecs, 
 * combine serialized matrices together into List in linear time.
 * @author yihed
 *
 */
public class ProjectionMatrix {
	
	private static final KernelLink ml = FileUtils.getKernelLinkInstance();
	private static final Logger logger = LogManager.getLogger(ProjectionMatrix.class);
	private static final String combinedMxPath = "src/thmp/data/combinedTDMx.mx";
	
	/**
	 * args is list of paths as strings, e.g. "0208_001/0208/termDocumentMatrixSVD.mx",
	 * the *projected* term document matrices.
	 */
	public static void main(String[] args){
		int argsLen = args.length;
		if(argsLen == 0){
			throw new IllegalStateException("Suply a list of paths to .mx files!");
		}
		//form list of String's of paths, e.g. "0208_001/0208/termDocumentMatrixSVD.mx".
		List<String> mxFilePathList = new ArrayList<String>();
		for(int i = 0; i < argsLen; i++){
			//be sure to check it's valid path to valid .mx
			mxFilePathList.add(args[i]);			
		}
		//String contextPath, String vMxName, String concatenatedListName
		combineMx(mxFilePathList, TermDocumentMatrix.PROJECTED_MX_CONTEXT_NAME, 
				TermDocumentMatrix.PROJECTED_MX_NAME, 
				TermDocumentMatrix.CONCATENATED_PROJECTED_TERM_DOCUMENT_MX_NAME);
		//DumpSave's concatenated List
		evaluateWLCommand(ml, "DumpSave[" + combinedMxPath + "," + TermDocumentMatrix.CONCATENATED_PROJECTED_TERM_DOCUMENT_MX_NAME + "]");
		
	}
	
	/**
	 * 
	 * @param u 
	 * @param q mx to be applied, could be 1-D. List of row vectors (List's).
	 */
	public static void applyProjectionMatrix(String queryMxStrName, String dInverseName, String uTransposeName, 
			String corMxName, String projectedMxName){
		String msg = "Transposing and applying corMx...";
		logger.info(msg);
		
		try {
			//process query first with corMx. Convert to column vectors.			
			ml.evaluate("q0 = Transpose[" + queryMxStrName + "] + 0.08*" + corMxName + ".Transpose["+ queryMxStrName +"];");			
			//applies projection mx with given vectors. Need to Transpose from column vectors.				
			ml.evaluate(projectedMxName + "= Transpose[" + dInverseName + "." + uTransposeName + ".q0];");
		} catch (MathLinkException e) {
			throw new IllegalStateException(e);
		}
		
	}
	
	/**
	 * Loads matrices in from mx files, concatenates them into 
	 * one Internal`Bag.
	 * @param  List of .mx file names containing the thm vectors (term doc mx for each).
	 * e.g. "0208_001/0208/termDocumentMatrixSVD.mx".
	 * @param projectedMxContextPath path to context of projected mx. 
	 * @param vMxName name of V matrix (as list of *row* vectors). Name is same for each .mx file.
	 * created from projecting full term-document matrices from full dimensional ones.
	 */
	public static void combineMx(List<String> projectedMxFilePathList, String projectedMxContextPath,
			String vMxName, String concatenatedListName){
		/* Need to get overall length */
		evaluateWLCommand(ml, "lengthCount=0");
		int fileCounter = 1;
		for(String filePath : projectedMxFilePathList){			
			evaluateWLCommand(ml, "<<" +filePath);
			//assign
			String ithMxName = vMxName + fileCounter;
			evaluateWLCommand(ml, ithMxName + "=" + projectedMxContextPath + "`" + vMxName);
			evaluateWLCommand(ml, "lengthCount += Length[" + ithMxName + "]");
			fileCounter++;
		}
		//create bag with initial capacity.
		evaluateWLCommand(ml, "bag=Internal`Bag[Range[lengthCount]]; rangeCounter=0");
		
		for(int i = 1; i < fileCounter; i++){
			String ithName = vMxName+i;
			evaluateWLCommand(ml, "Internal`BagPart[bag, Range[rangeCounter+1, (rangeCounter+= Length["+ithName+"]) ]] ="
					+ ithName);			
		}
		//assign combined list to name
		evaluateWLCommand(ml, concatenatedListName + "= Internal`BagPart[bag]");
	}
	
	
	
	
}
