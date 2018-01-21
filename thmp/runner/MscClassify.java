package thmp.runner;

import static thmp.utils.MathLinkUtils.evaluateWLCommand;

import java.util.ArrayList;
import java.util.List;

import thmp.utils.FileUtils;
import thmp.utils.MathLinkUtils.WLEvaluationMedium;
import thmp.runner.CreateMscVecs.Paper;

/**
 * Classifies MSC based on paper content.
 * @author yihed
 *
 */
public class MscClassify {

	private static final String pathToMscMx = "";
	
	private static void findMsc(String filePath, WLEvaluationMedium medium) {
		
		String fileContent = FileUtils.readStrFromFile(filePath);
		List<Paper> paperList = new ArrayList<Paper>();
		paperList.add(new Paper(fileContent));
		StringBuilder sb = CreateMscVecs.buildWordFreqDataStr(paperList);
		
		evaluateWLCommand(medium, "<<"+  pathToMscMx, false, true);
		
	}
	
	public static void main(String[] args) {
		
		//obtain a kernel, load package
		WLEvaluationMedium medium = FileUtils.acquireWLEvaluationMedium();
		evaluateWLCommand(medium, "<<"+pathToMscMx, false, true);
		
		while(true) {
			
			break;
		}
		FileUtils.releaseWLEvaluationMedium(medium);
	}
	
}
