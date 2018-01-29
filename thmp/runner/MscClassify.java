package thmp.runner;

import static thmp.utils.MathLinkUtils.evaluateWLCommand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

import com.wolfram.jlink.Expr;
import com.wolfram.jlink.ExprFormatException;

import thmp.utils.FileUtils;
import thmp.utils.MathLinkUtils.WLEvaluationMedium;
import thmp.runner.CreateMscVecs.Paper;

/**
 * Classifies MSC based on paper content.
 * @author yihed
 *
 */
public class MscClassify {

	private static final String mscContext = "MscClassify`";
	//"src/thmp/data/"+TermDocumentMatrix.FULL_TERM_DOCUMENT_MX_NAME+".mx";
	private static String pathToMscMx = "src/thmp/data/mscClassify.mx";
	private static String pathToMscPackage = "src/thmp/data/MscClassify.m";
	
	static {
		WLEvaluationMedium medium = FileUtils.acquireWLEvaluationMedium();
		
		pathToMscMx = FileUtils.getPathIfOnServlet(pathToMscMx);
		pathToMscPackage = FileUtils.getPathIfOnServlet(pathToMscPackage);
		
		evaluateWLCommand(medium, "<<"+pathToMscMx, false, true);
		evaluateWLCommand(medium, "<<"+pathToMscPackage, false, true);
		
		FileUtils.releaseWLEvaluationMedium(medium);
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
		
		Expr mscListExpr = evaluateWLCommand(medium, "Keys["+mscContext+"findNearestClasses[\""+  sb.toString() +"\"]]", true, true);
		System.out.println("mscListExpr "+mscListExpr);
		try{
			int mscListLen = mscListExpr.length();
			//System.out.println("mscListLen "+mscListLen);
			for(int i = 1; i <= mscListLen; i++) {
				String msc = mscListExpr.part(i).asString();
				mscList.add(msc);
				System.out.print(msc + "\t");
			}
			
		}catch(ExprFormatException e){
			System.out.println("ExprFormatException when interpreting msc class as String! " + e);
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
		
		evaluateWLCommand(medium, "<<"+pathToMscMx, false, true);
		evaluateWLCommand(medium, "<<"+pathToMscPackage, false, true);
		
		System.out.println( evaluateWLCommand(medium,  "$VersionNumber", true, true));
		/*System.out.println( evaluateWLCommand(medium,  "<<"+pathToMscMx, true, true));
		System.out.println( evaluateWLCommand(medium, "<<"+pathToMscPackage, true, true));*/
		System.out.println( evaluateWLCommand(medium, "Names[\""+mscContext+"*\"]", true, true));
		
		Scanner sc = new Scanner(System.in);
		System.out.println("Enter a file path: ");
		
		while(sc.hasNext()) {
			
			findMsc(sc.nextLine(), medium);
			System.out.println("\nEnter a file path: ");
		}
		
		sc.close();
		FileUtils.releaseWLEvaluationMedium(medium);
	}
	
}
