package thmp.runner;

import java.util.ArrayList;
import java.util.List;

import thmp.ParsedExpression;
import thmp.utils.FileUtils;

public class ExtractFromParsedExpressionList {

	public static void main(String[] args){
		String src = "src/thmp/data/parsedExpressionList.dat";
		//String dest = "src/thmp/data/allThmsList1.txt";
		String fileNamesDest = "src/thmp/data/fileNamesList1.txt";
		
		@SuppressWarnings("unchecked")
		List<ParsedExpression> peList = ((List<ParsedExpression>)FileUtils.deserializeListFromFile(src));
		List<String> thmStrList = new ArrayList<String>();
		List<String> fileNamesList = new ArrayList<String>();
		
		boolean writeToFile = true;
		if(writeToFile){
			for(ParsedExpression pe : peList){
				thmStrList.add(pe.getOriginalThmStr() + "\n\n");
				fileNamesList.add(pe.getDefListWithThm().getSrcFileName());
			}
			//FileUtils.writeToFile(thmStrList, dest);
			FileUtils.writeToFile(fileNamesList, fileNamesDest);
		}
		
		ParsedExpression pe = peList.get(17);
		System.out.println("defListWithThm: "+pe.getDefListWithThm());
		System.out.println("file" +pe.getDefListWithThm().getSrcFileName());		
	}
}
