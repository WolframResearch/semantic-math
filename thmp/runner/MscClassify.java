package thmp.runner;

import static thmp.utils.MathLinkUtils.evaluateWLCommand;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import com.wolfram.jlink.Expr;
import com.wolfram.jlink.ExprFormatException;

import thmp.utils.FileUtils;
import thmp.utils.MathLinkUtils.WLEvaluationMedium;
import thmp.runner.CreateMscVecs.Paper;
import thmp.search.ThmSearch.TermDocumentMatrix;

/**
 * Classifies MSC based on paper content.
 * @author yihed
 *
 */
public class MscClassify {

	private static final String mscContext = "MscClassify`";
	//"src/thmp/data/"+TermDocumentMatrix.FULL_TERM_DOCUMENT_MX_NAME+".mx";
	private static final String pathToMscMx = "/home/usr0/yihed/thm/src/thmp/data/mscClassify.mx";
	private static final String pathToMscPackage = "/home/usr0/yihed/thm/src/thmp/data/MscClassify.m";
	
	private static void findMsc(String filePath, WLEvaluationMedium medium) {
		
		String fileContent = FileUtils.readStrFromFile(filePath);
		List<Paper> paperList = new ArrayList<Paper>();
		paperList.add(new Paper(fileContent));
		StringBuilder sb = CreateMscVecs.buildWordFreqDataStr(paperList);
		System.out.println(sb.toString());
		
		Expr mscListExpr = evaluateWLCommand(medium, "Keys["+mscContext+"findNearestClasses[\""+  sb.toString() +"\"]]", true, true);
		System.out.println("mscListExpr "+mscListExpr);
		try{
			int mscListLen = mscListExpr.length();
			//System.out.println("mscListLen "+mscListLen);
			for(int i = 1; i <= mscListLen; i++) {
				String msc = mscListExpr.part(i).asString();
				System.out.print(msc + "\t");
			}
			
		}catch(ExprFormatException e){
			System.out.println("ExprFormatException when interpreting msc class as String! " + e);
		}
		
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
