package thmp.runner;

import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.ImmutableList;

import thmp.parse.ParsedExpression;
import thmp.parse.TheoremContainer;
import thmp.search.ThmSearch;
import thmp.utils.FileUtils;

/**
 * Generate SVD matrix in the form of sparse array. 
 * Useful when e.g. matrix generation in DetectHypothesis 
 * did not complete. This gives the decomposition.
 * 
 * @author yihed
 */
public class GenerateSVDMatrix{

	public static void main(String[] args){
		/*Deserialize parsedExpressionList from input, e.g. combinedParsedExpressionList.dat */		
		int argsLen = args.length;
		if(argsLen < 1){
			System.out.println("Please supply a file to deserialize ParsedExpression's from!");
			return;
		}
		
		String fileStr = args[0];
		
		@SuppressWarnings("unchecked")
		List<ParsedExpression> parsedExpressionList = (List<ParsedExpression>)FileUtils.deserializeListFromFile(fileStr);
		//thmp.utils.ResourceDeposit.setParsedExpressionList(parsedExpressionList);
		
		/*Do *NOT* set gatheringDataBoolToTrue()! Since need to use word maps gathered from last time.*/
		//Searcher.SearchMetaData.set_gatheringDataBoolToTrue();
		//FileUtils.set_dataGenerationMode();	
		List<ParsedExpression> selectedParsedExpressionList = new ArrayList<ParsedExpression>();
		
		//frequently used source: number of theorems: 69295. Cut this number so SVD is actually possible:
		//for 23000 words, half an hour to process and generate corr mx.
		//boolean pickBool = true;
		int parsedExpressionListSz = parsedExpressionList.size();
		for(int i = 0; i < parsedExpressionListSz; i++){
			ParsedExpression thm = parsedExpressionList.get(i);
			//take every third thm right now. 28162 words, 23099 thms take 1.75 hours on byblis.
			if(i % 3 == 0){
				selectedParsedExpressionList.add(thm);
			}
			//pickBool = pickBool ^ true;
		}
		
		ThmSearch.TermDocumentMatrix.createTermDocumentMatrixSVD(
				ImmutableList.<TheoremContainer>copyOf(selectedParsedExpressionList));
		FileUtils.closeKernelLinkInstance();
	}
}
