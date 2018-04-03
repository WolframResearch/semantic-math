package thmp.runner;

import static thmp.utils.MathLinkUtils.evaluateWLCommand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.wolfram.jlink.Expr;
import com.wolfram.jlink.ExprFormatException;

import thmp.utils.FileUtils;
import thmp.utils.MathLinkUtils.WLEvaluationMedium;
import thmp.utils.WordForms;
import thmp.runner.CreateMscVecs.Paper;
import thmp.search.DBSearch;

/**
 * Classifies MSC based on paper content.
 * @author yihed
 *
 */
public class MscClassify {

	private static final String mscContext = "MscClassify`";
	//"src/thmp/data/"+TermDocumentMatrix.FULL_TERM_DOCUMENT_MX_NAME+".mx";
	//private static String pathToMscMx = "src/thmp/data/mscClassify.mx";
	private static String pathToMscTxt = "src/thmp/data/mscClassify.txt";
	private static String pathToMscPackage = "src/thmp/data/MscClassify.m";
	private static String pathToMscNetMx = "src/thmp/data/MscNet.mx";
	//dir containing net model, resource files, etc
	private static String pathToMscDir = "src/thmp/data";
	
	private static final Logger logger = LogManager.getLogger(MscClassify.class);
	
	static {
		WLEvaluationMedium medium = FileUtils.acquireWLEvaluationMedium();
		
		loadResources(medium);
		
		FileUtils.releaseWLEvaluationMedium(medium);
	}

	private static void loadResources(WLEvaluationMedium medium) {
		pathToMscTxt = FileUtils.getPathIfOnServlet(pathToMscTxt);
		pathToMscPackage = FileUtils.getPathIfOnServlet(pathToMscPackage);
		pathToMscNetMx = FileUtils.getPathIfOnServlet(pathToMscNetMx);
		pathToMscDir = FileUtils.getPathIfOnServlet(pathToMscDir);
		
		//Note these need to be loaded on *all* kernels!
		
		//Expr s = evaluateWLCommand(medium, "<<"+pathToMscMx, true, true);
		//This order matters! Use this rather than mx, mx version issue here, but should switch to mx, depending on size reduction benefits.
		evaluateWLCommand(medium, "{MscClassify`Private`$wordFreqAdjAssoc,MscClassify`Private`$wordIndexAssoc,"
				+ "MscClassify`Private`$wordScoreMapAssoc,MscClassify`Private`$freqWordsAssoc,MscClassify`Private`$mscListList,"
				+ "MscClassify`Private`$v,MscClassify`Private`$dInverseUTranspose}="
				+ "Uncompress[Import[\""+ pathToMscTxt +"\"]]", false, true);
		
		//logger.info("Return from getting pathToMscMx: ",s);
		evaluateWLCommand(medium, "<<"+pathToMscPackage, false, true);
		evaluateWLCommand(medium, "<<"+pathToMscNetMx, false, true);
		
		//load MscNet resources
		evaluateWLCommand(medium, mscContext+"initialize[\""+pathToMscDir+"\"]", false, true);
		
		//logger.info("Return from getting pathToMscPackage: "+s);
		//logger.info("Names[\"a`*\"]  "+ evaluateWLCommand(medium, "Names[\"MscClassify`*\"]", true, true));
	}
	
	private static List<String> findMsc(String filePath, WLEvaluationMedium medium) {
		
		String fileContent = null;
		try {
			fileContent = FileUtils.readStrFromFile(filePath);
		}catch(IllegalStateException e) {
			System.out.println("Can't find file at path " + filePath);
			return Collections.emptyList();
		}		
		return findMsc(medium, fileContent);		
	}

	private static List<String> findMsc(WLEvaluationMedium medium, String fileContent) {
		List<Paper> paperList = new ArrayList<Paper>();
		paperList.add(new Paper(fileContent));
		StringBuilder sb = CreateMscVecs.buildWordFreqDataStr(paperList);
		System.out.println(sb.toString());
		
		List<String> mscList = new ArrayList<String>();
		
		Expr mscListExpr = evaluateWLCommand(medium, mscContext+"findNearestClasses[\""+  sb.toString() +"\"]", true, true);
		System.out.println("mscListExpr "+mscListExpr);
		logger.info("mscListExpr "+mscListExpr);
		
		try{
			int mscListLen = mscListExpr.length();
			for(int i = 1; i <= mscListLen; i++) {
				String msc = mscListExpr.part(i).asString();
				if( WordForms.DIGIT_PATTERN.matcher(msc.substring(0, 1)).matches() ) {
					//avoid e.g. "PRIMA" that the model sometimes predicts.
					mscList.add(msc);
					//System.out.print(msc + "\t");
				}				
			}
			
		}catch(ExprFormatException e){
			String msg = "ExprFormatException when interpreting msc class as String! " + e;
			System.out.println(msg);
			logger.error(msg);
			return Collections.emptyList();
		}
		return mscList;
	}
	
	/**
	 * Classifies MSC based on paper content.
	 * @param paperContent
	 * @return List of msc.
	 */
	public static List<String> findMsc(String paperContent) {
		WLEvaluationMedium medium = FileUtils.acquireWLEvaluationMedium();
		List<String> mscList = findMsc(medium, paperContent);
		FileUtils.releaseWLEvaluationMedium(medium);
		return mscList;
	}
	
	public static void main(String[] args) {
		
		//obtain a kernel, load package
		WLEvaluationMedium medium = FileUtils.acquireWLEvaluationMedium();
		//evaluateWLCommand(medium, "Get[\""+pathToMscMx+"\"];", false, true);
		//evaluateWLCommand(medium, "Get[\""+pathToMscPackage+"\"];", false, true);
		
		//evaluateWLCommand(medium, "<<"+pathToMscMx, false, true);
		//evaluateWLCommand(medium, "<<"+pathToMscPackage, false, true);
		loadResources(medium);
		
		System.out.println( evaluateWLCommand(medium,  "$VersionNumber", true, true));
		/*System.out.println( evaluateWLCommand(medium,  "<<"+pathToMscMx, true, true));
		System.out.println( evaluateWLCommand(medium, "<<"+pathToMscPackage, true, true));*/
		System.out.println( evaluateWLCommand(medium, "Names[\""+mscContext+"*\"]", true, true));
		
		Scanner sc = new Scanner(System.in);
		System.out.println("Enter a file path: ");
		
		while(sc.hasNext()) {
			
			findMsc(sc.nextLine(), medium);
			System.out.println("\nEnter path to a tex source file: ");
		}
		
		sc.close();
		FileUtils.releaseWLEvaluationMedium(medium);
	}
	
}
