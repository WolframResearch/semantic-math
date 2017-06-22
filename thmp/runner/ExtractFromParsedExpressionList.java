package thmp.runner;

import java.util.ArrayList;
import java.util.List;

import thmp.parse.ParsedExpression;
import thmp.search.SearchCombined.ThmHypPair;
import thmp.utils.FileUtils;

public class ExtractFromParsedExpressionList {

	private static String peSerialSrcFile = "src/thmp/data/parsedExpressionList.dat";
	//private static String peSerialSrcFile = "";
	
	public static void main(String[] args){
		
		if(false) writePEThmsToFile();	
		getThmListSize();
		
	}

	private static void getThmListSize() {
		String peSerialSrcFile = "src/thmp/data/pe/combinedParsedExpressionList1";
		
		@SuppressWarnings("unchecked")
		List<ThmHypPair> peList = ((List<ThmHypPair>)FileUtils.deserializeListFromFile(peSerialSrcFile));
		System.out.println("ExtractFromParsedExpressionList - 1 peList.size() " + peList.size());
		
		peSerialSrcFile = "src/thmp/data/pe/combinedParsedExpressionList0";		
		@SuppressWarnings("unchecked")
		List<ThmHypPair> peList0 = ((List<ThmHypPair>)FileUtils.deserializeListFromFile(peSerialSrcFile));
		System.out.println("ExtractFromParsedExpressionList - 0 peList.size() " + peList0.size());
	
		
		peSerialSrcFile = "0502_001Untarred/0502/parsedExpressionList";	
		@SuppressWarnings("unchecked")
		List<ThmHypPair> peList2 = ((List<ThmHypPair>)FileUtils.deserializeListFromFile(peSerialSrcFile));
		System.out.println("ExtractFromParsedExpressionList - 0502 peList.size() " + peList2.size());
		peSerialSrcFile = "0502_002Untarred/0502/parsedExpressionList";	
		@SuppressWarnings("unchecked")
		List<ThmHypPair> peList22 = ((List<ThmHypPair>)FileUtils.deserializeListFromFile(peSerialSrcFile));
		System.out.println("ExtractFromParsedExpressionList - 05022 peList.size() " + peList22.size());
		
		peSerialSrcFile = "0503_001Untarred/0503/parsedExpressionList";	
		@SuppressWarnings("unchecked")
		List<ThmHypPair> peList3 = ((List<ThmHypPair>)FileUtils.deserializeListFromFile(peSerialSrcFile));
		System.out.println("ExtractFromParsedExpressionList - 0503 peList.size() " + peList3.size());
		peSerialSrcFile = "0503_002Untarred/0503/parsedExpressionList";	
		@SuppressWarnings("unchecked")
		List<ThmHypPair> peList32 = ((List<ThmHypPair>)FileUtils.deserializeListFromFile(peSerialSrcFile));
		System.out.println("ExtractFromParsedExpressionList - 05032 peList.size() " + peList32.size());
		
		peSerialSrcFile = "0504_001Untarred/0504/parsedExpressionList";		
		@SuppressWarnings("unchecked")
		List<ThmHypPair> peList4 = ((List<ThmHypPair>)FileUtils.deserializeListFromFile(peSerialSrcFile));
		System.out.println("ExtractFromParsedExpressionList - 0504 peList.size() " + peList4.size());
		
	}
	
	/**
	 * 
	 */
	private static void writePEThmsToFile() {
		//String dest = "src/thmp/data/allThmsList1.txt";
		String fileNamesDest = "src/thmp/data/fileNamesList1.txt";
		
		@SuppressWarnings("unchecked")
		List<ParsedExpression> peList = ((List<ParsedExpression>)FileUtils.deserializeListFromFile(peSerialSrcFile));
		List<String> thmStrList = new ArrayList<String>();
		List<String> fileNamesList = new ArrayList<String>();
		
		boolean writeToFile = true;
		if(writeToFile){
			for(ParsedExpression pe : peList){
				//getOriginalThmStr is now transient! Should access with thmWithDefList
				thmStrList.add(pe.getOriginalThmStr() + "\n\n");
				fileNamesList.add(pe.getDefListWithThm().getSrcFileName());
			}
			FileUtils.writeToFile(fileNamesList, fileNamesDest);
		}
		
		ParsedExpression pe = peList.get(17);
		System.out.println("defListWithThm: "+pe.getDefListWithThm());
		System.out.println("file" +pe.getDefListWithThm().getSrcFileName());
	}
}
