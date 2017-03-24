package thmp.search;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.wolfram.jlink.Expr;
import com.wolfram.jlink.KernelLink;
import com.wolfram.jlink.MathLinkException;

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
	
	/**
	 * uses random numbers to pick out papers, 
	 */
	public static void main(){
		
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
	 * e.g. "src/thmp/data/termDocumentMatrixSVD.mx".
	 * @param vMxName name of V matrix (as *row* vectors). Name is same for each .mx file.
	 */
	public static void combineMx(List<String> mxFilePathList, String contextPath,
			String vMxName, String concatenatedListName){
		/* Need to get overall length */
		evaluateWLCommand("lengthCount=0");
		int fileCounter = 1;
		for(String filePath : mxFilePathList){			
			evaluateWLCommand("<<" +filePath);
			//assign
			String ithMxName = vMxName + fileCounter;
			evaluateWLCommand(ithMxName + "=" + contextPath + "`" + vMxName);
			evaluateWLCommand("lengthCount += Length[" + ithMxName + "]");
			fileCounter++;
		}
		//create bag with initial capacity.
		evaluateWLCommand("bag=Internal`Bag[Range[lengthCount]]; rangeCounter=0");
		
		for(int i = 1; i < fileCounter; i++){
			String ithName = vMxName+i;
			evaluateWLCommand("Internal`BagPart[bag, Range[rangeCounter+1, (rangeCounter+= Length["+ithName+"]) ]] ="
					+ ithName);			
		}
		//assign combined list to name
		evaluateWLCommand(concatenatedListName + "= Internal`BagPart[bag]");
	}
	
	
	
	
}
